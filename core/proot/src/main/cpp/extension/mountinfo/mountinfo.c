#include "extension/extension.h"
#include "path/path.h"           /* translate_path,  */
#include "path/binding.h"        /* Binding, bindings */
#include "path/temp.h"           /* create_temp_file,  */
#include <limits.h>              /* INT_MAX,  */
#include <linux/limits.h>        /* PATH_MAX,  */
#include <string.h>              /* strlen, strcmp */
#include <stdio.h>               /* FILE, getline, fprintf */
#include <stdlib.h>              /* free,  */
#include <sys/queue.h>           /* CIRCLEQ_*,  */

/**
 * Append a synthesized mount-table line to @fp for each runtime
 * binding (i.e. one that wasn't part of the static -r/-b set).  This
 * is what lets sandbox helpers like bubblewrap find the mount they
 * just asked PRoot to create via emulate_mount().
 */
static void append_runtime_binding_lines(Tracee *target_tracee, FILE *fp)
{
	Binding *binding;
	int next_id = 1000000;
	int parent_id = 1;

	if (target_tracee->fs->bindings.guest == NULL)
		return;

	for (binding = CIRCLEQ_FIRST(target_tracee->fs->bindings.guest);
	     binding != (void *) target_tracee->fs->bindings.guest;
	     binding = CIRCLEQ_NEXT(binding, link.guest)) {
		/* Skip the root binding "/" — already present as the kernel root.  */
		if (strcmp(binding->guest.path, "/") == 0)
			continue;

		fprintf(fp,
			"%d %d 0:1 / %s rw,relatime - bind %s rw,relatime\n",
			next_id++, parent_id,
			binding->guest.path, binding->host.path);
	}
}

static void mountinfo_check_open_path(Tracee *tracee, char path[PATH_MAX]) {
	/* Try matching "/proc/<PID>/mountinfo"  */
	size_t len = strlen(path);
	if (
			len > (6 + 10) &&
			0 == strncmp(path, "/proc/", 6) &&
			0 == strcmp(path + (len - 10), "/mountinfo")
	   ) {
		/* Check if current root is under /data and if so replace contents
		 * of /proc/<PID>/mountinfo to make it contain /data as / mountpoint.
		 * This is needed because on Android / is read only mount
		 *
		 * https://github.com/termux/proot/issues/294
		 */
		char *path_end = NULL;
		long target_pid = strtol(path + 6, &path_end, 10);
		if (path_end != path + (len - 10) || target_pid <= 0 || target_pid > INT_MAX) {
			return;
		}
		Tracee *target_tracee = get_tracee(tracee, target_pid, false);
		if (target_tracee == NULL) {
			return;
		}

		/* Check if our root is under "/data"  */
		char root_path[PATH_MAX]; // Host path to guest root
		translate_path(target_tracee, root_path, AT_FDCWD, "/", true);
		Comparison compare_result = compare_paths(root_path, "/data");
		bool is_android_data = (compare_result == PATH2_IS_PREFIX || compare_result == PATHS_ARE_EQUAL);

		/* Are there bindings to expose as fake mounts (mount(2)
		 * calls from sandbox helpers are converted into
		 * bindings — see emulate_mount).  Skip the root
		 * binding, which the real kernel mount table already
		 * covers.  */
		bool has_extra_bindings = false;
		if (target_tracee->fs->bindings.guest != NULL) {
			Binding *b;
			for (b = CIRCLEQ_FIRST(target_tracee->fs->bindings.guest);
			     b != (void *) target_tracee->fs->bindings.guest;
			     b = CIRCLEQ_NEXT(b, link.guest)) {
				if (strcmp(b->guest.path, "/") != 0) {
					has_extra_bindings = true;
					break;
				}
			}
		}

		if (!is_android_data && !has_extra_bindings)
			return;

		/* Open real /proc/<PID>/mountinfo  */
		FILE *real_mountinfo_fp = fopen(path, "r");
		if (real_mountinfo_fp == NULL) {
			return;
		}

		/* Prepare faked mountinfo  */
		const char *new_path = create_temp_file(tracee->ctx, "mountinfo");
		FILE *new_mountinfo_fp = fopen(new_path, "w");
		if (new_mountinfo_fp == NULL) {
			fclose(real_mountinfo_fp);
			return;
		}

		char *line = NULL;
		size_t line_buf_len = 0;
		ssize_t line_len = 0;
		bool found_line = false;

		if (is_android_data) {
			while ((line_len = getline(&line, &line_buf_len, real_mountinfo_fp)) > 0) {
				char *chunk = line;
				/* Skip columns before 'root'  */
				for (int i = 0; i < 4 && chunk - line < line_len; i++) {
					chunk = strchr(chunk, ' ');
					if (chunk == NULL) goto end_line_scan;
					chunk++;
				}

				/* Match path  */
				char *chunk_end = strchr(chunk, ' ');
				if (chunk_end == NULL) continue;

				if (chunk_end - chunk == 5 && 0 == memcmp(chunk, "/data", 5)) {
					/* Write line into new file keeping only "/" from root column  */
					fwrite(line, chunk - line + 1, 1, new_mountinfo_fp);
					fwrite(chunk_end, line_len - (chunk_end - line), 1, new_mountinfo_fp);
					found_line = true;
					break;
				}
end_line_scan: ;
			}

			/* Once root was added, rescan and add other standard mounts  */
			if (found_line) {
				fseek(real_mountinfo_fp, 0, SEEK_SET);
				while ((line_len = getline(&line, &line_buf_len, real_mountinfo_fp)) > 0) {
					char *chunk = line;
					/* Skip columns before 'root'  */
					for (int i = 0; i < 4 && chunk - line < line_len; i++) {
						chunk = strchr(chunk, ' ');
						if (chunk == NULL) goto end_line_scan2;
						chunk++;
					}

					/* Match path  */
					char *chunk_end = strchr(chunk, ' ');
					if (chunk_end == NULL) continue;

					size_t mount_len = chunk_end - chunk;
					if (
							(mount_len == 4 && 0 == memcmp(chunk, "/dev", 4)) ||
							(mount_len >= 5 && 0 == memcmp(chunk, "/dev/", 5)) ||
							(mount_len == 5 && 0 == memcmp(chunk, "/proc", 5)) ||
							(mount_len == 4 && 0 == memcmp(chunk, "/sys", 4)) ||
							(mount_len >= 5 && 0 == memcmp(chunk, "/sys/", 5)) ||
							(mount_len == 4 && 0 == memcmp(chunk, "/tmp", 4))
							) {
						/* Copy line into new file verbatim  */
						fwrite(line, line_len, 1, new_mountinfo_fp);
					}
end_line_scan2: ;
				}
			}
		} else {
			/* Non-Android case: copy real mountinfo verbatim.  */
			while ((line_len = getline(&line, &line_buf_len, real_mountinfo_fp)) > 0)
				fwrite(line, line_len, 1, new_mountinfo_fp);
			found_line = true;
		}

		/* Append synthesized entries for runtime bindings so
		 * helpers like bubblewrap find the mounts they think
		 * they just created.  */
		append_runtime_binding_lines(target_tracee, new_mountinfo_fp);

		free(line);
		fclose(new_mountinfo_fp);
		fclose(real_mountinfo_fp);

		/* Redirect open to our temp file  */
		if (found_line) {
			strncpy(path, new_path, PATH_MAX);
		}
		return;
	}

}

int mountinfo_callback(Extension *extension, ExtensionEvent event,
        intptr_t data1 UNUSED, intptr_t data2 UNUSED)
{
    switch (event) {
    case TRANSLATED_PATH:
	{
		Tracee *tracee = TRACEE(extension);
		Sysnum num = get_sysnum(tracee, ORIGINAL);
		if (num == PR_open || num == PR_openat) {
			mountinfo_check_open_path(tracee, (char*) data1);
		}
        return 0;
	}

    default:
        return 0;
    }
}
