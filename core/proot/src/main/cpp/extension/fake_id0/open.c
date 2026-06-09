#include <errno.h>
#include <fcntl.h>
#include <linux/limits.h>
#include <sys/stat.h>
#include <sys/types.h>

#include "tracee/reg.h"

#include "extension/fake_id0/open.h"
#include "extension/fake_id0/helper_functions.h"

/** Handles open, openat, and creat syscalls. Creates meta files to match the
 *  creation of new files, or checks the permissions of files that already
 *  exist given a matching meta file. See open(2) for returned permission
 *  errors.
 */
int handle_open_enter_end(Tracee *tracee, Reg fd_sysarg, Reg path_sysarg, 
	Reg flags_sysarg, Reg mode_sysarg, Config *config)
{   
	int status, perms, access_mode;
	char orig_path[PATH_MAX];
	char rel_path[PATH_MAX];
	char meta_path[PATH_MAX];
	word_t flags;
	mode_t mode;

	status = read_sysarg_path(tracee, orig_path, path_sysarg, CURRENT);
	if(status < 0) 
		return status;
	if(status == 1) 
		return 0;

	status = get_meta_path(orig_path, meta_path);
	if(status < 0) 
		return status;
 
	if(flags_sysarg != IGNORE_SYSARG) 
		flags = peek_reg(tracee, ORIGINAL, flags_sysarg);
	else  
		flags = O_CREAT;

	/* If the metafile doesn't exist and we aren't creating a new file, get out. */
	if(path_exists(meta_path) != 0 && (flags & O_CREAT) != O_CREAT) 
		return 0;

	status = get_fd_path(tracee, rel_path, fd_sysarg, CURRENT);
	if(status < 0) 
		return status; 
	
	/** If the open call is a creat call (flags is set to IGNORE_SYSARG in 
	 *  handle_sysenter_end) or an open call intended to create a new file
	 *  then we write a new meta file to match. Note that flags is compared
	 *  only to O_CREAT because some utilities (like touch) do not
	 *  use O_TRUNC and O_WRONLY and instead incorporate other flags.
	 *  A value in flags_sysarg of IGNORE_SYSARG signifies a creat(2) call.
	 */
	if((flags & O_CREAT) == O_CREAT) { 

		/** Many open calls include O_CREAT flags even if the file exists
		 *  already. Probably because many things don't check existence and
		 *  just tell open to create a file if it doesn't exist. In the cases
		 *  a file does exist already, the permissions of it still need to be
		 *  checked.
		 */
		if(path_exists(orig_path) == 0) 
			goto check;

		status = check_dir_perms(tracee, 'w', meta_path, rel_path, config);
		if(status < 0) 
			return status;

		mode = peek_reg(tracee, ORIGINAL, mode_sysarg);
		poke_reg(tracee, mode_sysarg, (mode|0700));
		status = write_meta_file(meta_path, mode, config->euid, config->egid, 1, config);
		return status;
	}

check:

	status = check_dir_perms(tracee, 'r', meta_path, rel_path, config);
	if(status < 0) 
		return status;
	
	perms = get_permissions(meta_path, config, 0); 
	access_mode = (flags & O_ACCMODE);

	/* 0 = RDONLY, 1 = WRONLY, 2 = RDWR */
	if((access_mode == O_WRONLY && (perms & 2) != 2) ||
	(access_mode == O_RDONLY && (perms & 4) != 4) ||
	(access_mode == O_RDWR && (perms & 6) != 6)) {
		return -EACCES;
	}

	return 0;
}

