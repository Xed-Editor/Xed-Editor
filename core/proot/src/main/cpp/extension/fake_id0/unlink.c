#include <linux/limits.h>
#include <unistd.h>

#include "extension/fake_id0/unlink.h"
#include "extension/fake_id0/helper_functions.h"

/** Handles unlink, unlinkat, and rmdir syscalls. Checks permissions in meta 
 *  files matching the file to be unlinked if the meta file exists. Unlinks
 *  the meta file if the call would be successful. See unlink(2) and rmdir(2)
 *  for returned errors.
 */
int handle_unlink_enter_end(Tracee *tracee, Reg fd_sysarg, Reg path_sysarg, Config *config)
{
	int status;
	char orig_path[PATH_MAX];
	char rel_path[PATH_MAX];
	char meta_path[PATH_MAX];
	
	status = read_sysarg_path(tracee, orig_path, path_sysarg, CURRENT); 
	if(status < 0) 
		return status;
	if(status == 1)
		return 0;

	status = get_meta_path(orig_path, meta_path);
	if(status < 0) 
		return status;
	
	status = get_fd_path(tracee, rel_path, fd_sysarg, CURRENT);
	if(status < 0) 
		return status;
	
	status = check_dir_perms(tracee, 'w', orig_path, rel_path, config);
	if(status < 0) 
		return status;
 
	/** If the meta_file relating to the file being unlinked exists,
	 *  unlink that as well.
	 */
	if(path_exists(meta_path) == 0) 
		unlink(meta_path);

	return 0;
}
