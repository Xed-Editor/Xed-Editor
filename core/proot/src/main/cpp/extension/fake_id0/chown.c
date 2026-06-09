#include <unistd.h>      /* get*id(2),  */
#include <linux/limits.h>
#include <errno.h>

#include "syscall/sysnum.h"
#include "extension/fake_id0/chown.h"
#include "extension/fake_id0/helper_functions.h"

#ifndef USERLAND
int handle_chown_enter_end(Tracee *tracee, Config *config, Reg uid_sysarg, Reg gid_sysarg) {
	uid_t uid;
	gid_t gid;

	uid = peek_reg(tracee, ORIGINAL, uid_sysarg);
	gid = peek_reg(tracee, ORIGINAL, gid_sysarg);

	/* Swap actual and emulated ids to get a chance of
	 * success.  */
	if (uid == config->ruid)
		poke_reg(tracee, uid_sysarg, getuid());
	if (gid == config->rgid)
		poke_reg(tracee, gid_sysarg, getgid());

	return 0;
}
#endif /* ifndef USERLAND */

#ifdef USERLAND
/** Handles chown, lchown, fchown, and fchownat syscalls. Changes the meta file
 *  to reflect arguments sent to the syscall if the meta file exists. See
 *  chown(2) for returned permission errors.
 */
int handle_chown_enter_end(Tracee *tracee, Reg path_sysarg, Reg owner_sysarg,
	Reg group_sysarg, Reg fd_sysarg, Reg dirfd_sysarg, Config *config)
{
	int status;
	mode_t mode;
	uid_t owner, read_owner;
	gid_t group, read_group;
	char path[PATH_MAX];
	char rel_path[PATH_MAX];
	char meta_path[PATH_MAX];
	
	if(path_sysarg == IGNORE_SYSARG)
		status = get_fd_path(tracee, path, fd_sysarg, CURRENT);
	else
		status = read_sysarg_path(tracee, path, path_sysarg, CURRENT);
	if(status < 0)
		return status;
	// If the path exists outside the guestfs, drop the syscall.
	else if(status == 1) {
		set_sysnum(tracee, PR_getuid);
		return 0;
	}

	status = get_meta_path(path, meta_path);
	if(status < 0)
		return status;

	if(path_exists(meta_path) != 0)
		return 0;

	status = get_fd_path(tracee, rel_path, dirfd_sysarg, CURRENT);
	if(status < 0)
		return status;

	status = check_dir_perms(tracee, 'r', path, rel_path, config);
	if(status < 0)
		return status;

	read_meta_file(meta_path, &mode, &read_owner, &read_group, config);
	owner = peek_reg(tracee, ORIGINAL, owner_sysarg);
	/** When chown is called without an owner specified, eg 
	 *  chown :1000 'file', the owner argument to the system call is implicitly
	 *  set to -1. To avoid this, the owner argument is replaced with the owner
	 *  according to the meta file if it exists, or the current euid.
	 */
	if((int) owner == -1)
		owner = read_owner;
	group = peek_reg(tracee, ORIGINAL, group_sysarg);
	if(config->euid == 0) 
		write_meta_file(meta_path, mode, owner, group, 0, config);

	//TODO Handle chown properly: owner can only change the group of
	//  a file to another group they belong to.
	else if(config->euid == read_owner) {
		write_meta_file(meta_path, mode, read_owner, group, 0, config);
		poke_reg(tracee, owner_sysarg, read_owner);	
	}

	else if(config->euid != read_owner) 
		return -EPERM;

	set_sysnum(tracee, PR_getuid);

	return 0;
}
#endif /* ifdef USERLAND */
