#include "sysvipc.h"
#include "sysvipc_internal.h"

#include "tracee/reg.h"
#include "tracee/mem.h"
#include "tracee/tracee.h"
#include "extension/extension.h"
#include "path/temp.h"
#include "syscall/chain.h"

#include <sys/socket.h>
#include <sys/un.h>

#include <unistd.h>
#include <assert.h> /* assert */
#include <sys/errno.h> /* E* */
#include <sys/stat.h> /* S_IR */
#include <sys/mman.h> /* MAP_SHARED */
#include <syscall.h> /* syscall() */
#include <string.h> /* memset */
#include <time.h> /* time */
#include <fcntl.h> /* open, fcntl */

#ifdef __ANDROID__
#include <linux/ashmem.h> /* ASHMEM_* */
#else
#include <unistd.h> /* ftruncate */
#endif


#define SYSVIPC_MAX_SHM_SIZE 100 * 4096

struct SysVIpcRecvMsgPointers {
	word_t msghdr_ptr;
	word_t cmsg_controllen_ptr;
	word_t cmsg_control_ptr;
};

int sysvipc_shm_recvmsg_pointers(Tracee *tracee, struct SysVIpcRecvMsgPointers *out_pointers, word_t guest_buf, bool do_write)
{
#if defined(ARCH_X86_64) || defined(ARCH_ARM64)
	bool is32 = is_32on64_mode(tracee);
#else
	bool is32 = true;
#endif

	/* Calculate addresses of given fields in guest userspace.  */
	word_t ptr_len = is32 ? 4 : 8;
	word_t buf_end = guest_buf + sizeof(struct sockaddr_un);

	word_t data_addr = buf_end - 4 ;

	word_t data_iov_length = data_addr - ptr_len;
	word_t data_iov_addr = data_iov_length - ptr_len;

	word_t msghdr_flags = data_iov_addr - ptr_len;
	word_t msghdr_controllen = msghdr_flags - ptr_len;
	word_t msghdr_control = msghdr_controllen - ptr_len;
	word_t msghdr_iovlen = msghdr_control - ptr_len;
	word_t msghdr_iov = msghdr_iovlen - ptr_len;
	word_t msghdr = msghdr_iov - ptr_len * 2; // name & namelen unused
	// control data is at guest_buf

	if (do_write) {
		char data[sizeof(struct sockaddr_un)] = {};

		if (is32) {
			*(uint32_t*) &data[data_iov_addr - guest_buf] = data_addr;
			*(uint32_t*) &data[data_iov_length - guest_buf] = 1;
			*(uint32_t*) &data[msghdr_iov - guest_buf] = data_iov_addr;
			*(uint32_t*) &data[msghdr_iovlen - guest_buf] = 1;
			*(uint32_t*) &data[msghdr_control - guest_buf] = guest_buf;
			*(uint32_t*) &data[msghdr_controllen - guest_buf] = 20; // sizeof(cmsghdr) + sizeof(uint64_t)
		} else {
			*(uint64_t*) &data[data_iov_addr - guest_buf] = data_addr;
			*(uint64_t*) &data[data_iov_length - guest_buf] = 1;
			*(uint64_t*) &data[msghdr_iov - guest_buf] = data_iov_addr;
			*(uint64_t*) &data[msghdr_iovlen - guest_buf] = 1;
			*(uint64_t*) &data[msghdr_control - guest_buf] = guest_buf;
			*(uint64_t*) &data[msghdr_controllen - guest_buf] = 20; // sizeof(cmsghdr) + sizeof(uint64_t)
		}

		int status = write_data(tracee, guest_buf, data, sizeof(data));
		if (status < 0) return status;
	}

	out_pointers->msghdr_ptr = msghdr;
	out_pointers->cmsg_controllen_ptr = msghdr_controllen;
	out_pointers->cmsg_control_ptr = guest_buf;
	return 0;
}

enum SysVIpcShmHelperRequestOp {
	SHMHELPER_DISTRIBUTE,
	SHMHELPER_ALLOC,
	SHMHELPER_FREE,
};
struct SysVIpcShmHelperRequest {
	enum SysVIpcShmHelperRequestOp op;
	int fd;
	size_t size;
};
#define SYSVIPC_SHMHELPER_SOCKET_LEN 108

static struct sockaddr_un sysvipc_shm_helper_addr;
static int sysvipc_shm_send_helper_request(struct SysVIpcShmHelperRequest *request)
{
	static bool launched_helper;
	static int proot2helper;
	static int helper2proot;

	if (!launched_helper) {
		int pipe_proot2helper[2];
		int pipe_helper2proot[2];
		if (pipe2(pipe_proot2helper, O_CLOEXEC) < 0) {
			return -1;
		}
		if (pipe2(pipe_helper2proot, O_CLOEXEC) < 0) {
			close(pipe_proot2helper[0]);
			close(pipe_proot2helper[1]);
			return -1;
		}
		pid_t forked = fork();
		if (forked == 0) {
			close(pipe_proot2helper[1]);
			close(pipe_helper2proot[0]);
			dup2(pipe_proot2helper[0], 0);
			dup2(pipe_helper2proot[1], 1);
			close(pipe_proot2helper[0]);
			close(pipe_helper2proot[1]);
			fcntl(0, F_SETFL, 0);
			fcntl(1, F_SETFL, 0);

			/* Fork again to detach from proot waitpid() */
			forked = fork();
			if (forked == 0) {
				execl("/proc/self/exe", "proot", "--shm-helper", NULL);
				perror("proot-shm-helper: execl");
				_exit(1);
			} else {
				if (forked < 0) {
					perror("proot-shm-helper: second fork");
				}
				_exit(0);
			}
		} else if (forked < 0) {
			perror("proot-shm-helper: first fork");
			close(pipe_proot2helper[0]);
			close(pipe_proot2helper[1]);
			close(pipe_helper2proot[0]);
			close(pipe_helper2proot[1]);
			return -1;
		} else {
			close(pipe_proot2helper[0]);
			close(pipe_helper2proot[1]);
			int nread = read(pipe_helper2proot[0], sysvipc_shm_helper_addr.sun_path, SYSVIPC_SHMHELPER_SOCKET_LEN);
			if (nread != SYSVIPC_SHMHELPER_SOCKET_LEN) {
				close(pipe_proot2helper[1]);
				close(pipe_helper2proot[0]);
				return -1;
			}
			sysvipc_shm_helper_addr.sun_family = AF_UNIX;
			launched_helper = true;
			proot2helper = pipe_proot2helper[1];
			helper2proot = pipe_helper2proot[0];
		}
	}

	write(proot2helper, request, sizeof(*request));
	if (request->op == SHMHELPER_ALLOC) {
		int fd = -1;
		read(helper2proot, &fd, sizeof(fd));
		return fd;
	}
	return 0;
}

int sysvipc_shmget(Tracee *tracee, struct SysVIpcConfig *config)
{
	word_t shm_key = peek_reg(tracee, CURRENT, SYSARG_1);
	size_t shm_size = peek_reg(tracee, CURRENT, SYSARG_2);
	word_t shmflg = peek_reg(tracee, CURRENT, SYSARG_3);
	struct SysVIpcSharedMem *shms = config->ipc_namespace->shms;
	size_t unused_slot = 0;
	size_t shm_index = 0;
	size_t num_shms = talloc_array_length(shms);
	bool found_unused_slot = false;
	bool found_queue = false;
	for (; shm_index < num_shms; shm_index++) {
		if (shms[shm_index].valid) {
			if(shm_key != IPC_PRIVATE && shms[shm_index].key == (int32_t) shm_key) {
				found_queue = true;
				break;
			}
		} else if (!found_unused_slot) {
			unused_slot = shm_index;
			found_unused_slot = true;
		}
	}
	struct SysVIpcSharedMem *shm = NULL;
	if (!found_queue) {
		if (!(shmflg & IPC_CREAT)) {
			return -ENOENT;
		}
		if (found_unused_slot) {
			shm_index = unused_slot;
		} else {
			shm_index = num_shms;
			config->ipc_namespace->shms = shms = talloc_realloc(config->ipc_namespace, shms, struct SysVIpcSharedMem, num_shms + 1);
			memset(&shms[shm_index], 0, sizeof(shms[shm_index]));
		}
		shm = &shms[shm_index];
		struct SysVIpcShmHelperRequest request = {
			.op = SHMHELPER_ALLOC,
			.fd = IPC_OBJECT_ID(shm_index, shm),
			.size = shm_size
		};
		shm->fd = sysvipc_shm_send_helper_request(&request);
		if (shm->fd < 0) {
			return -ENOSPC;
		}
		memset(&shm->stats.shm_segsz, 0, sizeof(shm->stats.shm_segsz));
		shm->stats.shm_perm.mode = shmflg & 0777;
		shm->stats.shm_segsz = shm_size;
		shm->stats.shm_cpid = config->process->pgid;
		shm->key = shm_key;
		shm->valid = true;
		shm->mappings = talloc_zero(config->ipc_namespace, struct SysVIpcSharedMemMaps);
		LIST_INIT(shm->mappings);
	} else {
		if ((shmflg & IPC_CREAT) && (shmflg & IPC_EXCL)) {
			return -EEXIST;
		}
		shm = &shms[shm_index];
		if (shm_size && shm_size != shm->stats.shm_segsz) {
			return -EINVAL;
		}
	}
	return IPC_OBJECT_ID(shm_index, shm);
}

static void sysvipc_do_rmid(struct SysVIpcSharedMem *shm)
{
	assert(LIST_EMPTY(shm->mappings));

	/* Close ashmem fd in helper process  */
	struct SysVIpcShmHelperRequest request = {
		.op = SHMHELPER_FREE,
		.fd = shm->fd,
	};
	sysvipc_shm_send_helper_request(&request);

	/* Mark shm as freed  */
	TALLOC_FREE(shm->mappings);
	shm->valid = false;
	shm->rmid_pending = false;
	shm->generation = (shm->generation + 1) & 0xFFFF;
	shm->fd = -1;
}

static int sysvipc_shm_memmap_destructor(struct SysVIpcSharedMemMap *mapping) {
	LIST_REMOVE(mapping, link_shmid);
	LIST_REMOVE(mapping, link_process);

	struct SysVIpcSharedMem *shm = &mapping->ipc_namespace->shms[mapping->shm_index];
	if (shm->rmid_pending && LIST_EMPTY(shm->mappings)) {
		sysvipc_do_rmid(shm);
	}
	return 0;
}

static void sysvipc_shm_wake_pending_shmat()
{
	Tracee *other_tracee;
	struct SysVIpcConfig *other_config;
	SYSVIPC_FOREACH_TRACEE_ANY_NAMESPACE(other_tracee, other_config) {
		if (other_config->wait_reason == WR_WAIT_SHMAT_HELPER_BUSY) {
			// Restart shmat operation with socket(AF_UNIX, SOCK_SEQPACKET, 0)
			sysvipc_wake_tracee(other_tracee, other_config, 0);
			register_chained_syscall(other_tracee, PR_socket, AF_UNIX, SOCK_SEQPACKET, 0, 0, 0, 0);
			other_config->chain_state = CSTATE_SHMAT_SOCKET;
			return;
		}
	}
}

int sysvipc_shmat(Tracee *tracee, struct SysVIpcConfig *config)
{
	size_t shm_index;
	struct SysVIpcSharedMem *shm;
	LOOKUP_IPC_OBJECT(shm_index, shm, config->ipc_namespace->shms)

	config->waiting_object_index = shm_index;

	// Register mapping for process (to prevent IPC_RMID concurrent to shmat)
	struct SysVIpcSharedMemMap *mapping = talloc_zero(config->process, struct SysVIpcSharedMemMap);
	talloc_set_destructor(mapping, sysvipc_shm_memmap_destructor);
	mapping->ipc_namespace = config->ipc_namespace;
	mapping->shm_index = shm_index;
	LIST_INSERT_HEAD(&config->process->mapped_shms, mapping, link_process);
	LIST_INSERT_HEAD(shm->mappings, mapping, link_shmid);

	// Check if any tracee is running concurrent shmat and wait if so
	Tracee *other_tracee;
	struct SysVIpcConfig *other_config;
	SYSVIPC_FOREACH_TRACEE_ANY_NAMESPACE(other_tracee, other_config) {
		if (other_config->chain_state > CSTATE_SHMAT_SOCKET && other_config->chain_state <= CSTATE_SHMAT_MMAP) {
			config->wait_reason = WR_WAIT_SHMAT_HELPER_BUSY;
			return 0;
		}
	}

	// Start operation with socket(AF_UNIX, SOCK_SEQPACKET, 0)
	set_sysnum(tracee, PR_socket);
	poke_reg(tracee, SYSARG_1, AF_UNIX);
	poke_reg(tracee, SYSARG_2, SOCK_SEQPACKET);
	poke_reg(tracee, SYSARG_3, 0);
	config->chain_state = CSTATE_SHMAT_SOCKET;
	return 0;
}

static struct SysVIpcSharedMemMap *sysvipc_shm_find_pending_mapping(struct SysVIpcProcess *process, struct SysVIpcNamespace *ipc_namespace, size_t shm_index)
{
	struct SysVIpcSharedMemMap *mapping;
	LIST_FOREACH(mapping, &process->mapped_shms, link_process) {
		if (mapping->size == 0 && mapping->shm_index == shm_index && mapping->ipc_namespace == ipc_namespace) {
			return mapping;
		}
	}
	assert(!"No pending mapping found");
}

int sysvipc_shmat_chain(Tracee *tracee, struct SysVIpcConfig *config)
{
	assert(config->waiting_object_index < talloc_array_length(config->ipc_namespace->shms));
	struct SysVIpcSharedMem *shm = &config->ipc_namespace->shms[config->waiting_object_index];
	assert(shm->valid);
	switch (config->chain_state) {
	case CSTATE_SHMAT_SOCKET:
	{
		config->shmat_socket_fd = peek_reg(tracee, CURRENT, SYSARG_RESULT);
		if (config->shmat_socket_fd < 0) {
			goto fail_dont_close;
		}
		word_t guest_addr = peek_reg(tracee, CURRENT, STACK_POINTER) - sizeof(struct sockaddr_un);
		if (guest_addr == 0) {
			goto fail_dont_close;
		}
		if (write_data(tracee, guest_addr, &sysvipc_shm_helper_addr, sizeof(struct sockaddr_un)) < 0) {
			goto fail_dont_close;
		}
		register_chained_syscall(tracee, PR_connect, config->shmat_socket_fd, guest_addr, sizeof(struct sockaddr_un), 0, 0, 0);
		config->shmat_guest_buf = guest_addr;
		config->chain_state = CSTATE_SHMAT_CONNECT;
		return 1;
	}
	case CSTATE_SHMAT_CONNECT:
	{
		if ((int) peek_reg(tracee, CURRENT, SYSARG_RESULT) != 0) {
			goto fail_close_socket;
		}

		struct SysVIpcRecvMsgPointers pointers;
		if (sysvipc_shm_recvmsg_pointers(tracee, &pointers, config->shmat_guest_buf, true)) {
			goto fail_close_socket;
		}

		struct SysVIpcShmHelperRequest request = {
			.op = SHMHELPER_DISTRIBUTE,
			.fd = shm->fd,
		};
		if (sysvipc_shm_send_helper_request(&request) < 0) {
			goto fail_close_socket;
		}

		register_chained_syscall(tracee, PR_recvmsg, config->shmat_socket_fd, pointers.msghdr_ptr, 0, 0, 0, 0);
		config->chain_state = CSTATE_SHMAT_RECVMSG;

		return 1;
	}
	case CSTATE_SHMAT_RECVMSG:
	{
		struct SysVIpcRecvMsgPointers pointers;
		if (sysvipc_shm_recvmsg_pointers(tracee, &pointers, config->shmat_guest_buf, false)) {
			goto fail_close_socket;
		}

		struct cmsghdr cmsg = {};
		if (read_data(tracee, &cmsg, pointers.cmsg_control_ptr, sizeof(cmsg)) < 0) {
			goto fail_close_socket;
		}
		if (cmsg.cmsg_level != SOL_SOCKET || cmsg.cmsg_type != SCM_RIGHTS) {
			goto fail_close_socket;
		}

		word_t fd = 0;
		if (read_data(tracee, &fd, pointers.cmsg_control_ptr + sizeof(cmsg), 4) < 0) {
			goto fail_close_socket;
		}
		if (fd > 0xFFFF) {
			goto fail_close_socket;
		}
		config->shmat_mem_fd = fd;

		size_t page_size = sysconf(_SC_PAGESIZE);
		size_t map_size = shm->stats.shm_segsz;
		map_size = (map_size + (page_size - 1)) & ~(page_size - 1);

		word_t mmap_sysnum = detranslate_sysnum(get_abi(tracee), PR_mmap2) != SYSCALL_AVOIDER
			? PR_mmap2
			: PR_mmap;
		register_chained_syscall(tracee, mmap_sysnum, 0, map_size, PROT_READ|PROT_WRITE, MAP_SHARED, fd, 0);
		config->chain_state = CSTATE_SHMAT_MMAP;

		return 1;
	}
	case CSTATE_SHMAT_MMAP:
	{
		word_t addr = peek_reg(tracee, CURRENT, SYSARG_RESULT);

		struct SysVIpcSharedMemMap *mapping = sysvipc_shm_find_pending_mapping(config->process, config->ipc_namespace, config->waiting_object_index);
		mapping->addr = addr;
		mapping->size = peek_reg(tracee, CURRENT, SYSARG_2);

		register_chained_syscall(tracee, PR_close, config->shmat_mem_fd, 0, 0, 0, 0, 0);
		register_chained_syscall(tracee, PR_close, config->shmat_socket_fd, 0, 0, 0, 0, 0);
		force_chain_final_result(tracee, addr);
		config->chain_state = CSTATE_NOT_CHAINED;
		sysvipc_shm_wake_pending_shmat();

		return 1;
	}

	default:
		assert(!"Invalid chain_state in sysvipc_shmat_chain");
	}
	assert(!"Switch in sysvipc_shmat_chain has fallen thorugh");

fail_close_socket:
	register_chained_syscall(tracee, PR_close, config->shmat_socket_fd, 0, 0, 0, 0, 0);
	force_chain_final_result(tracee, -ENOMEM);
	config->chain_state = CSTATE_NOT_CHAINED;
	talloc_free(sysvipc_shm_find_pending_mapping(config->process, config->ipc_namespace, config->waiting_object_index));
	sysvipc_shm_wake_pending_shmat();
	return 1;
fail_dont_close:
	config->chain_state = CSTATE_NOT_CHAINED;
	talloc_free(sysvipc_shm_find_pending_mapping(config->process, config->ipc_namespace, config->waiting_object_index));
	sysvipc_shm_wake_pending_shmat();
	return -ENOMEM;
}

int sysvipc_shmdt(Tracee *tracee, struct SysVIpcConfig *config)
{
	word_t addr = peek_reg(tracee, CURRENT, SYSARG_1);
	struct SysVIpcSharedMemMap *mapped;
	LIST_FOREACH(mapped, &config->process->mapped_shms, link_process) {
		if (mapped->addr == addr) {
			set_sysnum(tracee, PR_munmap);
			poke_reg(tracee, SYSARG_2, mapped->size);
			config->chain_state = CSTATE_SINGLE;
			/* This talloc_free executes destructor and unlinks region */
			talloc_free(mapped);
			return 0;
		}
	}
	return -EINVAL;
}

static void sysvipc_shm_update_stats(struct SysVIpcSharedMem *shm)
{
	shm->stats.shm_nattch = 0;
	struct SysVIpcSharedMemMap *mapping = NULL;
	LIST_FOREACH(mapping, shm->mappings, link_shmid) {
		shm->stats.shm_nattch++;
	}
}

int sysvipc_shmctl(Tracee *tracee, struct SysVIpcConfig *config)
{
	size_t shm_index;
	struct SysVIpcSharedMem *shm;
	LOOKUP_IPC_OBJECT(shm_index, shm, config->ipc_namespace->shms)

	int cmd = peek_reg(tracee, CURRENT, SYSARG_2);
	word_t buf = peek_reg(tracee, CURRENT, SYSARG_3);
	
	switch (cmd) {
	case IPC_RMID:
	case IPC_RMID | SYSVIPC_IPC_64:
	{
		/* Perform rmid only if this region is not mapped,
		 * otherwise set flag to do so once all maps are unmapped */
		if (LIST_EMPTY(shm->mappings)) {
			sysvipc_do_rmid(shm);
		} else {
			shm->rmid_pending = true;
		}

		return 0;
	}
	case IPC_STAT:
	{
		/* Update shm_nattch */
		sysvipc_shm_update_stats(shm);

		/* Copy stats to user  */
		int status = write_data(tracee, buf, &shm->stats, sizeof(struct SysVIpcShmidDs));
		if (status < 0) return status;
		return 0;
	}
	default:
		return -EINVAL;
	}
}

void sysvipc_shm_inherit_process(struct SysVIpcProcess *parent, struct SysVIpcProcess *child)
{
	struct SysVIpcSharedMemMap *parent_mapping = NULL;
	struct SysVIpcSharedMemMap *prev_inserted_mapping = NULL;
	LIST_FOREACH(parent_mapping, &parent->mapped_shms, link_process) {
		struct SysVIpcSharedMemMap *new_mapping = talloc_zero(child, struct SysVIpcSharedMemMap);
		talloc_set_destructor(new_mapping, sysvipc_shm_memmap_destructor);

		new_mapping->addr = parent_mapping->addr;
		new_mapping->size = parent_mapping->size;
		new_mapping->ipc_namespace = parent_mapping->ipc_namespace;
		new_mapping->shm_index = parent_mapping->shm_index;
		LIST_INSERT_AFTER(parent_mapping, new_mapping, link_shmid);

		if (prev_inserted_mapping != NULL) {
			LIST_INSERT_AFTER(prev_inserted_mapping, new_mapping, link_process);
		} else {
			LIST_INSERT_HEAD(&child->mapped_shms, new_mapping, link_process);
		}
		prev_inserted_mapping = new_mapping;
	}
}

void sysvipc_shm_remove_mappings_from_process(struct SysVIpcProcess *process)
{
	/* Remove all mappings from process
	 * Using manual le_ pointer operations because we free during loop */
	struct SysVIpcSharedMemMap *mapping = process->mapped_shms.lh_first;
	while (mapping != NULL) {
		struct SysVIpcSharedMemMap *next_mapping = mapping->link_process.le_next;
		talloc_free(mapping);
		mapping = next_mapping;
	}
}

void sysvipc_shm_fill_proc(FILE *proc_file, struct SysVIpcNamespace *ipc_namespace)
{
	fprintf(
		proc_file,
		"       key      shmid perms                  size  cpid  lpid nattch   uid   gid  cuid  cgid      atime      dtime      ctime                   rss                  swap\n"
	);

	size_t page_size = sysconf(_SC_PAGESIZE);
	struct SysVIpcSharedMem *shms = ipc_namespace->shms;
	size_t shm_index = 0;
	size_t num_shms = talloc_array_length(shms);
	for (; shm_index < num_shms; shm_index++) {
		struct SysVIpcSharedMem *shm = &shms[shm_index];
		if (!shms[shm_index].valid) {
			continue;
		}

		sysvipc_shm_update_stats(shm);
		size_t map_size = shm->stats.shm_segsz;
		map_size = (map_size + (page_size - 1)) & ~page_size;

		fprintf(
			proc_file,
			"%10d %10d  %4o %21lu %5u %5u  "
			"%5lu %5u %5u %5u %5u %10llu %10llu %10llu "
			"%21lu %21lu\n",
			shm->key,
			(int) IPC_OBJECT_ID(shm_index, shm),
			shm->stats.shm_perm.mode,
			shm->stats.shm_segsz,
			shm->stats.shm_cpid,
			shm->stats.shm_lpid,
			shm->stats.shm_nattch,
			shm->stats.shm_perm.uid,
			shm->stats.shm_perm.gid,
			shm->stats.shm_perm.cuid,
			shm->stats.shm_perm.cgid,
			(unsigned long long) shm->stats.shm_atime,
			(unsigned long long) shm->stats.shm_dtime,
			(unsigned long long) shm->stats.shm_ctime,
			map_size,
			0L
		);
	}
}

int sysvipc_shm_namespace_destructor(struct SysVIpcNamespace *ipc_namespace) {
	struct SysVIpcSharedMem *shms = ipc_namespace->shms;
	size_t shm_index = 0;
	size_t num_shms = talloc_array_length(shms);
	for (; shm_index < num_shms; shm_index++) {
		struct SysVIpcSharedMem *shm = &shms[shm_index];
		if (shm->valid) {
			struct SysVIpcSharedMemMap *mapping;
			LIST_FOREACH(mapping, shm->mappings, link_shmid) {
				talloc_set_destructor(mapping, NULL);
			}
		}
	}
	return 0;
}

static int sysvipc_shm_do_allocate(size_t size, int shmid) {
#ifdef __ANDROID__
	int fd = open("/dev/ashmem", O_RDWR, 0);
	if (fd < 0) return -ENOSPC;

	char name_buffer[ASHMEM_NAME_LEN] = {0};
	snprintf(name_buffer, ASHMEM_NAME_LEN - 1, "sysvshm_0x%X", shmid);
	ioctl(fd, ASHMEM_SET_NAME, name_buffer);

	int ret = ioctl(fd, ASHMEM_SET_SIZE, size);
	if (ret < 0) {
		close(fd);
		return -ENOSPC;
	}

	return fd;
#else
	(void) shmid;
	FILE *fdesc = tmpfile();
	if (!fdesc) return -ENOSPC;
	int fd = dup(fileno(fdesc));
	fclose(fdesc);
	if (fd < 0) return -ENOSPC;

	if (ftruncate(fd, size) == -1) {
		return -ENOSPC;
	}

	return fd;
#endif
}

void sysvipc_shm_helper_main() {
	char *path;
	int socket_server_fd = socket(AF_UNIX, SOCK_SEQPACKET, 0);
	struct sockaddr_un addr = {
		.sun_family = AF_UNIX
	};
	for (int i = 0;; i++) {
		path = create_temp_name(NULL, "prootshm");
		(void) mktemp(path);

		if (strlen(path) > SYSVIPC_SHMHELPER_SOCKET_LEN) {
			close(socket_server_fd);
			fprintf(stderr, "proot-shm-helper: Temporary path too long\n");
			_exit(1);
		}

		memset(addr.sun_path, 0 , sizeof(addr.sun_path));
		strncpy(addr.sun_path, path, sizeof(addr.sun_path));
		
		if (bind(socket_server_fd, (struct sockaddr *) &addr, sizeof(addr)) == 0) {
			break;
		}

		if (i >= 64) {
			perror("proot-shm-helper: bind");
			TALLOC_FREE(path);
			close(socket_server_fd);
			_exit(1);
		}
		TALLOC_FREE(path);
	}

	if (listen(socket_server_fd, 1) < 0) {
		perror("proot-shm-helper: listen");
		unlink(path);
		_exit(0);
	}

	write(1, addr.sun_path, SYSVIPC_SHMHELPER_SOCKET_LEN);
	for (;;) {
		struct SysVIpcShmHelperRequest request;
		int status = TEMP_FAILURE_RETRY(read(0, &request, sizeof(request)));
		if (status == 0) {
			break;
		}
		if (status < 0) {
			perror("proot-shm-helper: read");
			break;
		}
		if (status != sizeof(request)) {
			fprintf(stderr, "proot-shm-helper: Incomplete request\n");
			break;
		}
		switch (request.op) {
		case SHMHELPER_ALLOC:
		{
			int fd = sysvipc_shm_do_allocate(request.size, request.fd);
			write(1, &fd, sizeof(int));
			break;
		}
		case SHMHELPER_FREE:
			close(request.fd);
			break;
		case SHMHELPER_DISTRIBUTE:
		{
			int client_fd = accept(socket_server_fd, NULL, 0);

			char nothing = '!';
			struct iovec nothing_ptr = { .iov_base = &nothing, .iov_len = 1 };

			struct {
				struct cmsghdr align;
				int fd[1];
			} ancillary_data_buffer;

			struct msghdr message_header = {
				.msg_name = NULL,
				.msg_namelen = 0,
				.msg_iov = &nothing_ptr,
				.msg_iovlen = 1,
				.msg_flags = 0,
				.msg_control = &ancillary_data_buffer,
				.msg_controllen = sizeof(struct cmsghdr) + sizeof(int)
			};

			struct cmsghdr* cmsg = CMSG_FIRSTHDR(&message_header);
			cmsg->cmsg_len = message_header.msg_controllen; // sizeof(int);
			cmsg->cmsg_level = SOL_SOCKET;
			cmsg->cmsg_type = SCM_RIGHTS;
			((int*) CMSG_DATA(cmsg))[0] = request.fd;

			sendmsg(client_fd, &message_header, 0);
			close(client_fd);
			break;
		}
		default:
			fprintf(stderr, "proot-shm-helper: Bad request\n");
			break;
		}
	}

	unlink(path);
	_exit(0);
}
