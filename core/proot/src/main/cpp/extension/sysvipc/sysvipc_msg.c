#include "sysvipc_internal.h"

#include "tracee/reg.h"
#include "tracee/mem.h"
#include "tracee/tracee.h"
#include "extension/extension.h"

#include <sys/errno.h> /* E* */
#include <sys/msg.h> /* IPC_PRIVATE */
#include <syscall.h> /* syscall() */
#include <string.h> /* memset */
#include <time.h> /* time */
#include <assert.h> /* assert */

#define SYSVIPC_MAX_MSG_SIZE 0xFFFF

int sysvipc_msgget(Tracee *tracee, struct SysVIpcConfig *config)
{
	word_t queue_id = peek_reg(tracee, CURRENT, SYSARG_1);
	word_t msgflg = peek_reg(tracee, CURRENT, SYSARG_2);
	struct SysVIpcMsgQueue *queues = config->ipc_namespace->queues;
	size_t unused_slot = 0;
	size_t queue_index = 0;
	size_t num_queues = talloc_array_length(queues);
	bool found_unused_slot = false;
	bool found_queue = false;
	for (; queue_index < num_queues; queue_index++) {
		if (queues[queue_index].valid) {
			if(queue_id != IPC_PRIVATE && queues[queue_index].key == (int32_t) queue_id) {
				found_queue = true;
				break;
			}
		} else if (!found_unused_slot) {
			unused_slot = queue_index;
			found_unused_slot = true;
		}
	}
	struct SysVIpcMsgQueue *queue = NULL;
	if (!found_queue) {
		if (!(msgflg & IPC_CREAT)) {
			return -ENOENT;
		}
		if (found_unused_slot) {
			queue_index = unused_slot;
		} else {
			queue_index = num_queues;
			config->ipc_namespace->queues = queues = talloc_realloc(config->ipc_namespace, queues, struct SysVIpcMsgQueue, num_queues + 1);
			memset(&queues[queue_index], 0, sizeof(queues[queue_index]));
		}
		queue = &queues[queue_index];
		queue->key = queue_id;
		queue->valid = true;
		queue->items = talloc(config->ipc_namespace->queues, struct SysVIpcMsgQueueItems);
		STAILQ_INIT(queue->items);
		memset(&queue->stats, 0, sizeof(queue->stats));
		queue->stats.msg_qbytes = 1024 * 64; // Not enforced limit
	} else {
		if ((msgflg & IPC_CREAT) && (msgflg & IPC_EXCL)) {
			return -EEXIST;
		}
		queue = &queues[queue_index];
	}
	return (queue_index + 1) | (queue->generation << 12);
}

static bool sysvipc_msg_match(int sender_type, int receiver_filter, int receiver_flag)
{
	bool matched =
		receiver_filter == 0 ||
		sender_type == receiver_filter ||
		(receiver_filter < 0 && sender_type <= -receiver_filter);

	if (receiver_flag & MSG_EXCEPT) {
		matched = !matched;
	}

	return matched;
}

static int sysvipc_msg_deliver(Tracee *recipent_tracee, struct SysVIpcConfig *recipent_config, struct SysVIpcMsgQueue *queue, struct SysVIpcMsgQueueItem *msg, time_t delivery_time)
{
	size_t msgsz = recipent_config->msgrcv_msgsz;
	if (msg->mtext_length > msgsz) {
		if (!(recipent_config->msgrcv_msgflg & MSG_NOERROR)) {
			return -E2BIG;
		}
	} else {
		msgsz = msg->mtext_length;
	}

	int status = write_data(recipent_tracee, recipent_config->msgrcv_msgp, &msg->mtype, sizeof(long));
	if (status < 0) return status;
	status = write_data(recipent_tracee, recipent_config->msgrcv_msgp + sizeof(long), msg->mtext, msgsz);
	if (status < 0) return status;
	queue->stats.msg_lrpid = recipent_tracee->pid;
	queue->stats.msg_rtime = delivery_time;
	return msgsz;
}

int sysvipc_msgsnd(Tracee *tracee, struct SysVIpcConfig *config) {
	/** Lookup queue */
	size_t queue_index;
	struct SysVIpcMsgQueue *queue;
	LOOKUP_IPC_OBJECT(queue_index, queue, config->ipc_namespace->queues)

	/** Read and check arguments */
	word_t msgp = peek_reg(tracee, CURRENT, SYSARG_2);
	size_t msgsz = peek_reg(tracee, CURRENT, SYSARG_3);
	//int msgflg = peek_reg(tracee, CURRENT, SYSARG_4);
	if (msgsz > SYSVIPC_MAX_MSG_SIZE) {
		return -EINVAL;
	}
	long mtype = 0;
	{
		int status = read_data(tracee, &mtype, msgp, sizeof(long));
		if (status < 0) {
			return status;
		}
	}
	if (mtype < 1) {
		return -EINVAL;
	}

	/** Create queue item */
	struct SysVIpcMsgQueueItem *item = talloc_zero(queue->items, struct SysVIpcMsgQueueItem);
	item->mtype = mtype;
	item->mtext_length = msgsz;
	item->mtext = talloc_array(item, char, msgsz);
	{
		int status = read_data(tracee, item->mtext, msgp + sizeof(long), msgsz);
		if (status < 0) {
			talloc_free(item);
			return status;
		}
	}

	/** Update stats */
	time_t current_time = 0;
	time(&current_time);
	queue->stats.msg_lspid = tracee->pid;
	queue->stats.msg_stime = current_time;

	/** Deliver to waiting msgrcv */
	Tracee *receiver_tracee;
	struct SysVIpcConfig *receiver_config;
	SYSVIPC_FOREACH_TRACEE(receiver_tracee, receiver_config, config->ipc_namespace) {
		if (
				receiver_config->wait_reason == WR_WAIT_QUEUE_RECV &&
				receiver_config->waiting_object_index == queue_index &&
				sysvipc_msg_match(
					item->mtype,
					receiver_config->msgrcv_msgtyp,
					receiver_config->msgrcv_msgflg
				)
		) {
			receiver_config->chain_state = CSTATE_MSGRCV_RETRY;
			sysvipc_wake_tracee(
				receiver_tracee,
				receiver_config,
				-EAGAIN
			);
			break;
		}
	}

	STAILQ_INSERT_TAIL(queue->items, item, link);
	queue->stats.msg_qnum += 1;
	queue->stats.msg_cbytes += item->mtext_length;

	return 0;
}

static int sysvipc_do_msgrcv(Tracee *tracee, struct SysVIpcConfig *config, size_t queue_index, struct SysVIpcMsgQueue *queue) {
	if ((int) config->msgrcv_msgsz < 0) {
		return -EINVAL;
	}

	if ((config->msgrcv_msgflg & ~(IPC_NOWAIT | MSG_NOERROR | MSG_COPY | MSG_EXCEPT)) != 0) {
		return -EINVAL;
	}

	bool copy = (config->msgrcv_msgflg & MSG_COPY) != 0;
	if (copy) {
		if ((config->msgrcv_msgflg & IPC_NOWAIT) == 0) {
			return -EINVAL;
		}
		if ((config->msgrcv_msgflg & MSG_EXCEPT) != 0) {
			return -EINVAL;
		}
	}

	struct SysVIpcMsgQueueItem *found_item = NULL;
	struct SysVIpcMsgQueueItem *candidate_item;
	if (copy) {
		int index = 0;
		STAILQ_FOREACH(candidate_item, queue->items, link) {
			if (index == config->msgrcv_msgtyp) {
				found_item = candidate_item;
				break;
			}
			index += 1;
		}
	} else {
		STAILQ_FOREACH(candidate_item, queue->items, link) {
			if (sysvipc_msg_match(candidate_item->mtype, config->msgrcv_msgtyp, config->msgrcv_msgflg)) {
				found_item = candidate_item;
				break;
			}
		}
	}

	if (found_item == NULL) {
		if (config->msgrcv_msgflg & IPC_NOWAIT) {
			return -ENOMSG;
		} else {
			config->wait_reason = WR_WAIT_QUEUE_RECV;
			config->waiting_object_index = queue_index;
			return 0;
		}
	}

	time_t current_time = 0;
	time(&current_time);

	int status = sysvipc_msg_deliver(tracee, config, queue, found_item, current_time);

	if (status >= 0 && !copy) {
		queue->stats.msg_qnum -= 1;
		queue->stats.msg_cbytes -= found_item->mtext_length;
		STAILQ_REMOVE(queue->items, found_item, SysVIpcMsgQueueItem, link);
		talloc_free(found_item);
	}

	return status;
}

int sysvipc_msgrcv(Tracee *tracee, struct SysVIpcConfig *config) {
	size_t queue_index;
	struct SysVIpcMsgQueue *queue;
	LOOKUP_IPC_OBJECT(queue_index, queue, config->ipc_namespace->queues)

	config->msgrcv_msgp = peek_reg(tracee, CURRENT, SYSARG_2);
	config->msgrcv_msgsz = peek_reg(tracee, CURRENT, SYSARG_3);
	config->msgrcv_msgtyp = peek_reg(tracee, CURRENT, SYSARG_4);
	config->msgrcv_msgflg = peek_reg(tracee, CURRENT, SYSARG_5);

	return sysvipc_do_msgrcv(tracee, config, queue_index, queue);
}

int sysvipc_msgrcv_retry(Tracee *tracee, struct SysVIpcConfig *config) {
	assert(config->chain_state == CSTATE_MSGRCV_RETRY);

	int status = config->status_after_wait;

	if (status == -EAGAIN) {
		size_t queue_index = config->waiting_object_index;
		assert(queue_index < talloc_array_length(config->ipc_namespace->queues));
		struct SysVIpcMsgQueue *queue = &config->ipc_namespace->queues[queue_index];
		assert(queue->valid);
		status = sysvipc_do_msgrcv(tracee, config, queue_index, queue);

		/* Retry handler requested wait? This is uncommon path
		 * (but can happen due to e.g. concurrent msgrcv consuming message),
		 * do a spurious wakeup */
		if (config->wait_reason != WR_NOT_WAITING) {
			status = -EINTR;
			config->wait_reason = WR_NOT_WAITING;
		}
	}

	config->chain_state = CSTATE_NOT_CHAINED;

	return status;
}

int sysvipc_msgctl(Tracee *tracee, struct SysVIpcConfig *config) {
	size_t queue_index;
	struct SysVIpcMsgQueue *queue;
	LOOKUP_IPC_OBJECT(queue_index, queue, config->ipc_namespace->queues)

	int cmd = peek_reg(tracee, CURRENT, SYSARG_2);
	word_t buf = peek_reg(tracee, CURRENT, SYSARG_3);
	
	switch (cmd) {
	case IPC_RMID:
	case IPC_RMID | SYSVIPC_IPC_64:
	{
		Tracee *waiting_tracee;
		struct SysVIpcConfig *waiting_config;
		SYSVIPC_FOREACH_TRACEE(waiting_tracee, waiting_config, config->ipc_namespace) {
			if (
					waiting_config->wait_reason == WR_WAIT_QUEUE_RECV &&
					waiting_config->waiting_object_index == queue_index
			   ) {
				sysvipc_wake_tracee(waiting_tracee, waiting_config, -EIDRM);
			}
		}

		queue->valid = false;
		queue->generation++;
		TALLOC_FREE(queue->items);
		return 0;
	}
	case IPC_STAT:
	case IPC_STAT | SYSVIPC_IPC_64:
	{
		int status = write_data(tracee, buf, &queue->stats, sizeof(struct msqid_ds));
		if (status < 0) return status;
		return 0;
	}
	default:
		return -EINVAL;
	}
}
