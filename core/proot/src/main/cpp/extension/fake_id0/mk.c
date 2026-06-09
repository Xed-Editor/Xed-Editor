#include <linux/limits.h>

#include "extension/fake_id0/mk.h"

#include "extension/fake_id0/helper_functions.h"

/** Handles mkdir, mkdirat, mknod, and mknodat syscalls. Creates a matching
 *  meta file. See mkdir(2) and mknod(2) for returned permission errors.
 */
int handle_mk_enter_end(Tracee *tracee, Reg fd_sysarg, Reg path_sysarg, 
	Reg mode_sysarg, Config *config)
{
	int status;
	mode_t mode;
	char orig_path[PATH_MAX];
	char rel_path[PATH_MAX];
	char meta_path[PATH_MAX];

	status  = read_sysarg_path(tracee, orig_path, path_sysarg, CURRENT);
	if(status < 0)
		return status;
	if(status == 1)
		return 0;

	/* If the path exists, get out. The syscall itself will return EEXIST. */
	if(path_exists(orig_path) == 0)
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
	
	mode = peek_reg(tracee, ORIGINAL, mode_sysarg);
	poke_reg(tracee, mode_sysarg, (mode|0700));
	return write_meta_file(meta_path, mode, config->euid, config->egid, 1, config);
}
