#include <stdlib.h>
#include <string.h>
#include <signal.h>
#include <unistd.h>
#include <sys/syscall.h>  /* __NR_memfd_create,  */
#include <linux/ashmem.h> /* ASHMEM_GET_SIZE,  */
#include <linux/memfd.h>  /* MFD_CLOEXEC  */

#include <talloc.h>

#include "extension/extension.h"
#include "path/path.h"
#include "tracee/mem.h"
#include "tracee/seccomp.h"
#include "syscall/chain.h"
#include "syscall/syscall.h" /* set_sysarg_data,  */

enum AshmemMemfdChainState {
	CS_IDLE,
	CS_STAT_ENTERED,
	CS_STAT_CHAINED_IOCTL
};

typedef struct {
	bool memfd_supported;
	enum AshmemMemfdChainState chain_state;
	int fd;
	word_t addr;
} AshmemMemfdState;

static FilteredSysnum filtered_sysnums[] = {
	{ PR_memfd_create,		0 },
	{ PR_ftruncate,		0 },
	{ PR_fstat,		0 },
	FILTERED_SYSNUM_END,
};

static int detect_memfd_support() {
	const char *assume_unsupported = getenv("PROOT_ASSUME_MEMFD_UNSUPPORTED");
	if (assume_unsupported != NULL) {
		if (0 == strcmp(assume_unsupported, "1")) {
			return 0;
		}
		if (0 == strcmp(assume_unsupported, "0")) {
			return 1;
		}
	}

	int reply_pipe[2];
	int status = pipe(reply_pipe);
	if (status < 0) {
		return -1;
	}

	status = fork();
	if (status < 0) {
		close(reply_pipe[0]);
		close(reply_pipe[1]);
		return -1;
	}

	if (status == 0) {
		/** Child process. Close readable end of pipe.  */
		close(reply_pipe[0]);

		/** Attempt creating memfd.  */
		signal(SIGSYS, SIG_DFL);
		int memfd = syscall(__NR_memfd_create, "support_probe", 0);

		/** Send message to parent on success.  */
		if (memfd >= 0) {
			write(reply_pipe[1], "\x01", 1);
			close(memfd);
		}
		close(reply_pipe[1]);
		_exit(0);
	}

	/** Parent process.  */
	close(reply_pipe[1]);
	char reply_value = 0;
	read(reply_pipe[0], &reply_value, 1);
	close(reply_pipe[0]);
	return reply_value == 1;
}

static bool is_ashmem_fd(Tracee *tracee, int fd) {
	char path[PATH_MAX] = {};
	if (readlink_proc_pid_fd(tracee->pid, fd, path) < 0) {
		return false;
	}
	return 0 == strcmp(path, "/dev/ashmem");
}

static void ashmem_memfd_handle_stat(Extension *extension, Tracee *tracee, int fd, Reg stat_reg) {
	if (is_ashmem_fd(tracee, fd)) {
		AshmemMemfdState *state = talloc_get_type_abort(extension->config, AshmemMemfdState);
		state->chain_state = CS_STAT_ENTERED;
		state->fd = fd;
		state->addr = peek_reg(tracee, CURRENT, stat_reg) + offsetof(struct stat, st_size);
		tracee->restart_how = PTRACE_SYSCALL;
	}
}

static int ashmem_memfd_handle_memfd_create(Extension *extension, Tracee *tracee, bool from_sigsys) {
	AshmemMemfdState *state = talloc_get_type_abort(extension->config, AshmemMemfdState);
	if (!state->memfd_supported) {
		word_t flags = peek_reg(tracee, CURRENT, SYSARG_2);
		set_sysnum(tracee, PR_openat);
		set_sysarg_data(tracee, "/dev/ashmem", 12, SYSARG_2);
		poke_reg(tracee, SYSARG_1, AT_FDCWD);
		poke_reg(tracee, SYSARG_3, O_RDWR | ((flags & MFD_CLOEXEC) ? O_CLOEXEC : 0));
		poke_reg(tracee, SYSARG_4, 0);
		if (from_sigsys) {
			restart_syscall_after_seccomp(tracee);
			/* Skip further processing (such as forcing syscall result) from SIGSYS handler.  */
			return 2;
		}
	}
	return 0;
}

static void ashmem_memfd_handle_syscall(Extension *extension) {
	Tracee *tracee = TRACEE(extension);
	switch (get_sysnum(tracee, CURRENT)) {
	case PR_memfd_create:
	{
		ashmem_memfd_handle_memfd_create(extension, tracee, false);
		break;
	}
	case PR_ftruncate:
	case PR_ftruncate64:
	{
		int fd = peek_reg(tracee, CURRENT, SYSARG_1);
		if (is_ashmem_fd(tracee, fd)) {
			set_sysnum(tracee, PR_ioctl);
			poke_reg(tracee, SYSARG_3, peek_reg(tracee, CURRENT, SYSARG_2));
			poke_reg(tracee, SYSARG_2, ASHMEM_SET_SIZE);
		}
	}
	case PR_fstat:
		ashmem_memfd_handle_stat(extension, tracee, peek_reg(tracee, CURRENT, SYSARG_1), SYSARG_2);
		break;
	case PR_fstatat64:
	{
		int fd = peek_reg(tracee, CURRENT, SYSARG_1);
		if (
				fd >= 0 &&
				/** Is path argument an empty string?  */
				!(peek_word(tracee, peek_reg(tracee, CURRENT, SYSARG_2)) & 0xFF)
		   ) {
			ashmem_memfd_handle_stat(extension, tracee, fd, SYSARG_3);
		}
		break;
	}
	default:
		break;
	}
}

static void ashmem_memfd_handle_stat_exit(Tracee *tracee, AshmemMemfdState *state) {
	if (peek_reg(tracee, CURRENT, SYSARG_RESULT) || peek_word(tracee, state->addr)) {
		state->chain_state = CS_IDLE;
		return;
	}

	register_chained_syscall(tracee, PR_ioctl, state->fd, ASHMEM_GET_SIZE, 0, 0, 0, 0);
	state->chain_state = CS_STAT_CHAINED_IOCTL;
}

int ashmem_memfd_callback(Extension *extension, ExtensionEvent event, intptr_t data1, intptr_t data2)
{
	switch (event) {
	case INITIALIZATION: {
		extension->config = talloc_zero(extension, AshmemMemfdState);
		AshmemMemfdState *state = talloc_get_type_abort(extension->config, AshmemMemfdState);

		memset(state, 0, sizeof(*state));
		state->memfd_supported = detect_memfd_support();

		extension->filtered_sysnums = filtered_sysnums;

		return 0;
	}
	case INHERIT_PARENT: /* Inheritable for sub reconfiguration ...  */
		return 1;

	case INHERIT_CHILD: {
		/* Create configuration in child.  */
		Extension *parent = (Extension *) data1;
		extension->config = talloc_zero(extension, AshmemMemfdState);
		if (extension->config == NULL)
			return -1;

		AshmemMemfdState *old_state = talloc_get_type_abort(parent->config, AshmemMemfdState);
		AshmemMemfdState *state = talloc_get_type_abort(extension->config, AshmemMemfdState);
		state->memfd_supported = old_state->memfd_supported;
	}

	case SYSCALL_ENTER_END:
		ashmem_memfd_handle_syscall(extension);
		return 0;

	case SYSCALL_EXIT_START:
	{
		AshmemMemfdState *state = talloc_get_type_abort(extension->config, AshmemMemfdState);
		switch (state->chain_state) {
			case CS_IDLE:
			case CS_STAT_CHAINED_IOCTL:
				break;
			case CS_STAT_ENTERED:
				ashmem_memfd_handle_stat_exit(TRACEE(extension), state);
				break;
		}
		return 0;
	}
	case SIGSYS_OCC:
	{
		Tracee *tracee = TRACEE(extension);
		if (get_sysnum(tracee, CURRENT) == PR_memfd_create) {
			return ashmem_memfd_handle_memfd_create(extension, tracee, true);
		}
		return 0;
	}
	case SYSCALL_CHAINED_EXIT:
	{
		AshmemMemfdState *state = talloc_get_type_abort(extension->config, AshmemMemfdState);
		if (state->chain_state == CS_STAT_CHAINED_IOCTL) {
			state->chain_state = CS_IDLE;
			Tracee *tracee = TRACEE(extension);
			poke_uint32(tracee, state->addr, peek_reg(tracee, CURRENT, SYSARG_RESULT));
			poke_reg(tracee, SYSARG_RESULT, 0);
		}
		return 0;
	}
	default:
		return 0;
	}
}
