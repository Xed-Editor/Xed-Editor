#include <linux/limits.h>

#include "extension/fake_id0/rename.h"
#include "extension/fake_id0/helper_functions.h"

/** Handles symlink and symlinkat syscalls. Returns -EACCES if search
 *  permission is not found for the directories except the final in newpath.
 *  Write permission is required for that directory. Unlike with link(2), 
 *  symlink does not require permissions on the original path.
 */
int handle_symlink_enter_end(Tracee *tracee, Reg oldpath_sysarg,
	Reg newdirfd_sysarg, Reg newpath_sysarg, Config *config)
{
	int status;
	char oldpath[PATH_MAX];
	char newpath[PATH_MAX];
	char rel_newpath[PATH_MAX];

	status = read_sysarg_path(tracee, oldpath, oldpath_sysarg, CURRENT);
	if(status < 0) 
		return status;

	status = read_sysarg_path(tracee, newpath, newpath_sysarg, CURRENT);
	if(status < 0) 
		return status;
	if(status == 1) 
		return 0;

	status = get_fd_path(tracee, rel_newpath, newdirfd_sysarg, CURRENT);
	if(status < 0)
		return status;

	status = check_dir_perms(tracee, 'w', newpath, rel_newpath, config);
	if(status < 0)
		return status;

	return 0;
}
