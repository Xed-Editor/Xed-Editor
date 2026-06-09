#include <linux/limits.h>
#include <errno.h>
#include <unistd.h>

#include "extension/fake_id0/access.h"
#include "extension/fake_id0/helper_functions.h"

/** Handles the access and faccessat syscalls. Checks permissions according to
 *  a meta file if it exists. See access(2) for returned errors.
 */
int handle_access_enter_end(Tracee *tracee, Reg path_sysarg,
	Reg mode_sysarg, Reg dirfd_sysarg, Config *config)
{
	int status, mode, perms, mask;
	char path[PATH_MAX];
	char rel_path[PATH_MAX];
	char meta_path[PATH_MAX];

	status = read_sysarg_path(tracee, path, path_sysarg, CURRENT);
	if(status < 0)
		return status;
	if(status == 1)
		return 0;

	status = get_fd_path(tracee, rel_path, dirfd_sysarg, CURRENT);
	if(status < 0)
		return status;

	status = check_dir_perms(tracee, 'r', path, rel_path, config);
	if(status < 0) 
		return status;

	// Only care about calls checking permissions.
	mode = peek_reg(tracee, ORIGINAL, mode_sysarg);
	if(mode & F_OK) 
		return 0;

	status = get_meta_path(path, meta_path);
	if(status < 0)
		return status;

	mask = 0;
	if((mode & R_OK) == R_OK)
		mask += 4;
	if((mode & W_OK) == W_OK)
		mask += 2;
	if((mode & X_OK) == X_OK)
		mask += 1; 

	perms = get_permissions(meta_path, config, 1);
	if((perms & mask) != mask) 
		return -EACCES;

	return 0;
}
