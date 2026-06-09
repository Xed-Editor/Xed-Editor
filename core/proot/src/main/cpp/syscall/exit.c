/* -*- c-set-style: "K&R"; c-basic-offset: 8 -*-
 *
 * This file is part of PRoot.
 *
 * Copyright (C) 2015 STMicroelectronics
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

#include <errno.h>       /* errno(3), E* */
#include <sys/utsname.h> /* struct utsname, */
#include <linux/net.h>   /* SYS_*, */
#include <linux/ioctl.h> /* _IOW, */
#include <linux/prctl.h> /* PR_GET_AUXV, */
#include <string.h>      /* strlen(3), */
#include <unistd.h>      /* readlink(2), */

#include "cli/note.h"
#include "syscall/syscall.h"
#include "syscall/sysnum.h"
#include "syscall/socket.h"
#include "syscall/chain.h"
#include "syscall/heap.h"
#include "syscall/rlimit.h"
#include "execve/execve.h"
#include "tracee/tracee.h"
#include "tracee/reg.h"
#include "tracee/mem.h"
#include "tracee/abi.h"
#include "tracee/seccomp.h"
#include "tracee/statx.h"
#include "path/path.h"
#include "ptrace/ptrace.h"
#include "ptrace/wait.h"
#include "extension/extension.h"
#include "arch.h"

/**
 * Translate the output arguments of the current @tracee's syscall in
 * the @tracee->pid process area. This function sets the result of
 * this syscall to @tracee->status if an error occured previously
 * during the translation, that is, if @tracee->status is less than 0.
 */
void translate_syscall_exit(Tracee *tracee)
{
	word_t syscall_number;
	word_t syscall_result;
	int status;

	status = notify_extensions(tracee, SYSCALL_EXIT_START, 0, 0);
	if (status < 0) {
		poke_reg(tracee, SYSARG_RESULT, (word_t) status);
		goto end;
	}
	if (status > 0)
		return;

	/* Set the tracee's errno if an error occured previously during
	 * the translation. */
	if (tracee->status < 0) {
		poke_reg(tracee, SYSARG_RESULT, (word_t) tracee->status);
		goto end;
	}

	/* If proot changed syscall to PR_void during enter,
	 * keep syscall result set during entry. */
	if (peek_reg(tracee, MODIFIED, SYSARG_NUM) ==
#if defined(ARCH_ARM64) || defined(ARCH_X86_64)
			(is_32on64_mode(tracee) ? (SYSCALL_AVOIDER & 0xFFFFFFFF) : SYSCALL_AVOIDER)
#else
			SYSCALL_AVOIDER
#endif
			&&
			peek_reg(tracee, ORIGINAL, SYSARG_NUM) != peek_reg(tracee, MODIFIED, SYSARG_NUM)) {
		poke_reg(tracee, SYSARG_RESULT, peek_reg(tracee, MODIFIED, SYSARG_RESULT));
	}

	/* Translate output arguments:
	 * - break: update the syscall result register with "status"
	 * - goto end: nothing else to do.
	 */
	syscall_number = get_sysnum(tracee, ORIGINAL);
	syscall_result = peek_reg(tracee, CURRENT, SYSARG_RESULT);
	switch (syscall_number) {
	case PR_brk:
		translate_brk_exit(tracee);
		goto end;

	case PR_getcwd: {
		char path[PATH_MAX];
		size_t new_size;
		size_t size;
		word_t output;

		size = (size_t) peek_reg(tracee, ORIGINAL, SYSARG_2);
		if (size == 0) {
			status = -EINVAL;
			break;
		}

		/* Ensure cwd still exists.  */
		status = translate_path(tracee, path, AT_FDCWD, ".", false);
		if (status < 0)
			break;

		new_size = strlen(tracee->fs->cwd) + 1;
		if (size < new_size) {
			status = -ERANGE;
			break;
		}

		/* Overwrite the path.  */
		output = peek_reg(tracee, ORIGINAL, SYSARG_1);
		status = write_data(tracee, output, tracee->fs->cwd, new_size);
		if (status < 0)
			break;

		/* The value of "status" is used to update the returned value
		 * in translate_syscall_exit().  */
		status = new_size;
		break;
	}

	case PR_accept:
	case PR_accept4:
		/* Nothing special to do if no sockaddr was specified.  */
		if (peek_reg(tracee, ORIGINAL, SYSARG_2) == 0)
			goto end;
		/* Fall through.  */
	case PR_getsockname:
	case PR_getpeername: {
		word_t sock_addr;
		word_t size_addr;
		word_t max_size;

		/* Error reported by the kernel.  */
		if ((int) syscall_result < 0)
			goto end;

		sock_addr = peek_reg(tracee, ORIGINAL, SYSARG_2);
		size_addr = peek_reg(tracee, MODIFIED, SYSARG_3);
		max_size  = peek_reg(tracee, MODIFIED, SYSARG_6);

		status = translate_socketcall_exit(tracee, sock_addr, size_addr, max_size);
		if (status < 0)
			break;

		/* Don't overwrite the syscall result.  */
		goto end;
	}

#define SYSARG_ADDR(n) (args_addr + ((n) - 1) * sizeof_word(tracee))

#define POKE_WORD(addr, value)			\
	poke_word(tracee, addr, value);		\
	if (errno != 0)	{			\
		status = -errno;		\
		break;				\
	}

#define PEEK_WORD(addr)				\
	peek_word(tracee, addr);		\
	if (errno != 0) {			\
		status = -errno;		\
		break;				\
	}

	case PR_socketcall: {
		word_t args_addr;
		word_t sock_addr;
		word_t size_addr;
		word_t max_size;

		args_addr = peek_reg(tracee, ORIGINAL, SYSARG_2);

		switch (peek_reg(tracee, ORIGINAL, SYSARG_1)) {
		case SYS_ACCEPT:
		case SYS_ACCEPT4:
			/* Nothing special to do if no sockaddr was specified.  */
			sock_addr = PEEK_WORD(SYSARG_ADDR(2));
			if (sock_addr == 0)
				goto end;
			/* Fall through.  */
		case SYS_GETSOCKNAME:
		case SYS_GETPEERNAME:
			/* Handle these cases below.  */
			status = 1;
			break;

		case SYS_BIND:
		case SYS_CONNECT:
			/* Restore the initial parameters: this memory was
			 * overwritten at the enter stage.  Remember: POKE_WORD
			 * puts -errno in status and breaks if an error
			 * occured.  */
			POKE_WORD(SYSARG_ADDR(2), peek_reg(tracee, MODIFIED, SYSARG_5));
			POKE_WORD(SYSARG_ADDR(3), peek_reg(tracee, MODIFIED, SYSARG_6));

			status = 0;
			break;

		default:
			status = 0;
			break;
		}

		/* Error reported by the kernel or there's nothing else to do.  */
		if ((int) syscall_result < 0 || status == 0)
			goto end;

		/* An error occured in SYS_BIND or SYS_CONNECT.  */
		if (status < 0)
			break;

		/* Remember: PEEK_WORD puts -errno in status and breaks if an
		 * error occured.  */
		sock_addr = PEEK_WORD(SYSARG_ADDR(2));
		size_addr = PEEK_WORD(SYSARG_ADDR(3));
		max_size  = peek_reg(tracee, MODIFIED, SYSARG_6);

		status = translate_socketcall_exit(tracee, sock_addr, size_addr, max_size);
		if (status < 0)
			break;

		/* Don't overwrite the syscall result.  */
		goto end;
	}

#undef SYSARG_ADDR
#undef PEEK_WORD
#undef POKE_WORD

	case PR_fchdir:
	case PR_chdir:
	/* These syscalls are voided in enter.c; make sure the
	 * tracee always sees a 0 return value even on kernels where
	 * the SYSCALL_AVOIDER trick leaks -ENOSYS through.  */
	case PR_unshare:
	case PR_setns:
	case PR_mount:
	case PR_umount:
	case PR_umount2:
	case PR_pivot_root:
		/* These syscalls are fully emulated, see enter.c for details
		 * (like errors).  */
		status = 0;
		break;

	case PR_rename:
	case PR_renameat: {
		char old_path[PATH_MAX];
		char new_path[PATH_MAX];
		ssize_t old_length;
		ssize_t new_length;
		Comparison comparison;
		Reg old_reg;
		Reg new_reg;
		char *tmp;

		/* Error reported by the kernel.  */
		if ((int) syscall_result < 0)
			goto end;

		if (syscall_number == PR_rename) {
			old_reg = SYSARG_1;
			new_reg = SYSARG_2;
		}
		else {
			old_reg = SYSARG_2;
			new_reg = SYSARG_4;
		}

		/* Get the old path, then convert it to the same
		 * "point-of-view" as tracee->fs->cwd (guest).  */
		status = read_path(tracee, old_path, peek_reg(tracee, MODIFIED, old_reg));
		if (status < 0)
			break;

		status = detranslate_path(tracee, old_path, NULL);
		if (status < 0)
			break;
		old_length = (status > 0 ? status - 1 : (ssize_t) strlen(old_path));

		/* Nothing special to do if the moved path is not the
		 * current working directory.  */
		comparison = compare_paths(old_path, tracee->fs->cwd);
		if (comparison != PATH1_IS_PREFIX && comparison != PATHS_ARE_EQUAL) {
			status = 0;
			break;
		}

		/* Get the new path, then convert it to the same
		 * "point-of-view" as tracee->fs->cwd (guest).  */
		status = read_path(tracee, new_path, peek_reg(tracee, MODIFIED, new_reg));
		if (status < 0)
			break;

		status = detranslate_path(tracee, new_path, NULL);
		if (status < 0)
			break;
		new_length = (status > 0 ? status - 1 : (ssize_t) strlen(new_path));

		/* Sanity check.  */
		if (strlen(tracee->fs->cwd) >= PATH_MAX) {
			status = 0;
			break;
		}
		strcpy(old_path, tracee->fs->cwd);

		/* Update the virtual current working directory.  */
		substitute_path_prefix(old_path, old_length, new_path, new_length);

		tmp = talloc_strdup(tracee->fs, old_path);
		if (tmp == NULL) {
			status = -ENOMEM;
			break;
		}

		TALLOC_FREE(tracee->fs->cwd);
		tracee->fs->cwd = tmp;

		status = 0;
		break;
	}

	case PR_readlink:
	case PR_readlinkat: {
		char referee[PATH_MAX];
		char referer[PATH_MAX];
		size_t old_size;
		size_t new_size;
		size_t max_size;
		word_t input;
		word_t output;

		/* Error reported by the kernel.  */
		if ((int) syscall_result < 0)
			goto end;

		old_size = syscall_result;

		if (syscall_number == PR_readlink) {
			output   = peek_reg(tracee, ORIGINAL, SYSARG_2);
			max_size = peek_reg(tracee, ORIGINAL, SYSARG_3);
			input    = peek_reg(tracee, MODIFIED, SYSARG_1);
		}
		else {
			output   = peek_reg(tracee, ORIGINAL,  SYSARG_3);
			max_size = peek_reg(tracee, ORIGINAL, SYSARG_4);
			input    = peek_reg(tracee, MODIFIED, SYSARG_2);
		}

		if (max_size > PATH_MAX)
			max_size = PATH_MAX;

		if (max_size == 0) {
			status = -EINVAL;
			break;
		}

		/* The kernel does NOT put the NULL terminating byte for
		 * readlink(2).  */
		status = read_data(tracee, referee, output, old_size);
		if (status < 0)
			break;
		referee[old_size] = '\0';

		/* Not optimal but safe (path is fully translated).  */
		status = read_path(tracee, referer, input);
		if (status < 0)
			break;

		if (status >= PATH_MAX) {
			status = -ENAMETOOLONG;
			break;
		}

		if (status == 1) {
			/* Empty path was passed (""),
			 * indicating that path is pointed to by fd passed in first argument */
			word_t dirfd = peek_reg(tracee, ORIGINAL, SYSARG_1);
			if (syscall_number == PR_readlink || dirfd < 0) {
				status = -EBADF;
				break;
			}
			status = readlink_proc_pid_fd(tracee->pid, dirfd, referer);
			if (status < 0)
				break;
		}

		/* If the kernel filled the whole output buffer, the symlink
		 * content was truncated to fit.  Detranslating a truncated
		 * host path yields a wrong, wrongly-short guest path -- and
		 * callers that only enlarge their buffer when readlink(2)
		 * returns exactly the buffer size (bubblewrap's
		 * readlink_malloc, glibc realpath, ...) never notice and
		 * silently use the broken path.  Host paths are much longer
		 * than the guest paths they map to (deep proot-distro rootfs
		 * prefix), so short guest targets truncate easily.  Re-read
		 * the link with a full-size buffer (referer is the translated
		 * host path) so the detranslation below sees the real target;
		 * readlink() simply fails for a non-symlink referer, leaving
		 * the original content untouched.  */
		if (old_size == max_size) {
			ssize_t full = readlink(referer, referee, sizeof(referee) - 1);
			if (full > 0) {
				referee[full] = '\0';
				old_size = (size_t) full;
			}
		}

		status = detranslate_path(tracee, referee, referer);
		if (status < 0)
			break;

		/* The original path doesn't require any transformation, i.e
		 * it is a symetric binding.  */
		if (status == 0)
			goto end;

		/* Overwrite the path.  Note: the output buffer might be
		 * initialized with zeros but it was updated with the kernel
		 * result, and then with the detranslated result.  This later
		 * might be shorter than the former, so it's safier to add a
		 * NULL terminating byte when possible.  This problem was
		 * exposed by IDA Demo 6.3.  */
		if ((size_t) status < max_size) {
			new_size = status - 1;
			status = write_data(tracee, output, referee, status);
		}
		else {
			new_size = max_size;
			status = write_data(tracee, output, referee, max_size);
		}
		if (status < 0)
			break;

		/* The value of "status" is used to update the returned value
		 * in translate_syscall_exit().  */
		status = new_size;
		break;
	}

#if defined(ARCH_X86_64)
	case PR_uname: {
		struct utsname utsname;
		word_t address;
		size_t size;

		if (get_abi(tracee) != ABI_2)
			goto end;

		/* Error reported by the kernel.  */
		if ((int) syscall_result < 0)
			goto end;

		address = peek_reg(tracee, ORIGINAL, SYSARG_1);

		status = read_data(tracee, &utsname, address, sizeof(utsname));
		if (status < 0)
			break;

		/* Some 32-bit programs like package managers can be
		 * confused when the kernel reports "x86_64".  */
		size = sizeof(utsname.machine);
		strncpy(utsname.machine, "i686", size);
		utsname.machine[size - 1] = '\0';

		status = write_data(tracee, address, &utsname, sizeof(utsname));
		if (status < 0)
			break;

		status = 0;
		break;
	}
#endif

	case PR_execve:
	case PR_execveat:
		translate_execve_exit(tracee);
		goto end;

	case PR_openat2:
	case PR_openat:
	case PR_open: {
		/* Track /proc/self/auxv opens so read() results can be patched.
		 * Needed on kernels < 6.4 where prctl(PR_GET_AUXV) is absent and
		 * rustix falls back to reading /proc/self/auxv directly. */
		char path_buf[sizeof("/proc/self/auxv")];
		Reg path_reg = (syscall_number == PR_open) ? SYSARG_1 : SYSARG_2;

		if ((int) syscall_result < 0)
			goto end;
		if (tracee->execfn_addr == 0)
			goto end;
		if (read_string(tracee, path_buf,
		                peek_reg(tracee, ORIGINAL, path_reg),
		                sizeof(path_buf)) <= 0)
			goto end;
		if (strcmp(path_buf, "/proc/self/auxv") != 0)
			goto end;

		tracee->auxv_fd = (int) syscall_result;
		tracee->sysexit_pending = true;
		tracee->restart_how = PTRACE_SYSCALL;
		goto end;
	}

	case PR_read: {
		/* Patch AT_EXECFN in data read from /proc/self/auxv. */
		word_t fd, buf_addr, result, offset, entry_size, type;

		if (tracee->auxv_fd < 0 || tracee->execfn_addr == 0)
			goto end;

		result = syscall_result;
		if ((word_t) result == 0 || (ssize_t) result < 0)
			goto end;

		fd = peek_reg(tracee, ORIGINAL, SYSARG_1);
		if ((int) fd != tracee->auxv_fd)
			goto end;

		buf_addr   = peek_reg(tracee, ORIGINAL, SYSARG_2);
		entry_size = 2 * sizeof_word(tracee);

		for (offset = 0; offset + entry_size <= result; offset += entry_size) {
			errno = 0;
			type = peek_word(tracee, buf_addr + offset);
			if (errno != 0)
				break;
			if (type == AT_NULL)
				break;
			if (type == AT_EXECFN) {
				poke_word(tracee, buf_addr + offset + sizeof_word(tracee),
				          tracee->execfn_addr);
				break;
			}
		}

		/* Stay in PTRACE_SYSCALL mode to intercept close(auxv_fd). */
		tracee->sysexit_pending = true;
		tracee->restart_how = PTRACE_SYSCALL;
		goto end;
	}

	case PR_prctl: {
#ifndef PR_GET_AUXV
#define PR_GET_AUXV 0x41555856
#endif
		word_t option;
		word_t buf_addr;
		word_t buf_max;
		word_t offset;
		word_t entry_size;
		word_t type;

		option = peek_reg(tracee, ORIGINAL, SYSARG_1);

		/* Record the tracee's own request for the "no new privileges"
		 * flag so a later PR_GET_NO_NEW_PRIVS (answered at sysenter)
		 * reports the guest's intent rather than the flag PRoot set
		 * itself.  A successful call implies arg2 == 1, the only value
		 * the kernel accepts.  PRoot sets the real flag in the launch
		 * child before the initial execve (see enable_syscall_filtering),
		 * so only count calls made once the guest program is running
		 * (tracee->seen_execve); the flag is a one-way latch and is
		 * never cleared, matching the kernel's fork/execve semantics. */
		if (option == PR_SET_NO_NEW_PRIVS) {
			if (tracee->seen_execve && (int) syscall_result == 0)
				tracee->no_new_privs = true;
			goto end;
		}

		/* Only intercept PR_GET_AUXV. */
		if (option != PR_GET_AUXV)
			goto end;

		/* Error or no execfn to fix: nothing to do. */
		if ((int) syscall_result < 0)
			goto end;
		if (tracee->execfn_addr == 0)
			goto end;

		/* PR_GET_AUXV returns the auxv size; if it exceeds the buffer
		 * arg, the kernel did not write anything (buffer too small). */
		buf_max = peek_reg(tracee, ORIGINAL, SYSARG_3);
		if (syscall_result > buf_max)
			goto end;

		/* Scan the returned auxv buffer for AT_EXECFN and patch its
		 * value to point to argv[0] instead of the loader temp file. */
		buf_addr   = peek_reg(tracee, ORIGINAL, SYSARG_2);
		entry_size = 2 * sizeof_word(tracee);

		for (offset = 0; offset + entry_size <= syscall_result; offset += entry_size) {
			errno = 0;
			type = peek_word(tracee, buf_addr + offset);
			if (errno != 0)
				break;
			if (type == AT_NULL)
				break;
			if (type == AT_EXECFN) {
				poke_word(tracee, buf_addr + offset + sizeof_word(tracee),
					  tracee->execfn_addr);
				break;
			}
		}
		goto end;
	}

	case PR_ptrace:
		status = translate_ptrace_exit(tracee);
		break;

	case PR_wait4:
	case PR_waitpid:
		if (tracee->as_ptracer.waits_in != WAITS_IN_PROOT)
			goto end;

		status = translate_wait_exit(tracee);
		break;

	case PR_setrlimit:
	case PR_prlimit64:
		/* Error reported by the kernel.  */
		if ((int) syscall_result < 0)
			goto end;

		status = translate_setrlimit_exit(tracee, syscall_number == PR_prlimit64);
		if (status < 0)
			break;

		/* Don't overwrite the syscall result.  */
		goto end;
	
	case PR_utime:
		if ((int) syscall_result == -ENOSYS)
		{
			fix_and_restart_enosys_syscall(tracee);
		}
		goto end;

	case PR_statfs:
	case PR_statfs64: {
		/* Possibly fake that /dev/shm is living on tmpfs */
		char devshm_path[PATH_MAX];
		char statfs_path[PATH_MAX];

		/* Only perform changes to result of successful syscall
		 * (that is, path was valid, it doesn't have to point
		 * to mount root) */
		if (syscall_result != 0) {
			goto end;
		}

		if (translate_path(tracee, devshm_path, AT_FDCWD, "/dev/shm", true) < 0) {
			VERBOSE(tracee, 5, "/dev/shm is not mounted, not changing statfs() result");
			goto end;
		}

		if (read_path(tracee, statfs_path, peek_reg(tracee, MODIFIED, SYSARG_1)) < 0) {
			VERBOSE(tracee, 5, "statfs() exit couldn't read statfs_path");
			goto end;
		}

		Comparison comparison = compare_paths(devshm_path, statfs_path);
		if (comparison == PATHS_ARE_EQUAL || comparison == PATH1_IS_PREFIX) {
			VERBOSE(tracee, 5, "Updating statfs() result to fake tmpfs /dev/shm");
			/* Write TMPFS_MAGIC at beginning of statfs structure.
			 *
			 * (It's at beginning of structure regardless of syscall variant
			 * (statfs vs statfs64) and architecture bitness
			 * (on 64 bit this field is 8 bytes long, but as long as it's
			 * little endian, it will need only first 4 bytes to be modified,
			 * as next 4 bytes will always be 0))
			 * */
			word_t stat_addr = peek_reg(tracee, ORIGINAL, syscall_number == PR_statfs64 ? SYSARG_3 : SYSARG_2);
			int write_status = write_data(tracee, stat_addr, "\x94\x19\x02\x01", 4);
			if (write_status < 0) {
				VERBOSE(tracee, 5, "Updating statfs() result failed");
			}
		}
		else {
			VERBOSE(tracee, 5, "statfs() not for /dev/shm, not changing result");
		}

		goto end;
	}

	case PR_statx:
		status = handle_statx_syscall(tracee, false);
		break;

	case PR_ioctl:
		if (peek_reg(tracee, ORIGINAL, SYSARG_2) == _IOW(0x94, 9, int) /* FICLONE */ &&
				(int) peek_reg(tracee, CURRENT, SYSARG_RESULT) == -EACCES) {
			poke_reg(tracee, SYSARG_RESULT, -EOPNOTSUPP);
		}
		goto end;

	case PR_socket:
		/* Record the fd we substituted for an AF_NETLINK request.  */
		if (tracee->pending_fake_netlink_socket) {
			int fd = (int) peek_reg(tracee, CURRENT, SYSARG_RESULT);
			if (fd >= 0) {
				int i;
				if (tracee->fake_netlink_fds_count < MAX_FAKE_NETLINK_FDS) {
					/* Avoid duplicates.  */
					bool present = false;
					for (i = 0; i < tracee->fake_netlink_fds_count; i++) {
						if (tracee->fake_netlink_fds[i] == fd) {
							present = true;
							break;
						}
					}
					if (!present)
						tracee->fake_netlink_fds[tracee->fake_netlink_fds_count++] = fd;
				}
			}
			tracee->pending_fake_netlink_socket = false;
		}
		goto end;

	default:
		goto end;
	}

	poke_reg(tracee, SYSARG_RESULT, (word_t) status);

end:
	status = notify_extensions(tracee, SYSCALL_EXIT_END, 0, 0);
	if (status < 0)
		poke_reg(tracee, SYSARG_RESULT, (word_t) status);
}
