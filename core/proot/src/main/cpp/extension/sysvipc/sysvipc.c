#include "extension/sysvipc/sysvipc.h"
#include "tracee/seccomp.h"
#include "syscall/chain.h"
#include "path/path.h"
#include "path/temp.h"

#include <assert.h> /* assert */
#include <unistd.h> /* syscall */
#include <sys/syscall.h> /* __NR_tkill */
#include <errno.h> /* E* */
#include <sched.h> /* CLONE_THREAD */
#include <string.h> /* strcmp */
#include <signal.h> /* SIGSTOP */


#include "sysvipc_internal.h"


static FilteredSysnum filtered_sysnums[] = {
	{ PR_msgget,		0 },
	{ PR_msgsnd,		0 },
	{ PR_msgrcv,		0 },
	{ PR_msgctl,		0 },
	{ PR_semget,		0 },
	{ PR_semop,		0 },
	{ PR_semtimedop,	0 },
	{ PR_semctl,		0 },
	{ PR_shmget,		0 },
	{ PR_shmat,		0 },
	{ PR_shmdt,		0 },
	{ PR_shmctl,		0 },
	FILTERED_SYSNUM_END,
};


static int sysvipc_syscall_common(Tracee *tracee, struct SysVIpcConfig *config, bool from_sigsys) {
	int status = 0;
	word_t timeout = 0;

	assert(config->wait_state == WSTATE_NOT_WAITING);

	word_t sysnum = get_sysnum(tracee, CURRENT);
	switch (sysnum) {
	case PR_msgget:
		status = sysvipc_msgget(tracee, config);
		break;
	case PR_msgsnd:
		status = sysvipc_msgsnd(tracee, config);
		break;
	case PR_msgrcv:
		status = sysvipc_msgrcv(tracee, config);
		break;
	case PR_msgctl:
		status = sysvipc_msgctl(tracee, config);
		break;
	case PR_semget:
		status = sysvipc_semget(tracee, config);
		break;
	case PR_semtimedop:
		timeout = peek_reg(tracee, CURRENT, SYSARG_4);
		// Fall-throug
	case PR_semop:
		status = sysvipc_semop(tracee, config);
		break;
	case PR_semctl:
		status = sysvipc_semctl(tracee, config);
		break;
	case PR_shmget:
		status = sysvipc_shmget(tracee, config);
		break;
	case PR_shmat:
		status = sysvipc_shmat(tracee, config);
		break;
	case PR_shmdt:
		status = sysvipc_shmdt(tracee, config);
		break;
	case PR_shmctl:
		status = sysvipc_shmctl(tracee, config);
		break;
	default:
		return 0;
	}

	if (config->chain_state != CSTATE_NOT_CHAINED) {
		/* Check if chain_state is one of initial ones
		 * (others not go through SYSCALL_ENTER_START).  */
		assert(
				config->chain_state == CSTATE_SINGLE ||
				config->chain_state == CSTATE_SHMAT_SOCKET
		);
		if (config->chain_state == CSTATE_SINGLE) {
			config->chain_state = CSTATE_NOT_CHAINED;
		}
		tracee->restart_how = PTRACE_SYSCALL;
		if (from_sigsys) {
			restart_syscall_after_seccomp(tracee);
			return 2;
		} else {
			return 1;
		}
	} else if (config->wait_reason != WR_NOT_WAITING) {
		poke_reg(tracee, SYSARG_1, 0);
		poke_reg(tracee, SYSARG_2, 0);
		poke_reg(tracee, SYSARG_3, timeout);
		poke_reg(tracee, SYSARG_4, 0);
		set_sysnum(tracee, PR_ppoll);
		tracee->restart_how = PTRACE_SYSCALL;
		if (from_sigsys) {
			config->wait_state = WSTATE_RESTARTED_INTO_PPOLL;
			restart_syscall_after_seccomp(tracee);
			return 2;
		} else {
			config->wait_state = WSTATE_ENTERED_PPOLL;
			return 1;
		}
	} else {
		if (from_sigsys) {
			set_result_after_seccomp(tracee, status);
			return 2;
		} else {
			config->status_after_wait = status;
			config->wait_state = WSTATE_ENTERED_GETPID;
			set_sysnum(tracee, PR_getpid);
			tracee->restart_how = PTRACE_SYSCALL;
			return 1;
		}
	}
}

static int sysvipc_proc_handler(
		char *out_path,
		Extension *extension,
		void (*handler)(FILE *proc_file, struct SysVIpcNamespace *ipc_namespace)
		) {
	Tracee *tracee = TRACEE(extension);
	struct SysVIpcConfig *config = extension->config;

	const char *path = create_temp_file(tracee->ctx, "prootseq");
	if (path == NULL) {
		return -ENOMEM;
	}

	FILE *fp = fopen(path, "w");
	if (fp == NULL) {
		return -ENOMEM;
	}
	handler(fp, config->ipc_namespace);
	fclose(fp);

	strncpy(out_path, path, PATH_MAX);
	return 1;
}

/**
 * Handler for this @extension.  It is triggered each time an @event
 * occurred.  See ExtensionEvent for the meaning of @data1 and @data2.
 */
int sysvipc_callback(Extension *extension, ExtensionEvent event, intptr_t data1, intptr_t data2)
{
	switch (event) {
	case INITIALIZATION:
	{
		Tracee *tracee = TRACEE(extension);
		struct SysVIpcConfig *config = talloc_zero(extension, struct SysVIpcConfig);
		config->ipc_namespace = talloc_zero(config, struct SysVIpcNamespace);
		talloc_set_destructor(config->ipc_namespace, sysvipc_shm_namespace_destructor);
		config->process = talloc_zero(config, struct SysVIpcProcess);
		config->process->pgid = tracee->pid;
		extension->config = config;
		extension->filtered_sysnums = filtered_sysnums;
		return 0;
	}

	case INHERIT_PARENT: /* Inheritable for sub reconfiguration ...  */
		return 1;

	case INHERIT_CHILD: {
		Extension *parent = (Extension *) data1;
		struct SysVIpcConfig *parent_config = parent->config;
		struct SysVIpcConfig *child_config = talloc_zero(extension, struct SysVIpcConfig);
		if (child_config == NULL)
			return -1;

		if (data2 & CLONE_THREAD) {
			child_config->process = talloc_reference(child_config, parent_config->process);
		} else {
			Tracee *tracee = TRACEE(extension);
			child_config->process = talloc_zero(child_config, struct SysVIpcProcess);
			child_config->process->pgid = tracee->pid;
			sysvipc_shm_inherit_process(parent_config->process, child_config->process);
		}

		child_config->ipc_namespace = talloc_reference(child_config, parent_config->ipc_namespace);
		extension->config = child_config;

		return 0;
	}

	case SYSCALL_ENTER_END:
		/* If we've just finished execve remove mapped shms from this process  */
		if (data1 == 0) {
			Tracee *tracee = TRACEE(extension);
			if (get_sysnum(tracee, CURRENT) == PR_execve) {
				struct SysVIpcConfig *config = extension->config;
				sysvipc_shm_remove_mappings_from_process(config->process);
			}
		}
		return 0;

	case SYSCALL_ENTER_START:
	{
		Tracee *tracee = TRACEE(extension);
		struct SysVIpcConfig *config = extension->config;
		switch (config->wait_state) {
		case WSTATE_NOT_WAITING:
			return sysvipc_syscall_common(tracee, config, false);
		case WSTATE_RESTARTED_INTO_PPOLL:
			assert(get_sysnum(tracee, CURRENT) == PR_ppoll);
			config->wait_state = WSTATE_ENTERED_PPOLL;
			tracee->restart_how = PTRACE_SYSCALL;
			return 1;
		case WSTATE_RESTARTED_INTO_PPOLL_CANCELED:
		{
			int status = config->status_after_wait;
			if (config->chain_state == CSTATE_MSGRCV_RETRY) {
				status = sysvipc_msgrcv_retry(tracee, config);
			}
			poke_reg(tracee, SYSARG_RESULT, status);
			set_sysnum(tracee, PR_void);
			config->wait_state = WSTATE_NOT_WAITING;
			return 1;
		}
		default:
			assert(!"Bad wait_state on SYSCALL_ENTER_START");
		}
	}

	case SIGSYS_OCC:
	{
		Tracee *tracee = TRACEE(extension);
		struct SysVIpcConfig *config = extension->config;
		return sysvipc_syscall_common(tracee, config, true);
	}

	case SYSCALL_EXIT_START:
	{
		Tracee *tracee = TRACEE(extension);
		struct SysVIpcConfig *config = extension->config;
		if (config->chain_state >= CSTATE_SHMAT_SOCKET && config->chain_state <= CSTATE_SHMAT_MMAP) {
			assert(config->chain_state == CSTATE_SHMAT_SOCKET);
			return sysvipc_shmat_chain(tracee, config);
		}
		switch (config->wait_state) {
		case WSTATE_NOT_WAITING:
			return 0;
		case WSTATE_ENTERED_PPOLL:
			assert(config->wait_state != WSTATE_NOT_WAITING);
			config->wait_state = WSTATE_NOT_WAITING;
			switch (config->wait_reason) {
			case WR_NOT_WAITING:
				assert(!"Unexpected wait_reason=WR_NOT_WAITING in SYSCALL_EXIT_START/WSTATE_ENTERED_PPOLL");
			case WR_WAIT_SEMOP:
				sysvipc_semop_timedout(tracee, config);
				break;
			default:
				config->wait_reason = WR_NOT_WAITING;
			}
			assert(config->wait_reason == WR_NOT_WAITING);
			int ppoll_status = (int) peek_reg(tracee, CURRENT, SYSARG_RESULT);
			if (ppoll_status == -EFAULT || ppoll_status == -EINTR) {
				return 1;
			}
			return -EINTR;
		case WSTATE_SIGNALED_PPOLL:
		case WSTATE_ENTERED_GETPID:
		{
			assert(config->wait_reason == WR_NOT_WAITING);
			config->wait_state = WSTATE_NOT_WAITING;
			int status = config->status_after_wait;
			if (config->chain_state == CSTATE_MSGRCV_RETRY) {
				status = sysvipc_msgrcv_retry(tracee, config);
			}
			poke_reg(tracee, SYSARG_RESULT, status);
			return 1;
		}
		default:
			assert(!"Bad wait_state on SYSCALL_EXIT_START");
		}
	}

	case SYSCALL_CHAINED_ENTER:
	{
		struct SysVIpcConfig *config = extension->config;
		switch (config->wait_state) {
		case WSTATE_NOT_WAITING:
			break;
		case WSTATE_RESTARTED_INTO_PPOLL_CANCELED:
		{
			Tracee *tracee = TRACEE(extension);
			poke_reg(tracee, SYSARG_3, 1);
			config->wait_state = WSTATE_SIGNALED_PPOLL;
			break;
		}
		default:
			assert(!"Bad wait_state on SYSCALL_CHAINED_ENTER");
		}
		return 0;
	}

	case SYSCALL_CHAINED_EXIT:
	{
		Tracee *tracee = TRACEE(extension);
		struct SysVIpcConfig *config = extension->config;
		switch (config->wait_state) {
		case WSTATE_NOT_WAITING:
			break;
		case WSTATE_SIGNALED_PPOLL:
			config->wait_state = WSTATE_NOT_WAITING;
			/* Don't run chain handlers, return instead of break */
			return 0;
		default:
			assert(!"Bad wait_state on SYSCALL_CHAINED_EXIT");
		}
		if (config->chain_state >= CSTATE_SHMAT_SOCKET && config->chain_state <= CSTATE_SHMAT_MMAP) {
			sysvipc_shmat_chain(tracee, config);
		}
		return 0;
	}

	case GUEST_PATH:
	{
		if (strcmp((const char *) data2, "/proc/sysvipc/shm") == 0) {
			return sysvipc_proc_handler((char *) data1, extension, sysvipc_shm_fill_proc);
		}
		return 0;
	}

	default:
		return 0;
	}
}

struct SysVIpcConfig *sysvipc_get_config(Tracee *tracee)
{
	Extension *extension = get_extension(tracee, sysvipc_callback);
	if (extension == NULL)
		return NULL;
	return talloc_get_type_abort(extension->config, struct SysVIpcConfig);
}

void sysvipc_wake_tracee(Tracee *tracee, struct SysVIpcConfig *config, int status)
{
	assert(config->wait_reason != WR_NOT_WAITING);
	config->wait_reason = WR_NOT_WAITING;
	config->status_after_wait = status;
	if (config->wait_state == WSTATE_ENTERED_PPOLL) {
		config->wait_state = WSTATE_SIGNALED_PPOLL;
		syscall(__NR_tkill, tracee->pid, SIGSTOP);
		tracee->sigstop = SIGSTOP_IGNORED;
	} else if (config->wait_state == WSTATE_RESTARTED_INTO_PPOLL) {
		config->wait_state = WSTATE_RESTARTED_INTO_PPOLL_CANCELED;
	} else {
		assert(!"Bad wait_state in sysvipc_wake_tracee");
	}
}
