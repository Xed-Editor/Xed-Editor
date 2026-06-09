#include <unistd.h>      /* get*id(2),  */
#include <sys/socket.h>  /* cmsghdr, */
#include <sys/types.h>   /* uid_t, gid_t, get*id(2), */
#include <linux/net.h>   /* SYS_SENDMSG, */

#include "cli/note.h"
#include "tracee/mem.h"
#include "syscall/sysnum.h"
#include "syscall/syscall.h"
#include "extension/fake_id0/sendmsg.h"

#define MAX_CONTROLLEN 1024

static void sendmsg_unpack_control_and_len(const Tracee *tracee, const struct msghdr *msghdr, word_t *out_control, size_t *out_controllen) {
#if defined(ARCH_X86_64) || defined(ARCH_ARM64)
	if (is_32on64_mode(tracee)) {
		const char *raw_msghdr = (const char *) msghdr;
		*out_control = *(uint32_t*) &raw_msghdr[16];
		*out_controllen = *(uint32_t*) &raw_msghdr[20];
	} else
#else
	(void) tracee;
#endif
	{
		*out_control = (word_t) msghdr->msg_control;
		*out_controllen = msghdr->msg_controllen;
	}
}

static void sendmsg_pack_control(const Tracee *tracee, struct msghdr *msghdr, word_t control) {
#if defined(ARCH_X86_64) || defined(ARCH_ARM64)
	if (is_32on64_mode(tracee)) {
		const char *raw_msghdr = (const char *) msghdr;
		*(uint32_t*) &raw_msghdr[16] = (uint32_t) control;
	} else
#else
	(void) tracee;
#endif
	{
		msghdr->msg_control = (void *) control;
	}
}

static void sendmsg_unpack_cmsghdr(const Tracee *tracee, const struct cmsghdr *cmsghdr, size_t *out_len, int *out_level, int *out_type) {
#if defined(ARCH_X86_64) || defined(ARCH_ARM64)
	if (is_32on64_mode(tracee)) {
		const uint32_t *cmsghdr_as_ints = (const uint32_t *) cmsghdr;
		*out_len = cmsghdr_as_ints[0];
		*out_level = cmsghdr_as_ints[1];
		*out_type = cmsghdr_as_ints[2];
	} else
#else
	(void) tracee;
#endif
	{
		*out_len = cmsghdr->cmsg_len;
		*out_level = cmsghdr->cmsg_level;
		*out_type = cmsghdr->cmsg_type;
	}
}

int handle_sendmsg_enter_end(Tracee *tracee, word_t sysnum)
{
	/* Read sendmsg header.  */
	int status;
	unsigned long socketcall_args[3];
	struct msghdr msg = {};
	bool is_socketcall = sysnum == PR_socketcall;

	size_t tracee_sizeof_msghdr = sizeof(struct msghdr);
	size_t tracee_sizeof_cmsghdr = sizeof(struct cmsghdr);
	size_t tracee_alignofmask_cmsghdr = sizeof(long) - 1;
#if defined(ARCH_X86_64) || defined(ARCH_ARM64)
	if (is_32on64_mode(tracee)) {
		tracee_sizeof_msghdr = 28;
		tracee_sizeof_cmsghdr = 12;
		tracee_alignofmask_cmsghdr = 4 - 1;
	}
#endif

	if (!is_socketcall)
	{
		status = read_data(tracee, &msg, peek_reg(tracee, CURRENT, SYSARG_2), tracee_sizeof_msghdr);
		if (status < 0) return status;
	}
	else
	{
		word_t call = peek_reg(tracee, CURRENT, SYSARG_1);

		/* On i386 opening audit socket is done through socketcall()
		 * See extension/fake_id0/socket.c
		 * for non-socketcall handler.  */
		if (call == SYS_SOCKET) {
			status = read_data(tracee, socketcall_args, peek_reg(tracee, CURRENT, SYSARG_2), sizeof(socketcall_args));
			/* Emulate audit functionality not compiled into kernel
			 * 		 * if tracee was supposed to have the capability.  */
			if (
			socketcall_args[0] == 16 /* AF_NETLINK */ &&
			socketcall_args[2] == 9 /* NETLINK_AUDIT */)
				return -EPROTONOSUPPORT;
		}

		/* Check if socketcall(2) is a sendmsg(2)  */
		if (call != SYS_SENDMSG) return 0;

		/* Read socketcall args structure */
		status = read_data(tracee, socketcall_args, peek_reg(tracee, CURRENT, SYSARG_2), sizeof(socketcall_args));
		if (status < 0) return status;

		/* Read sendmsg header */
		status = read_data(tracee, &msg, socketcall_args[1], tracee_sizeof_msghdr);
		if (status < 0) return status;
	}

	size_t msg_controllen;
	word_t msg_control;
	sendmsg_unpack_control_and_len(tracee, &msg, &msg_control, &msg_controllen);
	if (msg_control != 0 && msg_controllen != 0)
	{
		bool did_modify = 0;

		if (msg.msg_controllen > MAX_CONTROLLEN) {
			VERBOSE(tracee, 1, "sendmsg() with msg_controllen=%zu, is_32on64_mode=%d, not doing fixup", msg_controllen, is_32on64_mode(tracee));
			return 0;
		}

		/* Read cmsg header.  */
		char cmsg_buf[msg_controllen];
		status = read_data(tracee, cmsg_buf, msg_control, msg_controllen);
		if (status < 0) return status;

		/* Iterate over control messages.  */
		size_t msg_position = 0;
		while (msg_position < msg_controllen)
		{
			if (msg_controllen - msg_position < tracee_sizeof_cmsghdr) {
				/* Malformed cmsg - header didn't fit in last entry.  */
				return 0;
			}

			size_t cmsg_len;
			int cmsg_level, cmsg_type;
			sendmsg_unpack_cmsghdr(tracee, (const struct cmsghdr *) &cmsg_buf[msg_position], &cmsg_len, &cmsg_level, &cmsg_type);

			if (!(
					cmsg_len >= tracee_sizeof_cmsghdr &&
					cmsg_len <= msg_controllen - msg_position
				))
			{
				/* Malformed cmsg - header or body didn't fit in entry.  */
				return 0;
			}

			/* Look into cmsg data.  */
			if (cmsg_level == SOL_SOCKET && cmsg_type == SCM_CREDENTIALS)
			{
				/* cmsg_len != CMSG_LEN(sizeof(struct ucred))  **/
				if (cmsg_len != tracee_sizeof_cmsghdr + sizeof(struct ucred))
				{
					/* Malformed cmsg - struct ucred size mismatch.  */
					return 0;
				}
				struct ucred *ucred = (struct ucred *) &cmsg_buf[msg_position + tracee_sizeof_cmsghdr];
				/* Set uid and gid of SCM_CREDENTIALS to ones that proot really has.
				 * Pid is not changed as we don't fiddle with getpid()  */
				ucred->uid = getuid();
				ucred->gid = getgid();
				did_modify = true;
			}

			/* Advance to next message (CMSG_NXTHDR).  */
			msg_position += (cmsg_len + tracee_alignofmask_cmsghdr) & ~tracee_alignofmask_cmsghdr;
		}
		if (did_modify)
		{
			/* Write cmsg data into tracee. */
			msg_control = alloc_mem(tracee, msg_controllen);
			if (msg_control == 0) return -ENOMEM;

			status = write_data(tracee, msg_control, cmsg_buf, msg_controllen);
			if (status < 0) return -ENOMEM;

			/* Write sendmsg header.  */
			word_t sendmsg_header_addr = alloc_mem(tracee, tracee_sizeof_msghdr);
			if (sendmsg_header_addr == 0) return -ENOMEM;

			sendmsg_pack_control(tracee, &msg, msg_control);
			status = write_data(tracee, sendmsg_header_addr, &msg, tracee_sizeof_msghdr);
			if (status < 0) return -ENOMEM;

			/* Write address of new sendmsg header.  */
			if (!is_socketcall)
			{
				poke_reg(tracee, SYSARG_2, sendmsg_header_addr);
			}
			else
			{
				socketcall_args[1] = sendmsg_header_addr;
				status = set_sysarg_data(tracee, socketcall_args, sizeof(socketcall_args), SYSARG_2);
				if (status < 0) return status;
			}
		}
	}

	return 0;
}
