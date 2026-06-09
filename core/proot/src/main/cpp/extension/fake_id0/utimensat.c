#include <linux/limits.h>
#include <errno.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>

#include "tracee/mem.h"
#include "extension/fake_id0/utimensat.h"
#include "extension/fake_id0/helper_functions.h"

/** Handles the utimensat syscall. Checks permissions of the meta file if it
 *  exists and returns an error if the call would not pass according to the 
 *  errors found in utimensat(2).
 */
int handle_utimensat_enter_end(Tracee *tracee, Reg dirfd_sysarg, 
	Reg path_sysarg, Reg times_sysarg, Config *config)
{
	int status, perms, fd;
	struct timespec times[2];
	mode_t ignore_m;
	uid_t owner;
	gid_t ignore_g;
	char path[PATH_MAX];
	char meta_path[PATH_MAX];

	// Only care about calls that attempt to change something.
	status = peek_reg(tracee, ORIGINAL, times_sysarg);
	if(status != 0) {
		status = read_data(tracee, times, peek_reg(tracee, ORIGINAL, times_sysarg), sizeof(times));
		if(times[0].tv_nsec != UTIME_NOW && times[1].tv_nsec != UTIME_NOW) 
			return 0;
	}

	fd = peek_reg(tracee, ORIGINAL, dirfd_sysarg);
	if(fd == AT_FDCWD) {
		status = read_sysarg_path(tracee, path, path_sysarg, CURRENT);
		if(status < 0) 
			return status;
		if(status == 1)
			return 0;
	}
	else {
		status = get_fd_path(tracee, path, dirfd_sysarg, CURRENT);
		if(status < 0)
			return status;
	}

	status = get_meta_path(path, meta_path);
	if(status < 0)
		return status;

	// Current user must be owner of file or root.
	read_meta_file(meta_path, &ignore_m, &owner, &ignore_g, config);
	if(config->euid != owner && config->euid != 0) 
		return -EACCES;

	// If write permissions are on the file, continue.
	perms = get_permissions(meta_path, config, 0);
	if((perms & 2) != 2)
		return -EACCES;

	return 0;
}
