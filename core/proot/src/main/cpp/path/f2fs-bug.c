#include <stdbool.h>       /* bool, true, false,  */
#include <assert.h>        /* assert(3), */
#include <string.h>        /* str*(3), */
#include <linux/limits.h>  /* PATH_MAX, */
#include <unistd.h>        /* access(2), rmdir(2), */
#include <fcntl.h>         /* O_WRONLY, open(2) */
#include <sys/wait.h>      /* waitpid(2), */
#include <errno.h>         /* errno, EEXIST,  */
#include <libgen.h>        /* dirname(3), basename(3),  */
#include <dirent.h>        /* readdir(3), opendir(3), */

#include "path/temp.h"
#include "tracee/tracee.h"
#include "cli/note.h"

/**
 * Check if device is affected by f2fs case sensitivity bug
 */
static bool probe_f2fs_bug(const Tracee *tracee) {
	VERBOSE(tracee, 6, "Checking for f2fs case sensitivity bug");

	bool result = false;

	/* Get base temporary directory */
	const char *base_tmp = get_temp_directory();
	assert(strlen(base_tmp) < PATH_MAX - 30);

	/* Create temporary subdirectory */
	char tmp[PATH_MAX];
	strcpy(tmp, base_tmp);
	strcat(tmp, "/proot_f2fsbug_XXXXXX");
	if (mkdtemp(tmp) == NULL) {
		note(tracee, WARNING, SYSTEM, "Unable to create temp directory for f2fs bug probe");
		goto end;
	}

	/* Build test file paths */
	char file1[PATH_MAX];
	char file2[PATH_MAX];
	char file3[PATH_MAX];
	strcpy(file1, tmp);
	strcat(file1, "/aa");
	strcpy(file2, tmp);
	strcat(file2, "/Aa");
	strcpy(file3, tmp);
	strcat(file3, "/aA");

	/* Create first file */
	int fd = open(file1, O_WRONLY|O_CREAT|O_EXCL, 0600);
	if (fd < 0) {
		note(tracee, WARNING, SYSTEM, "Unable to create first file for f2fs bug probe");
		goto end_remove_temp_dir;
	}
	close(fd);

	/* Create second file, this checks if filesystem is normally case insensitive */
	fd = open(file2, O_WRONLY|O_CREAT|O_EXCL, 0600);
	if (fd < 0) {
		note(tracee, WARNING, SYSTEM, "Looks like there is case-insensitive file system in %s", tmp);
		goto end_delete_temp_files;
	}
	close(fd);

	/* Prewarm third file (on normal kernel this won't have any side effect) */
	int access_result = access(file3, F_OK);
	if (access_result == 0) {
		note(tracee, WARNING, SYSTEM, "f2fs bug probe detected successful access() on non-existent file");
		goto end_delete_temp_files;
	}

	/* Create third file from child process */
	int wstatus = 0;
	pid_t pid = fork();
	if (pid == 0) {
		errno = 0;
		fd = open(file3, O_WRONLY|O_CREAT, 0600);
		if (fd < 0) {
			if (errno == EEXIST) {
				VERBOSE(tracee, 1, "f2fs bug detected");
				_exit(1);
			} else {
				note(tracee, WARNING, SYSTEM, "f2fs bug probe failed to open third file with different errno than expected (errno=%d)", errno);
				_exit(2);
			}
		}
		close(fd);
		_exit(0);
	} else if (pid != -1) {
		waitpid(pid, &wstatus, 0);
	} else {
		note(tracee, WARNING, SYSTEM, "fork() failed for f2fs bug probe");
		goto end_delete_temp_files;
	}

	/* Set result basing on child exit status */
	if (WIFEXITED(wstatus) && WEXITSTATUS(wstatus) == 0) {
		VERBOSE(tracee, 6, "f2fs bug not present on device");
	} else if (WIFEXITED(wstatus) && WEXITSTATUS(wstatus) == 1) {
		/* Bug detected */
		VERBOSE(tracee, 1, "enabling f2fs bug workaround");
		result = true;
	} else {
		note(tracee, WARNING, SYSTEM, "got unexpected status from f2fs bug probe process (wstatus=0x%X)", wstatus);
	}

end_delete_temp_files:
	unlink(file1);
	unlink(file2);
	unlink(file3);
end_remove_temp_dir:
	rmdir(tmp);
end:
	return result;
}

bool should_skip_file_access_due_to_f2fs_bug(const Tracee *tracee, const char *path) {
	/* On first call check if workaround should be enabled */
	static bool f2fs_bug_probed;
	static bool f2fs_bug_detected;
	if (!f2fs_bug_probed) {
		const char *env = getenv("PROOT_F2FS_WORKAROUND");
		if (env != NULL && strcmp(env, "1") == 0) {
			VERBOSE(tracee, 1, "enabling f2fs bug workaround due to env variable");
			f2fs_bug_detected = true;
		} else if (env != NULL && strcmp(env, "0") == 0) {
			VERBOSE(tracee, 1, "disabling f2fs bug workaround due to env variable");
			f2fs_bug_detected = false;
		} else {
			f2fs_bug_detected = probe_f2fs_bug(tracee);
		}
		f2fs_bug_probed = true;
	}

	/* If workaround is not active don't skip access to file */
	if (!f2fs_bug_detected) {
		return false;
	}

	assert(strlen(path) < PATH_MAX - 1);
	char buf[PATH_MAX];
	strcpy(buf, path);
	const char *dname = dirname(buf);

	DIR *dir = opendir(dname);
	if (dir == NULL) {
		VERBOSE(tracee, 4, "f2fs bug workaround cannot list directory %s", dname);
		/* Don't skip access to unlistable directory (e.g. "/data" on Android) */
		return false;
	}

	strcpy(buf, path);
	const char *bname = basename(buf);
	struct dirent *entry;
	while ((entry = readdir(dir)) != NULL) {
		if (strcmp(entry->d_name, bname) == 0) {
			/* Found file name on listing so it exists */
			VERBOSE(tracee, 4, "f2fs bug workaround found file %s", path);
			closedir(dir);
			return false;
		}
	}

	/* File not found in list, do not pass its name to kernel or inode will go into bad state */
	VERBOSE(tracee, 4, "f2fs bug workaround did not find file %s", path);
	closedir(dir);
	return true;
}
