#include <linux/limits.h>
#include <errno.h>
#include <sys/stat.h>

#include "extension/fake_id0/exec.h"

#include "extension/fake_id0/helper_functions.h"

/** Handles execve system calls. Checks permissions in a meta file if it exists
 *  and returns errors matching those in execve(2).
 */
int handle_exec_enter_end(Tracee *tracee, Reg filename_sysarg, Config *config)
{
	int status, perms;
	char path[PATH_MAX];
	char meta_path[PATH_MAX];
	uid_t uid;
	gid_t gid;
	mode_t mode;

	status = read_sysarg_path(tracee, path, filename_sysarg, ORIGINAL);
	if(status < 0) 
		return status;
	if(status == 1) 
		return 0;

	status = get_meta_path(path, meta_path);
	if(status < 0) 
		return status;

	/* If metafile doesn't exist, get out, but don't error. */
	if(path_exists(meta_path) != 0)
		return 0;
	
	/* Check perms relative to / since there is no dirfd argument to execve */
	status = check_dir_perms(tracee, 'r', meta_path, "/", config);
	if(status < 0) 
		return status;
	
	/* Check whether the file has execute permission. */
	perms = get_permissions(meta_path, config, 0);
	if((perms & 1) != 1) 
		return -EACCES;

	/* If the setuid or setgid bits are on, change config accordingly. */
	read_meta_file(meta_path, &mode, &uid, &gid, config);
	if ((mode & S_ISUID) != 0) {
		config->ruid = 0;
		config->euid = 0;
		config->suid = 0;
	}

	if ((mode & S_ISGID) != 0) {
		config->rgid = 0;
		config->egid = 0;
		config->sgid = 0;
	}

	/** TODO Add logic to determine interpreter being used, and check
	 *  permissions for it.
	 */

	return 0;
}
