#ifndef SYSVIPC_INTERNAL_H
#define SYSVIPC_INTERNAL_H

#include "tracee/tracee.h"
#include "sysvipc_sys.h"

#include <sys/queue.h>
#include <sys/msg.h>
#include <stdint.h>
#include <uchar.h>
#include <stdbool.h>

/******************
 * Message queues *
 *****************/
struct SysVIpcMsgQueueItem {
	long mtype;
	char *mtext;
	size_t mtext_length;
	STAILQ_ENTRY(SysVIpcMsgQueueItem) link;
};
STAILQ_HEAD(SysVIpcMsgQueueItems, SysVIpcMsgQueueItem);

struct SysVIpcMsgQueue {
	int32_t key;
	int16_t generation;
	bool valid;
	struct SysVIpcMsgQueueItems *items;
	struct msqid_ds stats;
};

/**************
 * Semaphores *
 *************/

struct SysVIpcSemaphore {
	int32_t key;
	int16_t generation;
	bool valid;
	uint16_t *sems;
	int nsems;
};

/*****************
 * Shared Memory *
 ****************/

/**
 * Currently mapped region of shared memory
 * (For shmdt and shm_nattch)
 */
struct SysVIpcSharedMemMap {
	word_t addr;

	/**
	 * Size of mmap-ed region or 0 if mmap hasn't been done yet
	 * (but is scheduled in syscall chain)
	 */
	size_t size;

	/**
	 * SysVIpcNamespace containing this shm id
	 *
	 * Note: This reference isn't tracked by talloc,
	 * however as we don't currently implement unshare/clone(CLONE_NEWIPC)
	 * IPC namespace will be kept because process that has mapping
	 * must be in same IPC namespace
	 */
	struct SysVIpcNamespace *ipc_namespace;

	/**
	 * Index of this mapping in SysVIpcNamespace.shms
	 */
	size_t shm_index;

	/**
	 * List pointers for SysVIpcSharedMem.mappings
	 */
	LIST_ENTRY(SysVIpcSharedMemMap) link_shmid;

	/**
	 * List pointers for SysVIpcProcess.mapped_shms
	 */
	LIST_ENTRY(SysVIpcSharedMemMap) link_process;
};
LIST_HEAD(SysVIpcSharedMemMaps, SysVIpcSharedMemMap);

struct SysVIpcSharedMem {
	int32_t key;
	int16_t generation;
	bool valid;
	bool rmid_pending;
	int fd;
	struct SysVIpcShmidDs stats;

	/**
	 * Currently shmat'ed memory for this shm id.
	 *
	 * While there is any map, IPC_RMID cannot be completed
	 * (will cause rmid_pending flag to be set instead)
	 */
	struct SysVIpcSharedMemMaps *mappings;
};

struct SysVIpcNamespace {
	/** Array of Message Queues
	 * Since arrays are 0-indexed and queues are 1-indexed,
	 * queues with id is at queues[id-1] */
	struct SysVIpcMsgQueue *queues;
	struct SysVIpcSemaphore *semaphores;
	struct SysVIpcSharedMem *shms;
};

enum SysVIpcWaitReason {
	WR_NOT_WAITING,
	WR_WAIT_QUEUE_RECV,
	WR_WAIT_SEMOP,
	WR_WAIT_SHMAT_HELPER_BUSY,
};

enum SysVIpcWaitState {
	WSTATE_NOT_WAITING,
	WSTATE_RESTARTED_INTO_PPOLL_CANCELED,
	WSTATE_RESTARTED_INTO_PPOLL,
	WSTATE_ENTERED_PPOLL,
	WSTATE_SIGNALED_PPOLL,
	WSTATE_ENTERED_GETPID,
};

enum SysVIpcChainState {
	CSTATE_NOT_CHAINED,
	CSTATE_SINGLE,
	CSTATE_SHMAT_SOCKET,
	CSTATE_SHMAT_CONNECT,
	CSTATE_SHMAT_RECVMSG,
	CSTATE_SHMAT_MMAP,
	CSTATE_MSGRCV_RETRY,
};

/** Per-process (thread group) structure with state of this extension */
struct SysVIpcProcess {
	int pgid;
	struct SysVIpcSharedMemMaps mapped_shms;
};

/** Per-tracee (thread) structure with state of this extension */
struct SysVIpcConfig {
	struct SysVIpcNamespace *ipc_namespace;
	struct SysVIpcProcess *process;

	/* Reason why this tracee should wait
	 *
	 * When syscall handler requests tracee to wait
	 * (for example because semaphore is blocked)
	 * it sets wait_reason to one of WR_WAIT_*
	 * values
	 *
	 * When tracee has to resume, use sysvipc_wake_tracee
	 * Only sysvipc_wake_tracee function should set
	 * this to WR_NOT_WAITING value  */
	enum SysVIpcWaitReason wait_reason;

	/* Internal state of tracee wait mechanism
	 *
	 * This should only be accessed from
	 * wait mechanism implementation in sysvipc.c,
	 * not syscall handlers in sysvipc_[msg|sem|shm].c  */
	enum SysVIpcWaitState wait_state;

	/* State of syscall chaining inside this extension
	 *
	 * This is used for shmat as it needs to perform
	 * sequence of operations that rely on results
	 * from previous chained syscalls
	 *
	 * When this is set to non-CSTATE_NOT_CHAINED value,
	 * sysvipc_syscall_common won't cancel syscall set
	 * by handler  */
	enum SysVIpcChainState chain_state;

	/* Result of syscall that will be reported
	 * after waiting
	 *
	 * This should be accessed by mechanisms in sysvipc.c
	 * Handlers shouldn't access this, instead they should
	 * return result directly or pass it to sysvipc_wake_tracee  */
	word_t status_after_wait;

	size_t waiting_object_index;

	word_t msgrcv_msgp;
	size_t msgrcv_msgsz;
	int msgrcv_msgtyp;
	int msgrcv_msgflg;

	struct SysVIpcSembuf *semop_sops;

	word_t shmat_guest_buf;
	int shmat_socket_fd;
	int shmat_mem_fd;
};

/**
 * Find given IPC object requested by tracee
 *
 * out_index should be size_t
 * out_object should be pointer to struct SysVIpc[MsgQueue]
 */
#define LOOKUP_IPC_OBJECT(out_index, out_object, objects_array) \
{ \
	int object_id = peek_reg(tracee, CURRENT, SYSARG_1); \
	int object_index = object_id & 0xFFF; \
	if (object_index <= 0 || object_index > (int)talloc_array_length(objects_array)) { \
		return -EINVAL; \
	} \
	out_index = object_index - 1; \
	out_object = &(objects_array)[object_index - 1]; \
	if (!out_object->valid || out_object->generation != ((object_id >> 12) & 0xFFFF)) { \
		return -EINVAL; \
	} \
}

#define IPC_OBJECT_ID(index, object) \
	((index + 1) | (object->generation << 12))

/**
 * Iterate over all Tracees in given SysVIpc namespace
 *
 * out_tracee should be 'Tracee *'
 * out_config should be 'struct SysVIpcConfig *'
 * checked_namespace is SysVIpc namespace to find tracees in
 */
#define SYSVIPC_FOREACH_TRACEE(out_tracee, out_config, checked_namespace) \
	LIST_FOREACH((out_tracee), get_tracees_list_head(), link) \
	if ( \
		((out_config) = sysvipc_get_config(out_tracee)) != NULL && \
		(out_config)->ipc_namespace == (checked_namespace) \
	)

#define SYSVIPC_FOREACH_TRACEE_ANY_NAMESPACE(out_tracee, out_config) \
	LIST_FOREACH((out_tracee), get_tracees_list_head(), link) \
	if ( \
		((out_config) = sysvipc_get_config(out_tracee)) != NULL \
	)

void sysvipc_wake_tracee(Tracee *tracee, struct SysVIpcConfig *config, int status);
struct SysVIpcConfig *sysvipc_get_config(Tracee *tracee);

int sysvipc_msgget(Tracee *tracee, struct SysVIpcConfig *config);
int sysvipc_msgsnd(Tracee *tracee, struct SysVIpcConfig *config);
int sysvipc_msgrcv(Tracee *tracee, struct SysVIpcConfig *config);
int sysvipc_msgrcv_retry(Tracee *tracee, struct SysVIpcConfig *config);
int sysvipc_msgctl(Tracee *tracee, struct SysVIpcConfig *config);

int sysvipc_semget(Tracee *tracee, struct SysVIpcConfig *config);
int sysvipc_semop(Tracee *tracee, struct SysVIpcConfig *config);
void sysvipc_semop_timedout(Tracee *tracee, struct SysVIpcConfig *config);
int sysvipc_semctl(Tracee *tracee, struct SysVIpcConfig *config);

int sysvipc_shmget(Tracee *tracee, struct SysVIpcConfig *config);
int sysvipc_shmat(Tracee *tracee, struct SysVIpcConfig *config);
int sysvipc_shmat_chain(Tracee *tracee, struct SysVIpcConfig *config);
int sysvipc_shmdt(Tracee *tracee, struct SysVIpcConfig *config);
int sysvipc_shmctl(Tracee *tracee, struct SysVIpcConfig *config);
void sysvipc_shm_inherit_process(struct SysVIpcProcess *parent, struct SysVIpcProcess *child);
void sysvipc_shm_remove_mappings_from_process(struct SysVIpcProcess *process);
void sysvipc_shm_fill_proc(FILE *proc_file, struct SysVIpcNamespace *ipc_namespace);
int sysvipc_shm_namespace_destructor(struct SysVIpcNamespace *ipc_namespace);

#endif // SYSVIPC_INTERNAL_H

