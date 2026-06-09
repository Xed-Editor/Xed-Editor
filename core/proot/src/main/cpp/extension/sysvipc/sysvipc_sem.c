#include "sysvipc_internal.h"

#include "tracee/reg.h"
#include "tracee/mem.h"

#include <sys/errno.h> /* E* */
#include <string.h> /* memset */
#include <assert.h> /* assert */

#define SYSVIPC_MAX_SEMS 512
#define SYSVIPC_MAX_NSEMS 512
#define SYSVIPC_MAX_NSOPS 512
#define SYSVIPC_MAX_SEMVAL 0x7000

int sysvipc_semget(Tracee *tracee, struct SysVIpcConfig *config) {
	word_t semaphore_id = peek_reg(tracee, CURRENT, SYSARG_1);
	int nsems = peek_reg(tracee, CURRENT, SYSARG_2);
	int semflg = peek_reg(tracee, CURRENT, SYSARG_3);

	if (nsems <= 0 || nsems > SYSVIPC_MAX_NSEMS) {
		return -EINVAL;
	}

	struct SysVIpcSemaphore *semaphores = config->ipc_namespace->semaphores;
	size_t unused_slot = 0;
	size_t semaphore_index = 0;
	size_t num_semaphores = talloc_array_length(semaphores);
	bool found_unused_slot = false;
	bool found_semaphore = false;
	for (; semaphore_index < num_semaphores; semaphore_index++) {
		if (semaphores[semaphore_index].valid) {
			if(semaphore_id != IPC_PRIVATE && semaphores[semaphore_index].key == (int32_t) semaphore_id) {
				found_semaphore = true;
				break;
			}
		} else if (!found_unused_slot) {
			unused_slot = semaphore_index;
			found_unused_slot = true;
		}
	}
	struct SysVIpcSemaphore *semaphore = NULL;
	if (!found_semaphore) {
		if (!(semflg & IPC_CREAT)) {
			return -ENOENT;
		}
		if (found_unused_slot) {
			semaphore_index = unused_slot;
		} else {
			if (num_semaphores >= SYSVIPC_MAX_SEMS) {
				return -ENOSPC;
			}
			semaphore_index = num_semaphores;
			config->ipc_namespace->semaphores = semaphores = talloc_realloc(config->ipc_namespace, semaphores, struct SysVIpcSemaphore, num_semaphores + 1);
			memset(&semaphores[semaphore_index], 0, sizeof(semaphores[semaphore_index]));
		}
		semaphore = &semaphores[semaphore_index];
		semaphore->key = semaphore_id;
		semaphore->valid = true;
		semaphore->sems = talloc_array(config->ipc_namespace, uint16_t, nsems);
		memset(semaphore->sems, 0, nsems * sizeof(uint16_t));
		semaphore->nsems = nsems;
	} else {
		if ((semflg & IPC_CREAT) && (semflg & IPC_EXCL)) {
			return -EEXIST;
		}
		semaphore = &semaphores[semaphore_index];
		if (semaphore->nsems < nsems) {
			return -EINVAL;
		}
	}
	return (semaphore_index + 1) | (semaphore->generation << 12);
}

/**
 * Check/execute semops of given tracee
 *
 * Returns 1 if tracee should still wait, otherwise
 * returns result semop syscall shall return
 *
 * If out_wait_type is non-NULL when semaphore waits
 * 'n' or 'z' is written to indicate this semaphore
 * is counted for GETNCNT/GETZCNT.
 */
static int sysvipc_sem_check(struct SysVIpcConfig *config, struct SysVIpcSemaphore *semaphore, char *out_wait_type) {
	assert(config->wait_reason == WR_WAIT_SEMOP);

	size_t nsops = talloc_array_length(config->semop_sops);
	uint16_t new_sems[semaphore->nsems];
	memcpy(new_sems, semaphore->sems, semaphore->nsems * sizeof(uint16_t));

	for (size_t i = 0; i < nsops; i++) {
		int op = config->semop_sops[i].sem_op;
		size_t sem_num = config->semop_sops[i].sem_num;
		if (op == 0) {
			if (new_sems[sem_num]) {
				if (config->semop_sops[i].sem_flg & IPC_NOWAIT) {
					return -EAGAIN;
				}
				if (out_wait_type != NULL) *out_wait_type = 'z';
				return 1;
			}
		} else {
			int new_value = (int)new_sems[sem_num] + op;
			if (new_value < 0) {
				if (config->semop_sops[i].sem_flg & IPC_NOWAIT) {
					return -EAGAIN;
				}
				if (out_wait_type != NULL) *out_wait_type = 'n';
				return 1;
			}
			if (new_value > SYSVIPC_MAX_SEMVAL) {
				return -ERANGE;
			}
			new_sems[sem_num] = new_value;
		}
	}
	memcpy(semaphore->sems, new_sems, semaphore->nsems * sizeof(uint16_t));

	return 0;
}

int sysvipc_semop(Tracee *tracee, struct SysVIpcConfig *config)
{
	/** Lookup semaphore */
	size_t semaphore_index;
	struct SysVIpcSemaphore *semaphore;
	LOOKUP_IPC_OBJECT(semaphore_index, semaphore, config->ipc_namespace->semaphores)

	/** Read and check arguments */
	word_t sops_ptr = peek_reg(tracee, CURRENT, SYSARG_2);
	size_t nsops = peek_reg(tracee, CURRENT, SYSARG_3);

	if (nsops > SYSVIPC_MAX_NSOPS) {
		return -E2BIG;
	}

	if (nsops == 0) {
		return -EINVAL;
	}

	struct SysVIpcSembuf *sops = talloc_array(config, struct SysVIpcSembuf, nsops);
	int status = read_data(tracee, sops, sops_ptr, sizeof(struct SysVIpcSembuf) * nsops);
	if (status < 0) {
		talloc_free(sops);
		return status;
	}

	for (size_t i = 0; i < nsops; i++) {
		if (sops[i].sem_num < 0 || sops[i].sem_num >= semaphore->nsems) {
			talloc_free(sops);
			return -EFBIG;
		}
	}

	config->wait_reason = WR_WAIT_SEMOP;
	config->waiting_object_index = semaphore_index;
	config->semop_sops = sops;
	int this_semop_status = sysvipc_sem_check(config, semaphore, NULL);

	Tracee *other_tracee;
	struct SysVIpcConfig *other_config;
	SYSVIPC_FOREACH_TRACEE(other_tracee, other_config, config->ipc_namespace) {
		if (
				other_config != config &&
				other_config->wait_reason == WR_WAIT_SEMOP &&
				other_config->waiting_object_index == semaphore_index) {
			int other_semop_status = sysvipc_sem_check(other_config, semaphore, NULL);
			if (other_semop_status != 1) {
				TALLOC_FREE(other_config->semop_sops);
				sysvipc_wake_tracee(other_tracee, other_config, other_semop_status);
			}
		}
	}


	if (this_semop_status == 1) {
		assert(config->wait_reason == WR_WAIT_SEMOP);
		return 0;
	} else {
		TALLOC_FREE(config->semop_sops);
		config->wait_reason = WR_NOT_WAITING;
		return this_semop_status;
	}
}

void sysvipc_semop_timedout(Tracee *tracee, struct SysVIpcConfig *config) {
	(void) tracee;

	TALLOC_FREE(config->semop_sops);
	config->wait_reason = WR_NOT_WAITING;
}

int sysvipc_semctl(Tracee *tracee, struct SysVIpcConfig *config) {
	/** Lookup semaphore */
	size_t semaphore_index;
	struct SysVIpcSemaphore *semaphore;
	LOOKUP_IPC_OBJECT(semaphore_index, semaphore, config->ipc_namespace->semaphores)

	int semnum = peek_reg(tracee, CURRENT, SYSARG_2);
	int cmd = peek_reg(tracee, CURRENT, SYSARG_3);
	word_t cmdarg = peek_reg(tracee, CURRENT, SYSARG_4);
	
	switch (cmd & ~SYSVIPC_IPC_64) {
	case SYSVIPC_GETVAL:
	{
		if (semnum < 0 || semnum >= semaphore->nsems) return -EINVAL;
		return semaphore->sems[semnum];
	}
	case SYSVIPC_SETVAL:
	{
		if (cmdarg > SYSVIPC_MAX_SEMVAL) return -ERANGE;
		if (semnum < 0 || semnum >= semaphore->nsems) return -EINVAL;
		semaphore->sems[semnum] = cmdarg;
		return 0;
	}
	case SYSVIPC_GETALL:
	{
		int status = write_data(tracee, cmdarg, semaphore->sems, semaphore->nsems * sizeof(uint16_t));
		if (status < 0) return status;
		return 0;
	}
	case IPC_RMID:
	{
		Tracee *waiting_tracee;
		struct SysVIpcConfig *waiting_config;
		SYSVIPC_FOREACH_TRACEE(waiting_tracee, waiting_config, config->ipc_namespace) {
			if (
					waiting_config->wait_reason == WR_WAIT_SEMOP &&
					waiting_config->waiting_object_index == semaphore_index
			   ) {
				sysvipc_wake_tracee(waiting_tracee, waiting_config, -EIDRM);
			}
		}

		semaphore->valid = false;
		semaphore->generation++;
		TALLOC_FREE(semaphore->sems);
		return 0;
	}
#if 0
	case IPC_STAT:
	{
		int status = write_data(tracee, buf, &queue->stats, sizeof(struct msqid_ds));
		if (status < 0) return status;
		return 0;
	}
#endif
	case SYSVIPC_IPC_INFO:
	case SYSVIPC_SEM_INFO:
	{
		struct SysVIpcSeminfo info = {
		// semmap
		.semmni = SYSVIPC_MAX_SEMS,
		.semmns = SYSVIPC_MAX_SEMS * SYSVIPC_MAX_NSEMS,
		// semmnu
		.semmsl = SYSVIPC_MAX_NSEMS,
		.semopm = SYSVIPC_MAX_NSOPS,
		// semume
		// semusz
		.semvmx = SYSVIPC_MAX_SEMVAL
		// semaem
		};
		if (cmd == SYSVIPC_SEM_INFO) {
			struct SysVIpcSemaphore *semaphores = config->ipc_namespace->semaphores;
			size_t num_semaphores = talloc_array_length(semaphores);
			info.semusz = num_semaphores;
			info.semaem = 0;
			for (size_t i = 0; i < num_semaphores; i++) {
				info.semaem += semaphores[i].nsems;
			}
		}
		int status = write_data(tracee, cmdarg, &info, sizeof(info));
		if (status < 0) return status;
		return 0;
	}
	default:
		return -EINVAL;
	}
}
