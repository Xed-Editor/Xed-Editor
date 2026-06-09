#include <errno.h>          /* E*, */
#include <sys/sysmacros.h>  /* major, minor, */

#include "tracee/statx.h"
#include "tracee/mem.h"

int handle_statx_syscall(Tracee *tracee, bool from_sigsys) {
	RegVersion regVersion = from_sigsys ? CURRENT : ORIGINAL;
	struct statx_syscall_state state = {};
	char guest_path[PATH_MAX] = {};
	struct stat stat_buf = {};
	bool do_fstat = false;

	/* Read arguments and translate path */
	word_t flags = peek_reg(tracee, regVersion, SYSARG_3);
	bool do_lstat = ((flags & AT_SYMLINK_NOFOLLOW) != 0);
	word_t mask = peek_reg(tracee, regVersion, SYSARG_4);
	int status = read_string(tracee, guest_path, peek_reg(tracee, regVersion, SYSARG_2), PATH_MAX);
	if (status < 0) {
		return status;
	}

	word_t dirfd = peek_reg(tracee, regVersion, SYSARG_1);
	if (status == 0) {
		return -EFAULT;
	}
	if (status == 1) {
		if ((flags & AT_EMPTY_PATH) == 0) {
			return -ENOENT;
		}
		status = readlink_proc_pid_fd(tracee->pid, dirfd, state.host_path);
		do_fstat = true;
	} else {
		if (status >= PATH_MAX) {
			return -ENAMETOOLONG;
		}
		status = translate_path(tracee, state.host_path, dirfd, guest_path, !do_lstat);
	}
	if (status < 0) {
		return status;
	}

	if (from_sigsys || peek_reg(tracee, CURRENT, SYSARG_RESULT) != 0) {
		/* Call [l]stat() on translated path */
		if (do_fstat) {
			char link[32] = {}; /* 32 > sizeof("/proc//cwd") + sizeof(#ULONG_MAX) */
			snprintf(link, sizeof(link), "/proc/%d/fd/%d", tracee->pid, (int) dirfd);
			status = stat(link, &stat_buf);
		} else if (do_lstat) {
			status = lstat(state.host_path, &stat_buf);
		} else {
			status = stat(state.host_path, &stat_buf);
		}
		if (status < 0) {
			status = -errno;
			if (status >= 0) status = -EPERM;
			return status;
		}

		/* Translate results from stat to statx */
		state.statx_buf.stx_mask = (
			mask & (
				STATX_TYPE |
				STATX_MODE |
				STATX_NLINK |
				STATX_UID |
				STATX_GID |
				STATX_ATIME |
				STATX_MTIME |
				STATX_CTIME |
				STATX_INO |
				STATX_SIZE |
				STATX_BLOCKS |
				STATX_BTIME
			)
		);
		state.statx_buf.stx_blksize = stat_buf.st_blksize;
		if (mask & (STATX_TYPE | STATX_MODE)) {
			state.statx_buf.stx_mode = stat_buf.st_mode;
		}
		if (mask & STATX_NLINK) {
			state.statx_buf.stx_nlink = stat_buf.st_nlink;
		}
		if (mask & STATX_UID) {
			state.statx_buf.stx_uid = stat_buf.st_uid;
		}
		if (mask & STATX_GID) {
			state.statx_buf.stx_gid = stat_buf.st_gid;
		}
		if (mask & STATX_ATIME) {
			state.statx_buf.stx_atime.tv_sec = stat_buf.st_atim.tv_sec;
			state.statx_buf.stx_atime.tv_nsec = stat_buf.st_atim.tv_nsec;
		}
		if (mask & STATX_MTIME) {
			state.statx_buf.stx_mtime.tv_sec = stat_buf.st_mtim.tv_sec;
			state.statx_buf.stx_mtime.tv_nsec = stat_buf.st_mtim.tv_nsec;
		}
		if (mask & STATX_CTIME) {
			state.statx_buf.stx_ctime.tv_sec = stat_buf.st_ctim.tv_sec;
			state.statx_buf.stx_ctime.tv_nsec = stat_buf.st_ctim.tv_nsec;
		}
		if (mask & STATX_INO) {
			state.statx_buf.stx_ino = stat_buf.st_ino;
		}
		if (mask & STATX_SIZE) {
			state.statx_buf.stx_size = stat_buf.st_size;
		}
		if (mask & STATX_BLOCKS) {
			state.statx_buf.stx_blocks = stat_buf.st_blocks;
		}
		if (mask & STATX_BTIME) {
			// stat() doesn't expose this, take ctime
			state.statx_buf.stx_btime.tv_sec = stat_buf.st_ctim.tv_sec;
			state.statx_buf.stx_btime.tv_nsec = stat_buf.st_ctim.tv_nsec;
		}
		state.statx_buf.stx_rdev_major = major(stat_buf.st_rdev);
		state.statx_buf.stx_rdev_minor = minor(stat_buf.st_rdev);
		state.updated_stats = true;
	} else {
		status = read_data(tracee, &state.statx_buf, peek_reg(tracee, ORIGINAL, SYSARG_5), sizeof(struct statx));
		if (status < 0) {
			return status;
		}
	}

	/* Notify extensions */
	status = notify_extensions(tracee, STATX_SYSCALL, (intptr_t) &state, 0);
	if (status < 0) {
		return status;
	}

	/* Return results to tracee */
	if (state.updated_stats) {
		status = write_data(tracee, peek_reg(tracee, CURRENT, SYSARG_5), &state.statx_buf, sizeof(state.statx_buf));
		if (status < 0) {
			return status;
		}
	}
	return 0;
}
