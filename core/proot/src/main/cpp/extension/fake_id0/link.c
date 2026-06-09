#include <linux/limits.h>

#include "extension/fake_id0/link.h"

#include "extension/fake_id0/helper_functions.h"

/** Handles link and linkat. Returns -EACCES if search permission is not
 *  given for the entire relative oldpath and the entire relative newpath
 *  except where write permission is needed (on the final directory component).
 */
int handle_link_enter_end(Tracee *tracee, Reg olddirfd_sysarg, Reg oldpath_sysarg,
	Reg newdirfd_sysarg, Reg newpath_sysarg, Config *config)
{
	int status;
	char oldpath[PATH_MAX];
	char rel_oldpath[PATH_MAX];
	char newpath[PATH_MAX];
	char rel_newpath[PATH_MAX];

	status = read_sysarg_path(tracee, oldpath, oldpath_sysarg, ORIGINAL);
	if(status < 0)
		return status;
	if(status == 1)
		return 0;

	status = read_sysarg_path(tracee, newpath, newpath_sysarg, ORIGINAL);
	if(status < 0)
		return status;
	if(status == 1)
		return 0;

	status = get_fd_path(tracee, rel_oldpath, olddirfd_sysarg, ORIGINAL);
	if(status < 0)
		return status;

	status = get_fd_path(tracee, rel_newpath, newdirfd_sysarg, ORIGINAL);
	if(status < 0)
		return status;

	status = check_dir_perms(tracee, 'r', oldpath, rel_oldpath, config);
	if(status < 0)
		return status;

	status = check_dir_perms(tracee, 'w', newpath, rel_newpath, config);
	if(status < 0)
		return status;

	return 0;
}
