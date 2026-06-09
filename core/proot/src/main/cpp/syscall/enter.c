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
#include <talloc.h>      /* talloc_*, */
#include <sys/un.h>      /* struct sockaddr_un, */
#include <linux/net.h>   /* SYS_*, */
#include <fcntl.h>       /* AT_FDCWD, */
#include <unistd.h>      /* close(2), */
#include <limits.h>      /* PATH_MAX, */
#include <string.h>      /* strcpy */
#include <stdbool.h>     /* bool */
#include <stdint.h>      /* uint32_t */
#include <sys/prctl.h>   /* PR_SET_DUMPABLE */
#include <sys/mount.h>   /* MS_BIND, MS_REMOUNT, ... */
#include <sys/socket.h>  /* AF_NETLINK, AF_UNIX, SOCK_DGRAM, SOCK_CLOEXEC */
#include <sched.h>       /* CLONE_NEW*, */
#include <termios.h>     /* TCSETS, TCSANOW */
#include <linux/netlink.h> /* struct nlmsghdr, NLMSG_ERROR, struct nlmsgerr */
#include <linux/rtnetlink.h> /* RTM_*, struct ifinfomsg, struct rtattr, RTA_* */
#include <linux/if_addr.h> /* struct ifaddrmsg, IFA_*, IFA_F_PERMANENT */
#include <linux/sockios.h> /* SIOCGIFINDEX */
#include <net/if.h>      /* struct ifreq, IFNAMSIZ, IFF_* */
#include <ifaddrs.h>     /* getifaddrs(3), to enumerate host interfaces */
#include <netinet/in.h>  /* struct sockaddr_in / sockaddr_in6 */
#include <netpacket/packet.h> /* struct sockaddr_ll (AF_PACKET) */
#include <sys/ioctl.h>   /* ioctl(2): SIOCGIFMTU / SIOCGIFHWADDR */
#include <sys/time.h>    /* struct timeval, for SO_RCVTIMEO */

/* ABI-stable rtnetlink constants we synthesise for the loopback reply.
 * Defined locally so we needn't pull in <linux/if.h> / <linux/if_arp.h>,
 * which clash with the already-included <net/if.h>.  */
#ifndef ARPHRD_LOOPBACK
#define ARPHRD_LOOPBACK 772
#endif
#ifndef ARPHRD_ETHER
#define ARPHRD_ETHER 1
#endif
#ifndef IFF_LOWER_UP
#define IFF_LOWER_UP 0x10000
#endif

#include "cli/note.h"
#include "syscall/syscall.h"
#include "syscall/sysnum.h"
#include "syscall/socket.h"
#include "ptrace/ptrace.h"
#include "ptrace/wait.h"
#include "syscall/heap.h"
#include "extension/extension.h"
#include "execve/execve.h"
#include "tracee/tracee.h"
#include "tracee/reg.h"
#include "tracee/mem.h"
#include "tracee/abi.h"
#include "tracee/event.h"
#include "path/path.h"
#include "path/canon.h"
#include "path/binding.h"
#include "path/temp.h"
#include "arch.h"

/* Older kernel headers may lack these. */
#ifndef CLONE_NEWTIME
#define CLONE_NEWTIME 0x00000080
#endif
#ifndef CLONE_NEWCGROUP
#define CLONE_NEWCGROUP 0x02000000
#endif

#define CLONE_NS_MASK (CLONE_NEWNS | CLONE_NEWUTS | CLONE_NEWIPC | \
		       CLONE_NEWUSER | CLONE_NEWPID | CLONE_NEWNET | \
		       CLONE_NEWCGROUP | CLONE_NEWTIME)

/**
 * Translate @path and put the result in the @tracee's memory address
 * space pointed to by the @reg argument of the current syscall. See
 * the documentation of translate_path() about the meaning of
 * @type. This function returns -errno if an error occured, otherwise
 * 0.
 */
static int translate_path2(Tracee *tracee, int dir_fd, char path[PATH_MAX], Reg reg, Type type)
{
	char new_path[PATH_MAX];
	int status;

	/* Special case where the argument was NULL. */
	if (path[0] == '\0')
		return 0;

	/* Translate the original path. */
	status = translate_path(tracee, new_path, dir_fd, path, type != SYMLINK);
	if (status < 0)
		return status;

	return set_sysarg_path(tracee, new_path, reg);
}

/**
 * A helper, see the comment of the function above.
 */
static int translate_sysarg(Tracee *tracee, Reg reg, Type type)
{
	char old_path[PATH_MAX];
	int status;

	/* Extract the original path. */
	status = get_sysarg_path(tracee, old_path, reg);
	if (status < 0)
		return status;

	return translate_path2(tracee, AT_FDCWD, old_path, reg, type);
}

/**
 * Canonicalize @user_path as a guest path, relative to the @tracee's
 * cwd when @user_path is relative.  Stores the result in @guest_path
 * with any trailing "/" or "/." stripped, so it can be used as a
 * binding key.  Returns 0 on success, -errno otherwise.
 */
static int guest_canonicalize(Tracee *tracee, const char *user_path,
			      char guest_path[PATH_MAX])
{
	int status;

	if (user_path[0] == '/')
		strcpy(guest_path, "/");
	else {
		status = getcwd2(tracee, guest_path);
		if (status < 0)
			return status;
	}

	status = canonicalize(tracee, user_path, true, guest_path, 0);
	if (status < 0)
		return status;

	chop_finality(guest_path);
	return 0;
}

/**
 * Emulate mount(@src_user, @target_user, @fstype, @flags) by adding a
 * PRoot binding from a host directory to the canonicalized target.
 * Bind mounts use the translated source; "proc"/"sysfs" use the
 * matching host file-system; "tmpfs"/"devpts"/"devtmpfs" get a fresh
 * empty directory.  Any other case is silently ignored: the caller
 * will still see the syscall succeed (we always void it).
 */
static void emulate_mount(Tracee *tracee, const char *src_user,
			  const char *target_user, const char *fstype,
			  unsigned long flags)
{
	char host_path[PATH_MAX];
	char guest_path[PATH_MAX];
	const char *tmpdir;

	if ((flags & MS_REMOUNT) != 0)
		return;

	if ((flags & MS_BIND) != 0) {
		if (translate_path(tracee, host_path, AT_FDCWD, src_user, true) < 0)
			return;
	}
	else if (strcmp(fstype, "proc") == 0)
		strcpy(host_path, "/proc");
	else if (strcmp(fstype, "sysfs") == 0)
		strcpy(host_path, "/sys");
	else if (strcmp(fstype, "devtmpfs") == 0)
		strcpy(host_path, "/dev");
	else if (strcmp(fstype, "devpts") == 0)
		strcpy(host_path, "/dev/pts");
	else if (strcmp(fstype, "tmpfs") == 0) {
		tmpdir = create_temp_directory(tracee->fs, "proot-tmpfs-");
		if (tmpdir == NULL)
			return;
		strncpy(host_path, tmpdir, PATH_MAX - 1);
		host_path[PATH_MAX - 1] = '\0';
	}
	else
		return;

	chop_finality(host_path);

	if (guest_canonicalize(tracee, target_user, guest_path) < 0)
		return;

	(void) insort_binding3(tracee, tracee->fs, host_path, guest_path);
}

/**
 * Emulate pivot_root(@new_root_user, @put_old_user) by changing the
 * tracee's root binding to point at @new_root_user (translated to
 * host) and re-exposing the previous root at @put_old_user, so that
 * sandbox helpers like bubblewrap can keep accessing the prior
 * file-system through the agreed "oldroot" path.
 */
static void emulate_pivot_root(Tracee *tracee, const char *new_root_user,
			       const char *put_old_user)
{
	char new_root_host[PATH_MAX];
	char new_root_guest[PATH_MAX];
	char put_old_guest[PATH_MAX];
	char old_root_host[PATH_MAX];
	Binding *root_binding;
	Binding **snapshot;
	size_t new_root_len;
	size_t put_old_len = 0;
	char put_old_after[PATH_MAX];
	bool have_put_old = false;
	size_t count = 0;
	size_t i;
	Binding *iter;

	if (translate_path(tracee, new_root_host, AT_FDCWD, new_root_user, true) < 0)
		return;
	chop_finality(new_root_host);

	if (guest_canonicalize(tracee, new_root_user, new_root_guest) < 0)
		return;

	/* put_old is relative to new_root, so resolve it against
	 * new_root_guest rather than the current cwd. */
	if (put_old_user[0] == '/')
		strcpy(put_old_guest, "/");
	else
		strcpy(put_old_guest, new_root_guest);
	if (canonicalize(tracee, put_old_user, true, put_old_guest, 0) < 0)
		return;

	root_binding = get_binding(tracee, GUEST, "/");
	if (root_binding == NULL)
		return;
	strncpy(old_root_host, root_binding->host.path, PATH_MAX - 1);
	old_root_host[PATH_MAX - 1] = '\0';

	new_root_len = strlen(new_root_guest);

	/* Work out where the previous root becomes reachable: put_old as a
	 * path under the *new* root, e.g. "/oldroot".  The pivot_root(".",
	 * ".") trick used to detach the old root leaves new_root == put_old,
	 * in which case there is nowhere to expose it. */
	if (   new_root_len > 0
	    && strncmp(put_old_guest, new_root_guest, new_root_len) == 0
	    && (   put_old_guest[new_root_len] == '/'
		|| (new_root_len == 1 && new_root_guest[0] == '/'))) {
		const char *after = put_old_guest + (new_root_len == 1 ? 0 : new_root_len);
		if (after[0] == '/' && after[1] != '\0') {
			strncpy(put_old_after, after, PATH_MAX - 1);
			put_old_after[PATH_MAX - 1] = '\0';
			put_old_len = strlen(put_old_after);
			have_put_old = true;
		}
	}

	/* Snapshot the current bindings before mutating the lists: the loop
	 * below both inserts (rebased / re-exposed) and removes bindings, so
	 * walking the live list would be unsafe. */
	for (iter = CIRCLEQ_FIRST(tracee->fs->bindings.guest);
	     iter != (void *) tracee->fs->bindings.guest;
	     iter = CIRCLEQ_NEXT(iter, link.guest))
		count++;

	snapshot = talloc_array(tracee->ctx, Binding *, count);
	if (snapshot == NULL)
		return;
	i = 0;
	for (iter = CIRCLEQ_FIRST(tracee->fs->bindings.guest);
	     iter != (void *) tracee->fs->bindings.guest && i < count;
	     iter = CIRCLEQ_NEXT(iter, link.guest))
		snapshot[i++] = iter;

	/* Switch the root over to new_root and expose the previous root at
	 * put_old, so the tracee can still reach it (bubblewrap accesses
	 * everything via "/oldroot" right after the pivot). */
	remove_binding_from_all_lists(tracee, root_binding);
	(void) insort_binding3(tracee, tracee->fs, new_root_host, "/");
	if (have_put_old)
		(void) insort_binding3(tracee, tracee->fs, old_root_host, put_old_after);

	for (i = 0; i < count; i++) {
		Binding *b = snapshot[i];
		size_t blen;

		if (b == root_binding || strcmp(b->guest.path, "/") == 0)
			continue;

		blen = strlen(b->guest.path);

		/* Bindings that live under the new root move *with* the pivot:
		 * "/newroot/usr" becomes "/usr".  Without this, the tracee's
		 * own bind mounts (e.g. bubblewrap's "--ro-bind /usr /usr"
		 * followed by pivot_root into that new root) stay at their
		 * pre-pivot guest path, so "/usr" resolves to the empty
		 * new-root mountpoint and exec'ing a binary under it fails
		 * with ENOENT. */
		if (   new_root_len > 0
		    && blen > new_root_len
		    && strncmp(b->guest.path, new_root_guest, new_root_len) == 0
		    && b->guest.path[new_root_len] == '/') {
			(void) insort_binding3(tracee, tracee->fs, b->host.path,
					       b->guest.path + new_root_len);
			/* Drop the stale pre-pivot binding so it can't shadow
			 * the rebased one (e.g. host->guest detranslation). */
			remove_binding_from_all_lists(tracee, b);
			continue;
		}

		/* Everything else belonged to the previous root tree;
		 * re-expose it under put_old (skipping what already sits
		 * there) and keep the original in place. */
		if (have_put_old) {
			char aliased[PATH_MAX];

			if (   strncmp(b->guest.path, put_old_after, put_old_len) == 0
			    && (   b->guest.path[put_old_len] == '\0'
				|| b->guest.path[put_old_len] == '/'))
				continue;

			if ((size_t) snprintf(aliased, sizeof(aliased), "%s%s",
					      put_old_after, b->guest.path)
			    >= sizeof(aliased))
				continue;

			(void) insort_binding3(tracee, tracee->fs,
					       b->host.path, aliased);
		}
	}

	talloc_free(snapshot);
}

/**
 * Emulate umount(@target_user) by removing the matching binding (if
 * any) so that a subsequent access to @target_user no longer goes
 * through the now-unmounted location.  This is the inverse of
 * emulate_mount().  Bindings put in place at PRoot startup
 * (recommended -R bindings, the rootfs itself) are NOT removed: we
 * only drop runtime bindings whose guest path exactly matches.
 */
static void emulate_umount(Tracee *tracee, const char *target_user)
{
	char guest_path[PATH_MAX];
	Binding *binding;

	if (guest_canonicalize(tracee, target_user, guest_path) < 0)
		return;

	/* Never drop the root binding.  */
	if (strcmp(guest_path, "/") == 0)
		return;

	binding = get_binding(tracee, GUEST, guest_path);
	if (binding == NULL)
		return;

	/* Only drop the binding if its guest path is exactly the
	 * unmount target; otherwise we'd unbind something the tracee
	 * didn't ask to unmount (e.g. its containing rootfs).  */
	if (strcmp(binding->guest.path, guest_path) != 0)
		return;

	remove_binding_from_all_lists(tracee, binding);
}

/**
 * Read umount(2)/umount2(2) arguments from the @tracee's registers
 * and apply emulate_umount().
 */
void apply_emulated_umount(Tracee *tracee)
{
	char target_user[PATH_MAX];

	if (get_sysarg_path(tracee, target_user, SYSARG_1) < 0)
		return;

	emulate_umount(tracee, target_user);
}

/**
 * Read mount(2) arguments from the @tracee's registers and apply
 * emulate_mount().  Safe to call from both the normal sysenter path
 * and the SIGSYS handler (Android's parent seccomp filter traps
 * mount, so the syscall never reaches our regular case).
 */
void apply_emulated_mount(Tracee *tracee)
{
	char src_user[PATH_MAX];
	char target_user[PATH_MAX];
	char fstype[256];
	word_t fstype_addr;
	unsigned long flags;

	fstype[0] = '\0';
	/* read_string doesn't guarantee a trailing NUL when it hits the
	 * size limit before finding one in the tracee's memory; pin the
	 * last byte so the downstream strcmp can't read past the buffer. */
	fstype[sizeof(fstype) - 1] = '\0';

	if (get_sysarg_path(tracee, src_user, SYSARG_1) < 0)
		return;
	if (get_sysarg_path(tracee, target_user, SYSARG_2) < 0)
		return;

	fstype_addr = peek_reg(tracee, CURRENT, SYSARG_3);
	if (fstype_addr != 0)
		(void) read_string(tracee, fstype, fstype_addr, sizeof(fstype) - 1);
	flags = peek_reg(tracee, CURRENT, SYSARG_4);

	emulate_mount(tracee, src_user, target_user, fstype, flags);
}

/**
 * Read pivot_root(2) arguments from the @tracee's registers and apply
 * emulate_pivot_root().  See apply_emulated_mount() for context.
 */
void apply_emulated_pivot_root(Tracee *tracee)
{
	char new_root_user[PATH_MAX];
	char put_old_user[PATH_MAX];

	if (get_sysarg_path(tracee, new_root_user, SYSARG_1) < 0)
		return;
	if (get_sysarg_path(tracee, put_old_user, SYSARG_2) < 0)
		return;

	emulate_pivot_root(tracee, new_root_user, put_old_user);
}

/**
 * Helpers for emulating AF_NETLINK / NETLINK_ROUTE traffic.  Some
 * environments deny the tracee a real netlink socket (Android's
 * SELinux policy on untrusted_app domains, seccomp filters inherited
 * from a Termux-like launcher, hardened containers, ...); in that
 * case we silently substitute an AF_UNIX/SOCK_DGRAM socket and
 * intercept the netlink-shaped syscalls a tracee might issue on it
 * (bind, sendto / sendmsg, recvfrom / recvmsg, getsockname,
 * getpeername), synthesising responses that match what the request
 * asked for: NLMSG_ERROR(err=0) for non-dump requests (bubblewrap's
 * loopback_setup RTM_NEWADDR / RTM_NEWLINK) and an empty NLMSG_DONE
 * for NLM_F_DUMP queries (apt / glibc getifaddrs RTM_GETADDR,
 * iproute2 RTM_GETLINK), so callers see a well-formed empty result
 * instead of a zero-byte recvmsg they treat as fatal.
 *
 * The substitution only happens when the host kernel actually
 * refuses AF_NETLINK; otherwise the tracee gets a real netlink
 * socket and ordinary users like c-ares (dnf, getaddrinfo, ...)
 * keep working.
 */

static bool host_blocks_af_netlink(const Tracee *tracee)
{
	enum { PROBE_UNKNOWN, PROBE_ALLOWED, PROBE_BLOCKED };
	static int cached = PROBE_UNKNOWN;
	struct sockaddr_nl snl;
	const char *blocked_op;
	int fd;
	int saved_errno;

	if (cached != PROBE_UNKNOWN)
		return cached == PROBE_BLOCKED;

	/* Mirror what bubblewrap's loopback_setup() does: socket() then
	 * bind() with nl_groups == 0.  Some hosts permit socket creation
	 * but reject bind() under separate SELinux/AppArmor/seccomp
	 * checks, so probing socket() alone would wrongly classify them
	 * as "AF_NETLINK works" and leave the tracee to fail later.  */
	fd = socket(AF_NETLINK, SOCK_RAW | SOCK_CLOEXEC, NETLINK_ROUTE);
	if (fd < 0) {
		saved_errno = errno;
		blocked_op = "socket";
		goto blocked;
	}

	memset(&snl, 0, sizeof(snl));
	snl.nl_family = AF_NETLINK;
	if (bind(fd, (struct sockaddr *) &snl, sizeof(snl)) < 0) {
		saved_errno = errno;
		close(fd);
		blocked_op = "bind";
		goto blocked;
	}

	close(fd);
	cached = PROBE_ALLOWED;
	return false;

blocked:
	cached = PROBE_BLOCKED;
	VERBOSE(tracee, 1, "AF_NETLINK %s denied by host (%s); enabling "
			   "AF_UNIX fallback for sandbox helpers",
		blocked_op, strerror(saved_errno));
	return true;
}

static bool is_fake_netlink_fd(const Tracee *tracee, int fd)
{
	int i;
	if (fd < 0)
		return false;
	for (i = 0; i < tracee->fake_netlink_fds_count; i++)
		if (tracee->fake_netlink_fds[i] == fd)
			return true;
	return false;
}

static void unmark_fake_netlink_fd(Tracee *tracee, int fd)
{
	int i;
	for (i = 0; i < tracee->fake_netlink_fds_count; i++) {
		if (tracee->fake_netlink_fds[i] == fd) {
			tracee->fake_netlink_fds[i] =
				tracee->fake_netlink_fds[--tracee->fake_netlink_fds_count];
			return;
		}
	}
}

/**
 * Append one rtattr (@type, @data/@dlen) to the netlink message being
 * built at @off in @buf and return the new offset.  Silently drops the
 * attribute (returning @off unchanged) if it would overflow @max.
 */
static size_t nl_add_attr(uint8_t *buf, size_t off, size_t max,
			  uint16_t type, const void *data, uint16_t dlen)
{
	struct rtattr *rta;
	size_t space = RTA_SPACE(dlen);

	if (off + space > max)
		return off;

	rta = (struct rtattr *) (buf + off);
	rta->rta_len  = RTA_LENGTH(dlen);
	rta->rta_type = type;
	if (dlen > 0)
		memcpy((char *) rta + RTA_LENGTH(0), data, dlen);
	if (space > RTA_LENGTH(dlen))
		memset(buf + off + RTA_LENGTH(dlen), 0, space - RTA_LENGTH(dlen));
	return off + space;
}

/**
 * Append an RTM_NEWLINK message describing one interface (@ifindex,
 * @iftype = ARPHRD_*, @ifflags, @mtu, @name, @hwaddr/@hwlen), so that
 * iproute2 / glibc see a sane, well-formed link.
 */
static size_t nl_build_link(uint8_t *buf, size_t off, size_t max,
			    uint32_t seq, uint32_t pid, uint16_t nlflags,
			    int ifindex, uint16_t iftype, uint32_t ifflags,
			    uint32_t mtu, const char *name,
			    const uint8_t *hwaddr, uint8_t hwlen)
{
	size_t start = off;
	struct nlmsghdr *nlh;
	struct ifinfomsg ifi;
	uint32_t txqlen    = 1000;
	uint8_t  operstate = (ifflags & IFF_UP) ? 6 : 2;  /* IF_OPER_UP : _DOWN */
	uint8_t  brd[8];
	size_t len;

	if (start + NLMSG_HDRLEN + NLMSG_ALIGN(sizeof(ifi)) > max)
		return start;

	memset(&ifi, 0, sizeof(ifi));
	ifi.ifi_family = AF_UNSPEC;
	ifi.ifi_type   = iftype;
	ifi.ifi_index  = ifindex;
	ifi.ifi_flags  = ifflags | ((ifflags & IFF_RUNNING) ? IFF_LOWER_UP : 0);
	ifi.ifi_change = 0;

	off = start + NLMSG_HDRLEN;
	memcpy(buf + off, &ifi, sizeof(ifi));
	off += NLMSG_ALIGN(sizeof(ifi));

	off = nl_add_attr(buf, off, max, IFLA_IFNAME, name, strlen(name) + 1);
	off = nl_add_attr(buf, off, max, IFLA_MTU, &mtu, sizeof(mtu));
	off = nl_add_attr(buf, off, max, IFLA_TXQLEN, &txqlen, sizeof(txqlen));
	off = nl_add_attr(buf, off, max, IFLA_OPERSTATE, &operstate, sizeof(operstate));
	if (hwlen > 0) {
		memset(brd, (iftype == ARPHRD_LOOPBACK) ? 0x00 : 0xff, sizeof(brd));
		off = nl_add_attr(buf, off, max, IFLA_ADDRESS, hwaddr, hwlen);
		off = nl_add_attr(buf, off, max, IFLA_BROADCAST, brd, hwlen);
	}

	len = off - start;
	nlh = (struct nlmsghdr *) (buf + start);
	nlh->nlmsg_len   = len;
	nlh->nlmsg_type  = RTM_NEWLINK;
	nlh->nlmsg_flags = nlflags;
	nlh->nlmsg_seq   = seq;
	nlh->nlmsg_pid   = pid;
	return start + NLMSG_ALIGN(len);
}

/**
 * Append an RTM_NEWADDR message for one address (@family, @addr/@addrlen,
 * @prefixlen, @scope) on interface @ifindex, labelled @label (IPv4 only;
 * may be NULL).
 */
static size_t nl_build_addr(uint8_t *buf, size_t off, size_t max,
			    uint32_t seq, uint32_t pid, uint16_t nlflags,
			    int family, int ifindex,
			    const uint8_t *addr, uint8_t addrlen,
			    uint8_t prefixlen, uint8_t scope, const char *label)
{
	size_t start = off;
	struct nlmsghdr *nlh;
	struct ifaddrmsg ifa;
	size_t len;

	if (start + NLMSG_HDRLEN + NLMSG_ALIGN(sizeof(ifa)) > max)
		return start;

	memset(&ifa, 0, sizeof(ifa));
	ifa.ifa_family    = family;
	ifa.ifa_prefixlen = prefixlen;
	ifa.ifa_flags     = IFA_F_PERMANENT;
	ifa.ifa_scope     = scope;
	ifa.ifa_index     = ifindex;

	off = start + NLMSG_HDRLEN;
	memcpy(buf + off, &ifa, sizeof(ifa));
	off += NLMSG_ALIGN(sizeof(ifa));

	off = nl_add_attr(buf, off, max, IFA_ADDRESS, addr, addrlen);
	off = nl_add_attr(buf, off, max, IFA_LOCAL, addr, addrlen);
	if (family == AF_INET && label != NULL)
		off = nl_add_attr(buf, off, max, IFA_LABEL, label, strlen(label) + 1);

	len = off - start;
	nlh = (struct nlmsghdr *) (buf + start);
	nlh->nlmsg_len   = len;
	nlh->nlmsg_type  = RTM_NEWADDR;
	nlh->nlmsg_flags = nlflags;
	nlh->nlmsg_seq   = seq;
	nlh->nlmsg_pid   = pid;
	return start + NLMSG_ALIGN(len);
}

/**
 * Append an NLMSG_DONE terminator (the canonical end-of-dump marker).
 */
static size_t nl_build_done(uint8_t *buf, size_t off, size_t max,
			    uint32_t seq, uint32_t pid)
{
	struct nlmsghdr *nlh;
	int32_t error = 0;
	size_t len = NLMSG_HDRLEN + sizeof(error);

	if (off + NLMSG_ALIGN(len) > max)
		return off;

	nlh = (struct nlmsghdr *) (buf + off);
	memcpy(buf + off + NLMSG_HDRLEN, &error, sizeof(error));
	nlh->nlmsg_len   = len;
	nlh->nlmsg_type  = NLMSG_DONE;
	nlh->nlmsg_flags = NLM_F_MULTI;
	nlh->nlmsg_seq   = seq;
	nlh->nlmsg_pid   = pid;
	return off + NLMSG_ALIGN(len);
}

/**
 * Append an NLMSG_ERROR reply carrying @error (0 == success ack).  An
 * error==0 ack is what bubblewrap's loopback_setup() expects for its
 * RTM_NEWADDR / RTM_NEWLINK; a negative error answers an unsupported
 * single-get (e.g. RTM_GETLINK for a non-loopback device).
 */
static size_t nl_build_error(uint8_t *buf, size_t off, size_t max,
			     uint32_t seq, uint32_t pid, int error)
{
	struct nlmsghdr *nlh;
	struct nlmsgerr err;
	size_t len = NLMSG_HDRLEN + sizeof(err);

	if (off + NLMSG_ALIGN(len) > max)
		return off;

	memset(&err, 0, sizeof(err));
	err.error = error;
	/* err.msg is the (zeroed) header of the original request; callers
	 * only inspect err.error.  */

	nlh = (struct nlmsghdr *) (buf + off);
	memcpy(buf + off + NLMSG_HDRLEN, &err, sizeof(err));
	nlh->nlmsg_len   = len;
	nlh->nlmsg_type  = NLMSG_ERROR;
	nlh->nlmsg_flags = 0;
	nlh->nlmsg_seq   = seq;
	nlh->nlmsg_pid   = pid;
	return off + NLMSG_ALIGN(len);
}

/**
 * Decide whether a single (non-dump) RTM_GETLINK request in @req refers
 * to the loopback interface: either it names "lo" via IFLA_IFNAME, or it
 * asks by ifi_index 0/1.  Used to answer real link lookups (iproute2's
 * ll_link_get, "ip addr show lo") while reporting -ENODEV for anything
 * else, which is the only interface PRoot can honestly present.
 */
static bool nl_request_is_loopback(const uint8_t *req, size_t req_len)
{
	const struct ifinfomsg *ifi;
	size_t off = NLMSG_HDRLEN;
	char name[IFNAMSIZ] = { 0 };
	bool have_name = false;
	int ifindex;

	if (req_len < off + sizeof(*ifi))
		return true;            /* no selector -> treat as loopback */

	ifi = (const struct ifinfomsg *) (req + off);
	ifindex = ifi->ifi_index;

	off += NLMSG_ALIGN(sizeof(*ifi));
	while (off + sizeof(struct rtattr) <= req_len) {
		const struct rtattr *rta = (const struct rtattr *) (req + off);
		size_t rlen = rta->rta_len;

		if (rlen < sizeof(*rta) || off + rlen > req_len)
			break;
		if (rta->rta_type == IFLA_IFNAME) {
			size_t dlen = rlen - RTA_LENGTH(0);
			size_t cpy  = dlen < sizeof(name) ? dlen : sizeof(name) - 1;
			memcpy(name, (const char *) rta + RTA_LENGTH(0), cpy);
			name[cpy] = '\0';
			have_name = true;
		}
		off += RTA_ALIGN(rlen);
	}

	if (have_name)
		return strcmp(name, "lo") == 0;
	return ifindex == 0 || ifindex == 1;
}

/**
 * Write a synthetic sockaddr_nl reply into the tracee's getsockname()
 * / getpeername() buffer pair (@addr_ptr, @size_ptr).  The kernel
 * would otherwise hand back the AF_UNIX sockaddr from our substituted
 * socket (length 2), which iproute2 rejects with "Wrong address
 * length 2".  Returns 0 on success or -errno (so the caller can
 * propagate it as the syscall's result).
 */
static int write_fake_netlink_sockname(Tracee *tracee, word_t addr_ptr,
				       word_t size_ptr)
{
	struct sockaddr_nl snl;
	uint32_t in_size;
	uint32_t out_size;

	if (size_ptr == 0)
		return -EINVAL;

	in_size = peek_uint32(tracee, size_ptr);
	if (errno != 0)
		return -errno;

	memset(&snl, 0, sizeof(snl));
	snl.nl_family = AF_NETLINK;
	snl.nl_pid    = (uint32_t) tracee->pid;

	if (addr_ptr != 0 && in_size > 0) {
		uint32_t copy = in_size < sizeof(snl) ? in_size : sizeof(snl);
		if (write_data(tracee, addr_ptr, &snl, copy) < 0)
			return -EFAULT;
	}

	/* Linux semantics: *size_ptr always reflects the real address
	 * length even when the caller's buffer was too small.  */
	out_size = sizeof(snl);
	poke_uint32(tracee, size_ptr, out_size);
	if (errno != 0)
		return -errno;

	return 0;
}

/* Prefix length (CIDR) from a contiguous network mask of @len bytes. */
static uint8_t nl_prefixlen(const uint8_t *mask, size_t len)
{
	uint8_t bits = 0;
	size_t i;

	for (i = 0; i < len; i++) {
		uint8_t b = mask[i];
		if (b == 0xff) {
			bits += 8;
			continue;
		}
		while (b & 0x80) {
			bits++;
			b <<= 1;
		}
		break;
	}
	return bits;
}

/* rtnetlink address scope for @addr (host / link / universe). */
static uint8_t nl_addr_scope(int family, const uint8_t *addr)
{
	if (family == AF_INET) {
		if (addr[0] == 127)
			return RT_SCOPE_HOST;                  /* 127/8 */
		if (addr[0] == 169 && addr[1] == 254)
			return RT_SCOPE_LINK;                  /* 169.254/16 */
		return RT_SCOPE_UNIVERSE;
	} else {
		static const uint8_t loop[16] = { 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,1 };
		if (memcmp(addr, loop, 16) == 0)
			return RT_SCOPE_HOST;                  /* ::1 */
		if (addr[0] == 0xfe && (addr[1] & 0xc0) == 0x80)
			return RT_SCOPE_LINK;                  /* fe80::/10 */
		return RT_SCOPE_UNIVERSE;
	}
}

/* The hardcoded loopback link / addresses, used as a fallback when the
 * host interfaces can't be enumerated. */
static size_t nl_build_loopback_link(uint8_t *buf, size_t off, size_t max,
				     uint32_t seq, uint32_t pid, uint16_t nlflags)
{
	static const uint8_t zero[6] = { 0 };
	return nl_build_link(buf, off, max, seq, pid, nlflags, 1, ARPHRD_LOOPBACK,
			     IFF_UP | IFF_LOOPBACK | IFF_RUNNING, 65536, "lo", zero, 6);
}

static size_t nl_build_loopback_addr(uint8_t *buf, size_t off, size_t max,
				     uint32_t seq, uint32_t pid, int family,
				     uint16_t nlflags)
{
	static const uint8_t v4[4]  = { 127, 0, 0, 1 };
	static const uint8_t v6[16] = { 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,1 };
	if (family == AF_INET6)
		return nl_build_addr(buf, off, max, seq, pid, nlflags, AF_INET6, 1,
				     v6, 16, 128, RT_SCOPE_HOST, NULL);
	return nl_build_addr(buf, off, max, seq, pid, nlflags, AF_INET, 1,
			     v4, 4, 8, RT_SCOPE_HOST, "lo");
}

/* Extract the interface a single (non-dump) RTM_GETLINK asks for: returns
 * its ifi_index and fills @name (empty if no IFLA_IFNAME was given). */
static int nl_request_link_target(const uint8_t *req, size_t req_len,
				  char name[IFNAMSIZ])
{
	const struct ifinfomsg *ifi;
	size_t off = NLMSG_HDRLEN;
	int ifindex;

	name[0] = '\0';
	if (req_len < off + sizeof(*ifi))
		return 0;
	ifi = (const struct ifinfomsg *) (req + off);
	ifindex = ifi->ifi_index;

	off += NLMSG_ALIGN(sizeof(*ifi));
	while (off + sizeof(struct rtattr) <= req_len) {
		const struct rtattr *rta = (const struct rtattr *) (req + off);
		size_t rlen = rta->rta_len;

		if (rlen < sizeof(*rta) || off + rlen > req_len)
			break;
		if (rta->rta_type == IFLA_IFNAME) {
			size_t dlen = rlen - RTA_LENGTH(0);
			size_t cpy  = dlen < IFNAMSIZ ? dlen : IFNAMSIZ - 1;
			memcpy(name, (const char *) rta + RTA_LENGTH(0), cpy);
			name[cpy] = '\0';
		}
		off += RTA_ALIGN(rlen);
	}
	return ifindex;
}

/* Build RTM_NEWLINK messages for the host's interfaces (the set
 * getifaddrs(3) exposes -- which keeps working on Android even when raw
 * AF_NETLINK is denied, see termux-ip.c).  A dump emits every interface;
 * a single get only the one matching @want_name / @want_index.  Returns
 * the new offset and stores the number of links built in @built. */
static size_t build_host_links(uint8_t *out, size_t max, uint32_t seq,
			       uint32_t pid, const char *want_name,
			       int want_index, bool dump, int *built)
{
	struct ifaddrs *ifaddr, *ifa;
	char seen[64][IFNAMSIZ];
	int seen_count = 0;
	size_t off = 0;
	int sock;

	*built = 0;
	if (getifaddrs(&ifaddr) != 0)
		return 0;
	sock = socket(AF_INET, SOCK_DGRAM | SOCK_CLOEXEC, 0);

	for (ifa = ifaddr; ifa != NULL; ifa = ifa->ifa_next) {
		uint32_t ifflags;
		uint16_t iftype;
		uint32_t mtu;
		uint8_t  hwaddr[8] = { 0 };
		uint8_t  hwlen = 0;
		int ifindex;
		int i;
		bool dup = false;

		if (ifa->ifa_name == NULL)
			continue;
		for (i = 0; i < seen_count; i++)
			if (strncmp(seen[i], ifa->ifa_name, IFNAMSIZ) == 0) {
				dup = true;
				break;
			}
		if (dup)
			continue;
		if (seen_count < 64) {
			strncpy(seen[seen_count], ifa->ifa_name, IFNAMSIZ - 1);
			seen[seen_count][IFNAMSIZ - 1] = '\0';
			seen_count++;
		}

		ifflags = ifa->ifa_flags;
		iftype  = (ifflags & IFF_LOOPBACK) ? ARPHRD_LOOPBACK : ARPHRD_ETHER;
		mtu     = (ifflags & IFF_LOOPBACK) ? 65536 : 1500;
		ifindex = (int) if_nametoindex(ifa->ifa_name);

		/* AF_PACKET entries carry the authoritative index/type/hwaddr. */
		if (ifa->ifa_addr != NULL && ifa->ifa_addr->sa_family == AF_PACKET) {
			struct sockaddr_ll *sll = (struct sockaddr_ll *) ifa->ifa_addr;
			if (sll->sll_ifindex != 0)
				ifindex = sll->sll_ifindex;
			iftype = sll->sll_hatype;
			if (sll->sll_halen > 0 && sll->sll_halen <= sizeof(hwaddr)) {
				memcpy(hwaddr, sll->sll_addr, sll->sll_halen);
				hwlen = sll->sll_halen;
			}
		}

		/* Best-effort MTU, and hwaddr/type when no AF_PACKET entry. */
		if (sock >= 0) {
			struct ifreq ifr;

			memset(&ifr, 0, sizeof(ifr));
			strncpy(ifr.ifr_name, ifa->ifa_name, IFNAMSIZ - 1);
			if (ioctl(sock, SIOCGIFMTU, &ifr) == 0)
				mtu = ifr.ifr_mtu;
			if (hwlen == 0) {
				memset(&ifr, 0, sizeof(ifr));
				strncpy(ifr.ifr_name, ifa->ifa_name, IFNAMSIZ - 1);
				if (ioctl(sock, SIOCGIFHWADDR, &ifr) == 0) {
					iftype = ifr.ifr_hwaddr.sa_family;
					memcpy(hwaddr, ifr.ifr_hwaddr.sa_data, 6);
					hwlen = 6;
				}
			}
		}

		if (!dump) {
			if (want_name != NULL && want_name[0] != '\0') {
				if (strcmp(want_name, ifa->ifa_name) != 0)
					continue;
			} else if (want_index > 0 && ifindex != want_index) {
				continue;
			}
		}

		if (off + 256 > max)
			break;
		off = nl_build_link(out, off, max, seq, pid,
				    dump ? NLM_F_MULTI : 0, ifindex, iftype,
				    ifflags, mtu, ifa->ifa_name, hwaddr, hwlen);
		(*built)++;
		if (!dump)
			break;
	}

	if (sock >= 0)
		close(sock);
	freeifaddrs(ifaddr);
	return off;
}

/* Build RTM_NEWADDR messages for the host's addresses (optionally
 * filtered to @want_family).  Returns the new offset; @built gets the
 * number of addresses emitted. */
static size_t build_host_addrs(uint8_t *out, size_t max, uint32_t seq,
			       uint32_t pid, int want_family, bool dump,
			       int *built)
{
	struct ifaddrs *ifaddr, *ifa;
	size_t off = 0;

	*built = 0;
	if (getifaddrs(&ifaddr) != 0)
		return 0;

	for (ifa = ifaddr; ifa != NULL; ifa = ifa->ifa_next) {
		const uint8_t *addr;
		const uint8_t *mask = NULL;
		uint8_t addrlen, masklen = 0;
		uint8_t prefixlen, scope;
		int family, ifindex;

		if (ifa->ifa_name == NULL || ifa->ifa_addr == NULL)
			continue;
		family = ifa->ifa_addr->sa_family;
		if (family != AF_INET && family != AF_INET6)
			continue;
		if (want_family != AF_UNSPEC && family != want_family)
			continue;

		if (family == AF_INET) {
			addr = (const uint8_t *) &((struct sockaddr_in *) ifa->ifa_addr)->sin_addr;
			addrlen = 4;
			if (ifa->ifa_netmask != NULL) {
				mask = (const uint8_t *) &((struct sockaddr_in *) ifa->ifa_netmask)->sin_addr;
				masklen = 4;
			}
		} else {
			addr = (const uint8_t *) &((struct sockaddr_in6 *) ifa->ifa_addr)->sin6_addr;
			addrlen = 16;
			if (ifa->ifa_netmask != NULL) {
				mask = (const uint8_t *) &((struct sockaddr_in6 *) ifa->ifa_netmask)->sin6_addr;
				masklen = 16;
			}
		}

		prefixlen = (mask != NULL) ? nl_prefixlen(mask, masklen)
					   : (family == AF_INET ? 32 : 128);
		scope = nl_addr_scope(family, addr);
		ifindex = (int) if_nametoindex(ifa->ifa_name);

		if (off + 256 > max)
			break;
		off = nl_build_addr(out, off, max, seq, pid,
				    dump ? NLM_F_MULTI : 0, family, ifindex,
				    addr, addrlen, prefixlen, scope, ifa->ifa_name);
		(*built)++;
	}

	freeifaddrs(ifaddr);
	return off;
}

/**
 * Relay a routing-table dump (RTM_GETROUTE) from the host kernel.  The
 * Android builds that deny *binding* an AF_NETLINK socket -- which is why
 * we emulate it in the first place -- still let an unbound socket issue a
 * dump, the same trick termux-ip.c uses for "ip route".  We run that dump
 * from PRoot and copy the kernel's RTM_NEWROUTE messages back, rewriting
 * nlmsg_seq / nlmsg_pid so the tracee's iproute2 accepts them.  Returns 0
 * (caller then falls back to an empty NLMSG_DONE, i.e. the previous
 * behaviour) whenever the host won't cooperate, so nothing regresses. */
static size_t relay_route_dump(const uint8_t *req, size_t req_len,
			       uint8_t *out, size_t max,
			       uint32_t seq, uint32_t pid)
{
	struct {
		struct nlmsghdr nlh;
		struct rtmsg    rtm;
	} dreq;
	struct sockaddr_nl sa;
	struct timeval tv = { .tv_sec = 1, .tv_usec = 0 };
	uint8_t family = (req_len > NLMSG_HDRLEN) ? req[NLMSG_HDRLEN] : 0;
	size_t off = 0;
	bool done = false;
	bool saw_done = false;
	int fd;
	int rounds;

	fd = socket(AF_NETLINK, SOCK_RAW | SOCK_CLOEXEC, NETLINK_ROUTE);
	if (fd < 0)
		return 0;
	(void) setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));

	memset(&dreq, 0, sizeof(dreq));
	dreq.nlh.nlmsg_len   = NLMSG_LENGTH(sizeof(struct rtmsg));
	dreq.nlh.nlmsg_type  = RTM_GETROUTE;
	dreq.nlh.nlmsg_flags = NLM_F_REQUEST | NLM_F_DUMP;
	dreq.nlh.nlmsg_seq   = seq;
	dreq.rtm.rtm_family  = family;

	memset(&sa, 0, sizeof(sa));
	sa.nl_family = AF_NETLINK;
	if (sendto(fd, &dreq, dreq.nlh.nlmsg_len, 0,
		   (struct sockaddr *) &sa, sizeof(sa)) < 0) {
		close(fd);
		return 0;
	}

	for (rounds = 0; !done && rounds < 64; rounds++) {
		uint8_t buf[8192] __attribute__((aligned(8)));
		struct nlmsghdr *h;
		ssize_t n;
		size_t len;

		n = recv(fd, buf, sizeof(buf), 0);
		if (n <= 0)
			break;
		len = (size_t) n;
		for (h = (struct nlmsghdr *) buf; NLMSG_OK(h, len);
		     h = NLMSG_NEXT(h, len)) {
			size_t mlen = h->nlmsg_len;
			size_t aligned = NLMSG_ALIGN(mlen);

			/* Keep 64 bytes spare so the NLMSG_DONE terminator
			 * below always fits, even on a truncated dump. */
			if (off + aligned + 64 > max) {
				done = true;
				break;
			}
			h->nlmsg_seq = seq;
			h->nlmsg_pid = pid;
			memcpy(out + off, h, mlen);
			if (aligned > mlen)
				memset(out + off + mlen, 0, aligned - mlen);
			off += aligned;
			if (h->nlmsg_type == NLMSG_DONE) {
				saw_done = true;
				done = true;
				break;
			}
		}
	}

	close(fd);

	if (off == 0)
		return 0;
	/* Always hand the tracee a terminator: if the kernel's own
	 * NLMSG_DONE didn't make it (timeout, truncation), append one so
	 * iproute2 doesn't read past the reply and report "EOF on netlink". */
	if (!saw_done)
		off = nl_build_done(out, off, max, seq, pid);
	return off;
}

/**
 * Parse the netlink request the tracee just sent (whether via sendto
 * with a flat buffer or via sendmsg with an iovec) and build the reply
 * the kernel would have produced into tracee->fake_netlink_reply, so a
 * later recvmsg / recvfrom on the same fake netlink fd can hand it back.
 *
 * RTM_GETLINK / RTM_GETADDR are answered with the *real* host interfaces
 * (enumerated via getifaddrs, which keeps working under Android's netlink
 * ban -- see termux-ip.c), falling back to a synthetic loopback when none
 * can be gathered.  Other dumps get an empty NLMSG_DONE; everything else
 * (bubblewrap's RTM_NEWADDR / RTM_NEWLINK and friends) gets an error==0
 * ack.  Replies carry the request's nlmsg_seq and the tracee's pid in
 * nlmsg_pid, which iproute2 / bubblewrap match against before accepting.
 */
static void build_fake_netlink_reply(Tracee *tracee, word_t buf_addr,
				     word_t buf_len)
{
	uint8_t req[256] __attribute__((aligned(8)));
	size_t  req_len;
	struct nlmsghdr hdr;
	uint8_t *out = tracee->fake_netlink_reply;
	size_t   max = sizeof(tracee->fake_netlink_reply);
	uint32_t pid = (uint32_t) tracee->pid;
	uint32_t seq;
	uint16_t type, flags;
	bool dump;
	size_t off = 0;

	tracee->fake_netlink_reply_len = 0;

	if (buf_addr == 0 || buf_len < sizeof(hdr))
		return;
	req_len = buf_len < sizeof(req) ? buf_len : sizeof(req);
	if (read_data(tracee, req, buf_addr, req_len) < 0)
		return;

	memcpy(&hdr, req, sizeof(hdr));
	type  = hdr.nlmsg_type;
	flags = hdr.nlmsg_flags;
	seq   = hdr.nlmsg_seq;
	dump  = (flags & NLM_F_DUMP) == NLM_F_DUMP;

	switch (type) {
	case RTM_GETLINK: {
		char want_name[IFNAMSIZ];
		int want_index = nl_request_link_target(req, req_len, want_name);
		int n = 0;

		off = build_host_links(out, max, seq, pid,
				       dump ? NULL : want_name,
				       dump ? 0 : want_index, dump, &n);
		if (n == 0) {
			/* Host enumeration unavailable: present loopback only. */
			off = 0;
			if (dump)
				off = nl_build_loopback_link(out, off, max, seq, pid, NLM_F_MULTI);
			else if (nl_request_is_loopback(req, req_len))
				off = nl_build_loopback_link(out, off, max, seq, pid, 0);
			else
				off = nl_build_error(out, off, max, seq, pid, -ENODEV);
		}
		if (dump)
			off = nl_build_done(out, off, max, seq, pid);
		break;
	}

	case RTM_GETADDR: {
		uint8_t family = (req_len > NLMSG_HDRLEN) ? req[NLMSG_HDRLEN] : 0;
		int want_family = (family == AF_INET || family == AF_INET6)
				  ? family : AF_UNSPEC;
		int n = 0;

		off = build_host_addrs(out, max, seq, pid, want_family, dump, &n);
		if (n == 0) {
			/* Host enumeration unavailable: present loopback only. */
			off = 0;
			if (family == 0 || family == AF_INET)
				off = nl_build_loopback_addr(out, off, max, seq, pid,
							     AF_INET, dump ? NLM_F_MULTI : 0);
			if (family == 0 || family == AF_INET6)
				off = nl_build_loopback_addr(out, off, max, seq, pid,
							     AF_INET6, dump ? NLM_F_MULTI : 0);
		}
		if (dump)
			off = nl_build_done(out, off, max, seq, pid);
		break;
	}

	case RTM_GETROUTE:
		if (dump) {
			off = relay_route_dump(req, req_len, out, max, seq, pid);
			if (off == 0)
				off = nl_build_done(out, off, max, seq, pid);
		} else {
			off = nl_build_error(out, off, max, seq, pid, 0);
		}
		break;

	default:
		if (dump)
			off = nl_build_done(out, off, max, seq, pid);
		else
			off = nl_build_error(out, off, max, seq, pid, 0);
		break;
	}

	tracee->fake_netlink_reply_len = off;
}

/**
 * Copy the pending fake netlink reply into the tracee's recvmsg iovec
 * array (@iov_ptr, @iov_count), walking segments until the reply is
 * exhausted.  Returns the number of bytes actually scattered (which may
 * be less than the reply when the caller's buffers are too small).
 */
static size_t scatter_fake_netlink_reply(Tracee *tracee, word_t iov_ptr,
					 word_t iov_count)
{
	size_t reply_len = tracee->fake_netlink_reply_len;
	size_t w = sizeof_word(tracee);
	size_t done = 0;
	word_t i;

	for (i = 0; i < iov_count && done < reply_len; i++) {
		word_t base = peek_word(tracee, iov_ptr + i * 2 * w);
		word_t len  = (errno == 0) ? peek_word(tracee, iov_ptr + i * 2 * w + w) : 0;
		size_t chunk;

		errno = 0;
		chunk = reply_len - done;
		if (chunk > len)
			chunk = len;
		if (base != 0 && chunk > 0) {
			if (write_data(tracee, base,
				       tracee->fake_netlink_reply + done, chunk) < 0)
				break;
		}
		done += chunk;
	}

	return done;
}

/**
 * If @cmd is SIOCGIFINDEX, answer it from the host's own interface
 * table instead of letting the tracee's ioctl reach the kernel.
 *
 * Android denies this ioctl (EACCES) on the AF_UNIX/AF_INET socket
 * glibc opens for if_nametoindex() for every device except loopback,
 * when the caller lacks CAP_NET_ADMIN.  That breaks more than
 * bubblewrap's loopback_setup(): ifaddr.get_adapters() leaves
 * Adapter.index == None for each real interface, and python-zeroconf's
 * ip6_to_address_and_index() then refuses to match an address to its
 * adapter ("No adapter found for IP address fe80::...").
 *
 * We resolve the name with if_nametoindex() in the tracer, which keeps
 * working under Android's netlink restrictions — the same way
 * build_host_addrs() already fills each address's ifa_index — and write
 * the real index back.  Loopback is index 1 on every Linux kernel, so
 * answer it even if the host enumeration somehow fails.  Returns false
 * for an unknown name, leaving the real ioctl to run (the previous
 * behaviour, so nothing regresses).
 *
 * Only touch the ifr_name (read) and ifr_ifindex (write) fields,
 * both at fixed offsets — sizeof(struct ifreq) differs between
 * 32- and 64-bit ABIs (the trailing union contains pointer-sized
 * members), and reading/writing the whole struct from PRoot would
 * overrun the tracee's buffer when the two ABIs disagree.
 */
static bool maybe_fake_siocgifindex(Tracee *tracee, word_t cmd, word_t arg)
{
	char name[IFNAMSIZ];
	int ifindex;

	if (cmd != SIOCGIFINDEX)
		return false;
	if (arg == 0)
		return false;
	if (read_data(tracee, name, arg, sizeof(name)) < 0)
		return false;
	name[IFNAMSIZ - 1] = '\0';

	ifindex = (int) if_nametoindex(name);
	if (ifindex <= 0) {
		if (strcmp(name, "lo") != 0)
			return false;
		ifindex = 1;
	}

	if (write_data(tracee, arg + IFNAMSIZ, &ifindex, sizeof(ifindex)) < 0)
		return false;
	return true;
}

/**
 * Detect /proc/<pid|self>/{uid_map,gid_map,setgroups}, which sandbox
 * helpers like bubblewrap write to during user-namespace setup.  The
 * tracee cannot really create namespaces under PRoot, so silently
 * redirect those writes to /dev/null.
 */
static bool is_proc_userns_file(const char *path)
{
	const char *p;
	const char *suffix;

	if (strncmp(path, "/proc/", 6) != 0)
		return false;
	p = path + 6;

	if (strncmp(p, "self/", 5) == 0)
		p += 5;
	else {
		const char *digits = p;
		while (*p >= '0' && *p <= '9')
			p++;
		if (p == digits || *p != '/')
			return false;
		p++;
	}

	suffix = p;
	return strcmp(suffix, "uid_map") == 0
	    || strcmp(suffix, "gid_map") == 0
	    || strcmp(suffix, "setgroups") == 0;
}

/**
 * Redirect openat()/open() of /proc/.../uid_map etc. to /dev/null so
 * that writes appear to succeed.  @reg holds the path argument; the
 * path has already been translated to host form.
 */
static void maybe_redirect_userns_file(Tracee *tracee, Reg reg)
{
	char host_path[PATH_MAX];

	if (get_sysarg_path(tracee, host_path, reg) < 0)
		return;
	if (!is_proc_userns_file(host_path))
		return;
	(void) set_sysarg_path(tracee, "/dev/null", reg);
}

/**
 * Translate the input arguments of the current @tracee's syscall in the
 * @tracee->pid process area. This function sets @tracee->status to
 * -errno if an error occured from the tracee's point-of-view (EFAULT
 * for instance), otherwise 0.
 */
int translate_syscall_enter(Tracee *tracee)
{
	int flags;
	int dirfd;
	int olddirfd;
	int newdirfd;

	int status;
	int status2;

	char path[PATH_MAX];
	char oldpath[PATH_MAX];
	char newpath[PATH_MAX];

	word_t syscall_number;
	bool special = false;

	status = notify_extensions(tracee, SYSCALL_ENTER_START, 0, 0);
	if (status < 0)
		goto end;
	if (status > 0)
		return 0;

	/* Translate input arguments. */
	syscall_number = get_sysnum(tracee, ORIGINAL);
	switch (syscall_number) {
	default:
		/* Nothing to do. */
		status = 0;
		break;

	case PR_execve:
		status = translate_execve_enter(tracee);
		break;

	case PR_execveat:
		if ((int) peek_reg(tracee, CURRENT, SYSARG_1) == AT_FDCWD) {
			set_sysnum(tracee, PR_execve);
			poke_reg(tracee, SYSARG_1, peek_reg(tracee, CURRENT, SYSARG_2));
			poke_reg(tracee, SYSARG_2, peek_reg(tracee, CURRENT, SYSARG_3));
			poke_reg(tracee, SYSARG_3, peek_reg(tracee, CURRENT, SYSARG_4));
		} else {
			note(tracee, ERROR, SYSTEM, "execveat() with non-AT_FDCWD fd is not currently supported");
			status = -ENOSYS;
			break;
		}
		status = translate_execve_enter(tracee);
		break;

	case PR_ptrace:
		status = translate_ptrace_enter(tracee);
		break;

	case PR_wait4:
	case PR_waitpid:
		status = translate_wait_enter(tracee);
		break;

	case PR_brk:
		translate_brk_enter(tracee);
		status = 0;
		break;

	case PR_getcwd:
		poke_reg(tracee, SYSARG_RESULT, 0);
		set_sysnum(tracee, PR_void);
		status = 0;
		break;

	case PR_fchdir:
	case PR_chdir: {
		struct stat statl;
		char *tmp;

		/* The ending "." ensures an error will be reported if
		 * path does not exist or if it is not a directory.  */
		if (syscall_number == PR_chdir) {
			status = get_sysarg_path(tracee, path, SYSARG_1);
			if (status < 0)
				break;

			status = join_paths(2, oldpath, path, ".");
			if (status < 0)
				break;

			dirfd = AT_FDCWD;
		}
		else {
			strcpy(oldpath, ".");
			dirfd = peek_reg(tracee, CURRENT, SYSARG_1);
		}

		status = translate_path(tracee, path, dirfd, oldpath, true);
		if (status < 0)
			break;

		status = lstat(path, &statl);
		if (status < 0)
			break;

		/* Check this directory is accessible.  */
		if ((statl.st_mode & S_IXUSR) == 0)
			return -EACCES;

		/* Sadly this method doesn't detranslate statefully,
		 * this means that there's an ambiguity when several
		 * bindings are from the same host path:
		 *
		 *    $ proot -m /tmp:/a -m /tmp:/b fchdir_getcwd /a
		 *    /b
		 *
		 *    $ proot -m /tmp:/b -m /tmp:/a fchdir_getcwd /a
		 *    /a
		 *
		 * A solution would be to follow each file descriptor
		 * just like it is done for cwd.
		 */

		status = detranslate_path(tracee, path, NULL);
		if (status < 0)
			break;

		/* Remove the trailing "/" or "/.".  */
		chop_finality(path);

		tmp = talloc_strdup(tracee->fs, path);
		if (tmp == NULL) {
			status = -ENOMEM;
			break;
		}
		TALLOC_FREE(tracee->fs->cwd);

		tracee->fs->cwd = tmp;
		talloc_set_name_const(tracee->fs->cwd, "$cwd");

		poke_reg(tracee, SYSARG_RESULT, 0);
		set_sysnum(tracee, PR_void);
		status = 0;
		break;
	}

	case PR_bind:
	case PR_connect: {
		word_t address;
		word_t size;

		/* If we already redirected this fd to AF_UNIX as part
		 * of the AF_NETLINK emulation, fail the bind silently
		 * (the kernel would otherwise refuse our sockaddr_nl). */
		if (syscall_number == PR_bind
		    && is_fake_netlink_fd(tracee, peek_reg(tracee, CURRENT, SYSARG_1))) {
			poke_reg(tracee, SYSARG_RESULT, 0);
			set_sysnum(tracee, PR_void);
			status = 0;
			break;
		}

		address = peek_reg(tracee, CURRENT, SYSARG_2);
		size    = peek_reg(tracee, CURRENT, SYSARG_3);

		status = translate_socketcall_enter(tracee, &address, size);
		if (status <= 0)
			break;

		poke_reg(tracee, SYSARG_2, address);
		poke_reg(tracee, SYSARG_3, sizeof(struct sockaddr_un));

		status = 0;
		break;
	}

#define SYSARG_ADDR(n) (args_addr + ((n) - 1) * sizeof_word(tracee))

#define PEEK_WORD(addr, forced_errno)		\
	peek_word(tracee, addr);		\
	if (errno != 0) {			\
		status = forced_errno ?: -errno; \
		break;				\
	}

#define POKE_WORD(addr, value)			\
	poke_word(tracee, addr, value);		\
	if (errno != 0) {			\
		status = -errno;		\
		break;				\
	}

	case PR_accept:
	case PR_accept4:
		/* Nothing special to do if no sockaddr was specified.  */
		if (peek_reg(tracee, ORIGINAL, SYSARG_2) == 0) {
			status = 0;
			break;
		}
		special = true;
		/* Fall through.  */
	case PR_getsockname:
	case PR_getpeername:{
		int size;

		/* For an fd we substituted to AF_UNIX as part of the
		 * AF_NETLINK emulation, hand back a synthetic sockaddr_nl
		 * so callers like iproute2 don't error out with "Wrong
		 * address length 2" on the AF_UNIX sockname.  */
		if ((syscall_number == PR_getsockname || syscall_number == PR_getpeername)
		    && is_fake_netlink_fd(tracee, peek_reg(tracee, CURRENT, SYSARG_1))) {
			word_t addr_ptr = peek_reg(tracee, CURRENT, SYSARG_2);
			word_t size_ptr = peek_reg(tracee, CURRENT, SYSARG_3);
			int    rc = write_fake_netlink_sockname(tracee, addr_ptr, size_ptr);

			poke_reg(tracee, SYSARG_RESULT, (word_t) rc);
			set_sysnum(tracee, PR_void);
			status = 0;
			break;
		}

		/* Remember: PEEK_WORD puts -errno in status and breaks if an
		 * error occured.  */
		size = (int) PEEK_WORD(peek_reg(tracee, ORIGINAL, SYSARG_3), special ? -EINVAL : 0);

		/* The "size" argument is both used as an input parameter
		 * (max. size) and as an output parameter (actual size).  The
		 * exit stage needs to know the max. size to not overwrite
		 * anything, that's why it is copied in the 6th argument
		 * (unused) before the kernel updates it.  */
		poke_reg(tracee, SYSARG_6, size);

		status = 0;
		break;
	}

	/* Substitute an AF_UNIX/SOCK_DGRAM socket for AF_NETLINK
	 * requests so the kernel doesn't reject them with EACCES on
	 * Android, then track the resulting fd so bind/sendto/recvfrom
	 * on it can be faked too.  Only NETLINK_ROUTE is emulated (it's
	 * what bubblewrap's loopback_setup needs); pass other netlink
	 * protocols (NETLINK_AUDIT, NETLINK_KOBJECT_UEVENT, ...) through
	 * untouched so the tracee gets the real kernel error / behavior
	 * for them.  */
	case PR_socket: {
		word_t domain = peek_reg(tracee, CURRENT, SYSARG_1);
		word_t protocol = peek_reg(tracee, CURRENT, SYSARG_3);
		if (   domain == AF_NETLINK
		    && protocol == NETLINK_ROUTE
		    && host_blocks_af_netlink(tracee)) {
			word_t type = peek_reg(tracee, CURRENT, SYSARG_2);
			poke_reg(tracee, SYSARG_1, AF_UNIX);
			poke_reg(tracee, SYSARG_2, SOCK_DGRAM | (type & SOCK_CLOEXEC));
			poke_reg(tracee, SYSARG_3, 0);
			tracee->pending_fake_netlink_socket = true;
			tracee->sysexit_pending = true;
			tracee->restart_how = PTRACE_SYSCALL;
		}
		status = 0;
		break;
	}

	case PR_sendto: {
		int fd = peek_reg(tracee, CURRENT, SYSARG_1);
		if (is_fake_netlink_fd(tracee, fd)) {
			word_t buf = peek_reg(tracee, CURRENT, SYSARG_2);
			word_t len = peek_reg(tracee, CURRENT, SYSARG_3);

			build_fake_netlink_reply(tracee, buf, len);

			poke_reg(tracee, SYSARG_RESULT, len);
			set_sysnum(tracee, PR_void);
			status = 0;
			break;
		}
		status = 0;
		break;
	}

	case PR_sendmsg: {
		int fd = peek_reg(tracee, CURRENT, SYSARG_1);
		if (is_fake_netlink_fd(tracee, fd)) {
			word_t msghdr_addr = peek_reg(tracee, CURRENT, SYSARG_2);
			size_t w = sizeof_word(tracee);
			word_t total = 0;
			word_t iov_ptr, iov_count;

			/* struct msghdr layout (Linux, both ABIs):
			 *   word  msg_name
			 *   u32   msg_namelen  (followed by pad to word align)
			 *   word  msg_iov
			 *   word  msg_iovlen
			 *   ...
			 */
			if (msghdr_addr != 0) {
				iov_ptr   = peek_word(tracee, msghdr_addr + 2 * w);
				iov_count = (errno == 0)
					    ? peek_word(tracee, msghdr_addr + 3 * w)
					    : 0;
				errno = 0;

				if (iov_ptr != 0 && iov_count > 0) {
					word_t base = peek_word(tracee, iov_ptr);
					word_t len  = (errno == 0)
						      ? peek_word(tracee, iov_ptr + w)
						      : 0;
					errno = 0;

					build_fake_netlink_reply(tracee, base, len);
					/* Use the first iovec's length as the
					 * pretended bytes-sent.  Multi-iovec
					 * netlink requests are unheard of for
					 * the bwrap / glibc / iproute2 callers
					 * we care about.  */
					total = len;
				}
			}

			poke_reg(tracee, SYSARG_RESULT, total);
			set_sysnum(tracee, PR_void);
			status = 0;
			break;
		}
		status = 0;
		break;
	}

	case PR_recvfrom: {
		int fd = peek_reg(tracee, CURRENT, SYSARG_1);
		if (is_fake_netlink_fd(tracee, fd)) {
			word_t buf       = peek_reg(tracee, CURRENT, SYSARG_2);
			word_t len       = peek_reg(tracee, CURRENT, SYSARG_3);
			int    flags     = (int) peek_reg(tracee, CURRENT, SYSARG_4);
			word_t addr_ptr  = peek_reg(tracee, CURRENT, SYSARG_5);
			word_t size_ptr  = peek_reg(tracee, CURRENT, SYSARG_6);
			size_t reply_len = tracee->fake_netlink_reply_len;
			size_t copied    = 0;
			size_t result;

			if (reply_len > 0 && buf != 0) {
				copied = len < reply_len ? len : reply_len;
				if (copied > 0 &&
				    write_data(tracee, buf,
					       tracee->fake_netlink_reply, copied) < 0)
					copied = 0;
			}

			/* MSG_PEEK leaves the reply pending for the real read
			 * that follows; MSG_TRUNC asks for the untruncated
			 * length (the libnetlink size-probe pattern).  */
			if (!(flags & MSG_PEEK))
				tracee->fake_netlink_reply_len = 0;
			result = (flags & MSG_TRUNC) ? reply_len : copied;

			/* Hand back a kernel sockaddr_nl (nl_pid == 0) source
			 * rather than the AF_UNIX address of our substitute.  */
			if (addr_ptr != 0 && size_ptr != 0)
				(void) write_fake_netlink_sockname(tracee, addr_ptr,
								   size_ptr);
			errno = 0;

			poke_reg(tracee, SYSARG_RESULT, (word_t) result);
			set_sysnum(tracee, PR_void);
			status = 0;
			break;
		}
		status = 0;
		break;
	}

	case PR_recvmsg: {
		int fd = peek_reg(tracee, CURRENT, SYSARG_1);
		if (is_fake_netlink_fd(tracee, fd)) {
			word_t msghdr_addr = peek_reg(tracee, CURRENT, SYSARG_2);
			int    flags = (int) peek_reg(tracee, CURRENT, SYSARG_3);
			size_t w = sizeof_word(tracee);
			word_t msg_name = 0;
			word_t iov_ptr = 0, iov_count = 0;
			size_t reply_len = tracee->fake_netlink_reply_len;
			size_t scattered = 0;
			size_t result;

			if (msghdr_addr != 0) {
				msg_name  = peek_word(tracee, msghdr_addr);
				if (errno != 0) { errno = 0; msg_name = 0; }
				iov_ptr   = peek_word(tracee, msghdr_addr + 2 * w);
				if (errno != 0) { errno = 0; iov_ptr   = 0; }
				iov_count = peek_word(tracee, msghdr_addr + 3 * w);
				if (errno != 0) { errno = 0; iov_count = 0; }
			}

			if (iov_ptr != 0 && iov_count > 0)
				scattered = scatter_fake_netlink_reply(tracee, iov_ptr,
								       iov_count);

			/* MSG_PEEK leaves the reply pending for the real read
			 * that follows; MSG_TRUNC asks for the untruncated
			 * length (iproute2's libnetlink size-probe pattern).  */
			if (!(flags & MSG_PEEK))
				tracee->fake_netlink_reply_len = 0;
			result = (flags & MSG_TRUNC) ? reply_len : scattered;

			/* glibc's getifaddrs() and friends inspect the
			 * source address: hand them a sockaddr_nl from the
			 * kernel (nl_pid == 0) rather than the AF_UNIX
			 * "address family 1" that a real recvmsg on our
			 * substituted socket would produce.  */
			if (msg_name != 0 && msghdr_addr != 0) {
				struct sockaddr_nl snl;
				uint32_t in_namelen = peek_uint32(tracee, msghdr_addr + w);
				if (errno == 0 && in_namelen > 0) {
					uint32_t copy = in_namelen < sizeof(snl)
							? in_namelen
							: sizeof(snl);
					memset(&snl, 0, sizeof(snl));
					snl.nl_family = AF_NETLINK;
					(void) write_data(tracee, msg_name, &snl, copy);
					poke_uint32(tracee, msghdr_addr + w,
						    (uint32_t) sizeof(snl));
				}
				errno = 0;
			}

			/* msg_flags (offset 6 words in struct msghdr, both
			 * ABIs): report MSG_TRUNC iff the caller's buffers
			 * couldn't hold the whole reply, like the kernel.  */
			if (msghdr_addr != 0) {
				poke_uint32(tracee, msghdr_addr + 6 * w,
					    scattered < reply_len ? MSG_TRUNC : 0);
				errno = 0;
			}

			poke_reg(tracee, SYSARG_RESULT, (word_t) result);
			set_sysnum(tracee, PR_void);
			status = 0;
			break;
		}
		status = 0;
		break;
	}

	case PR_socketcall: {
		word_t args_addr;
		word_t sock_addr_saved;
		word_t sock_addr;
		word_t size_addr;
		word_t size;

		args_addr = peek_reg(tracee, CURRENT, SYSARG_2);

		switch (peek_reg(tracee, CURRENT, SYSARG_1)) {
		case SYS_BIND:
		case SYS_CONNECT:
			/* Handle these cases below.  */
			status = 1;
			break;

		case SYS_ACCEPT:
		case SYS_ACCEPT4:
			/* Nothing special to do if no sockaddr was specified.  */
			sock_addr = PEEK_WORD(SYSARG_ADDR(2), 0);
			if (sock_addr == 0) {
				status = 0;
				break;
			}
			special = true;
			/* Fall through.  */
		case SYS_GETSOCKNAME:
		case SYS_GETPEERNAME:
			/* Remember: PEEK_WORD puts -errno in status and breaks
			 * if an error occured.  */
			size_addr =  PEEK_WORD(SYSARG_ADDR(3), 0);
			size = (int) PEEK_WORD(size_addr, special ? -EINVAL : 0);

			/* See case PR_accept for explanation.  */
			poke_reg(tracee, SYSARG_6, size);
			status = 0;
			break;

		default:
			status = 0;
			break;
		}

		/* An error occured or there's nothing else to do.  */
		if (status <= 0)
			break;

		/* Remember: PEEK_WORD puts -errno in status and breaks if an
		 * error occured.  */
		sock_addr = PEEK_WORD(SYSARG_ADDR(2), 0);
		size      = PEEK_WORD(SYSARG_ADDR(3), 0);

		sock_addr_saved = sock_addr;
		status = translate_socketcall_enter(tracee, &sock_addr, size);
		if (status <= 0)
			break;

		/* These parameters are used/restored at the exit stage.  */
		poke_reg(tracee, SYSARG_5, sock_addr_saved);
		poke_reg(tracee, SYSARG_6, size);

		/* Remember: POKE_WORD puts -errno in status and breaks if an
		 * error occured.  */
		POKE_WORD(SYSARG_ADDR(2), sock_addr);
		POKE_WORD(SYSARG_ADDR(3), sizeof(struct sockaddr_un));

		status = 0;
		break;
	}

#undef SYSARG_ADDR
#undef PEEK_WORD
#undef POKE_WORD

	case PR_access:
	case PR_acct:
	case PR_chmod:
	case PR_chown:
	case PR_chown32:
	case PR_chroot:
	case PR_getxattr:
	case PR_listxattr:
	case PR_mknod:
	case PR_oldstat:
	case PR_creat:
	case PR_removexattr:
	case PR_setxattr:
	case PR_stat:
	case PR_stat64:
	case PR_statfs:
	case PR_statfs64:
	case PR_swapoff:
	case PR_swapon:
	case PR_truncate:
	case PR_truncate64:
	case PR_uselib:
	case PR_utime:
	case PR_utimes:
		status = translate_sysarg(tracee, SYSARG_1, REGULAR);
		break;

	/* Pretend namespace syscalls succeed without doing anything;
	 * PRoot can't really create namespaces, and sandbox helpers
	 * like bubblewrap only check the return value.  */
	case PR_unshare:
	case PR_setns:
		poke_reg(tracee, SYSARG_RESULT, 0);
		set_sysnum(tracee, PR_void);
		status = 0;
		break;

	case PR_umount:
	case PR_umount2:
		apply_emulated_umount(tracee);
		poke_reg(tracee, SYSARG_RESULT, 0);
		set_sysnum(tracee, PR_void);
		status = 0;
		break;

	/* Strip CLONE_NEW* flags from clone(2)/clone3(2) so the
	 * syscall doesn't fail with EPERM on kernels that disallow
	 * unprivileged namespace creation (typical on Android).  The
	 * fork/thread itself still proceeds normally and PRoot keeps
	 * tracking the child through PTRACE_EVENT_CLONE.  When the
	 * caller asked for CLONE_NEWNS, remember it on the tracee so
	 * the new child gets isolated bindings (otherwise emulated
	 * mount(2) calls in the child would leak into the parent).  */
	case PR_clone: {
		word_t flags = peek_reg(tracee, CURRENT, SYSARG_1);
		if ((flags & CLONE_NS_MASK) != 0) {
			if ((flags & CLONE_NEWNS) != 0)
				tracee->clone_stripped_newns = true;
			poke_reg(tracee, SYSARG_1, flags & ~(word_t) CLONE_NS_MASK);
		}
		status = 0;
		break;
	}

	case PR_clone3: {
		word_t args_addr = peek_reg(tracee, CURRENT, SYSARG_1);
		word_t flags;

		if (args_addr != 0) {
			errno = 0;
			flags = peek_word(tracee, args_addr);
			if (errno == 0 && (flags & CLONE_NS_MASK) != 0) {
				if ((flags & CLONE_NEWNS) != 0)
					tracee->clone_stripped_newns = true;
				poke_word(tracee, args_addr,
					  flags & ~(word_t) CLONE_NS_MASK);
			}
		}
		status = 0;
		break;
	}

	/* mount(2) and pivot_root(2) are emulated by translating them
	 * into PRoot bindings (see emulate_mount/emulate_pivot_root)
	 * so the resulting paths actually become accessible.  */
	case PR_mount:
		apply_emulated_mount(tracee);
		poke_reg(tracee, SYSARG_RESULT, 0);
		set_sysnum(tracee, PR_void);
		status = 0;
		break;

	case PR_pivot_root:
		apply_emulated_pivot_root(tracee);
		poke_reg(tracee, SYSARG_RESULT, 0);
		set_sysnum(tracee, PR_void);
		status = 0;
		break;

	case PR_open:
		flags = peek_reg(tracee, CURRENT, SYSARG_2);

		if (tracee->execfn_addr != 0
		    && read_string(tracee, path, peek_reg(tracee, CURRENT, SYSARG_1), PATH_MAX) > 0
		    && strcmp(path, "/proc/self/auxv") == 0) {
			tracee->sysexit_pending = true;
			tracee->restart_how = PTRACE_SYSCALL;
		}

		if (   ((flags & O_NOFOLLOW) != 0)
		    || ((flags & O_EXCL) != 0 && (flags & O_CREAT) != 0))
			status = translate_sysarg(tracee, SYSARG_1, SYMLINK);
		else
			status = translate_sysarg(tracee, SYSARG_1, REGULAR);
		if (status >= 0)
			maybe_redirect_userns_file(tracee, SYSARG_1);
		break;

	case PR_fchownat:
	case PR_fstatat64:
	case PR_newfstatat:
	case PR_utimensat:
	case PR_name_to_handle_at:
		dirfd = peek_reg(tracee, CURRENT, SYSARG_1);

		status = get_sysarg_path(tracee, path, SYSARG_2);
		if (status < 0)
			break;

		flags = (  syscall_number == PR_fchownat
			|| syscall_number == PR_name_to_handle_at)
			? peek_reg(tracee, CURRENT, SYSARG_5)
			: peek_reg(tracee, CURRENT, SYSARG_4);

		if ((flags & AT_SYMLINK_NOFOLLOW) != 0)
			status = translate_path2(tracee, dirfd, path, SYSARG_2, SYMLINK);
		else
			status = translate_path2(tracee, dirfd, path, SYSARG_2, REGULAR);
		break;

	case PR_fchmodat:
	case PR_faccessat:
	case PR_faccessat2:
	case PR_futimesat:
	case PR_mknodat:
		dirfd = peek_reg(tracee, CURRENT, SYSARG_1);

		status = get_sysarg_path(tracee, path, SYSARG_2);
		if (status < 0)
			break;

		status = translate_path2(tracee, dirfd, path, SYSARG_2, REGULAR);
		break;

	case PR_inotify_add_watch:
		flags = peek_reg(tracee, CURRENT, SYSARG_3);

		if ((flags & IN_DONT_FOLLOW) != 0)
			status = translate_sysarg(tracee, SYSARG_2, SYMLINK);
		else
			status = translate_sysarg(tracee, SYSARG_2, REGULAR);
		break;

	case PR_readlink:
	case PR_lchown:
	case PR_lchown32:
	case PR_lgetxattr:
	case PR_llistxattr:
	case PR_lremovexattr:
	case PR_lsetxattr:
	case PR_lstat:
	case PR_lstat64:
	case PR_oldlstat:
	case PR_unlink:
	case PR_rmdir:
	case PR_mkdir:
		status = translate_sysarg(tracee, SYSARG_1, SYMLINK);
		break;

	case PR_linkat:
		olddirfd = peek_reg(tracee, CURRENT, SYSARG_1);
		newdirfd = peek_reg(tracee, CURRENT, SYSARG_3);
		flags    = peek_reg(tracee, CURRENT, SYSARG_5);

		status = get_sysarg_path(tracee, oldpath, SYSARG_2);
		if (status < 0)
			break;

		status = get_sysarg_path(tracee, newpath, SYSARG_4);
		if (status < 0)
			break;

		if ((flags & AT_SYMLINK_FOLLOW) != 0)
			status = translate_path2(tracee, olddirfd, oldpath, SYSARG_2, REGULAR);
		else
			status = translate_path2(tracee, olddirfd, oldpath, SYSARG_2, SYMLINK);
		if (status < 0)
			break;

		status = translate_path2(tracee, newdirfd, newpath, SYSARG_4, SYMLINK);
		break;

	case PR_openat2: {
		/* int openat2(int dirfd, const char *pathname,
		 *             struct open_how *how, size_t size);
		 *
		 * Rewrite into openat() and translate it as such: the path is
		 * in SYSARG_2 like openat(), but the open flags live inside the
		 * open_how struct rather than in a register, so move them into
		 * SYSARG_3.  The how.resolve flags (RESOLVE_BENEATH, ...) are
		 * dropped: they reject the absolute host paths PRoot produces,
		 * and PRoot already keeps path resolution inside the rootfs.  */
		struct proot_open_how how = {};
		word_t how_size = peek_reg(tracee, CURRENT, SYSARG_4);
		if (how_size > sizeof(how))
			how_size = sizeof(how);
		status = read_data(tracee, &how, peek_reg(tracee, CURRENT, SYSARG_3), how_size);
		if (status < 0)
			break;
		set_sysnum(tracee, PR_openat);
		poke_reg(tracee, SYSARG_3, how.flags);
		poke_reg(tracee, SYSARG_4, how.mode);
	}
		/* Fall through.  */

	case PR_openat:
		dirfd = peek_reg(tracee, CURRENT, SYSARG_1);
		flags = peek_reg(tracee, CURRENT, SYSARG_3);

		status = get_sysarg_path(tracee, path, SYSARG_2);
		if (status < 0)
			break;

		if (tracee->execfn_addr != 0 && strcmp(path, "/proc/self/auxv") == 0) {
			tracee->sysexit_pending = true;
			tracee->restart_how = PTRACE_SYSCALL;
		}

		if (   ((flags & O_NOFOLLOW) != 0)
			|| ((flags & O_EXCL) != 0 && (flags & O_CREAT) != 0))
			status = translate_path2(tracee, dirfd, path, SYSARG_2, SYMLINK);
		else
			status = translate_path2(tracee, dirfd, path, SYSARG_2, REGULAR);
		if (status >= 0)
			maybe_redirect_userns_file(tracee, SYSARG_2);
		break;

	case PR_readlinkat:
	case PR_unlinkat:
	case PR_mkdirat:
		dirfd = peek_reg(tracee, CURRENT, SYSARG_1);

		status = get_sysarg_path(tracee, path, SYSARG_2);
		if (status < 0)
			break;

		status = translate_path2(tracee, dirfd, path, SYSARG_2, SYMLINK);
		break;

	case PR_link:
	case PR_rename:
		status = translate_sysarg(tracee, SYSARG_1, SYMLINK);
		if (status < 0)
			break;

		status = translate_sysarg(tracee, SYSARG_2, SYMLINK);
		break;

	case PR_renameat:
	case PR_renameat2:
		olddirfd = peek_reg(tracee, CURRENT, SYSARG_1);
		newdirfd = peek_reg(tracee, CURRENT, SYSARG_3);

		status = get_sysarg_path(tracee, oldpath, SYSARG_2);
		if (status < 0)
			break;

		status = get_sysarg_path(tracee, newpath, SYSARG_4);
		if (status < 0)
			break;

		status = translate_path2(tracee, olddirfd, oldpath, SYSARG_2, SYMLINK);
		if (status < 0)
			break;

		status = translate_path2(tracee, newdirfd, newpath, SYSARG_4, SYMLINK);
		break;

	case PR_symlink:
		status = translate_sysarg(tracee, SYSARG_2, SYMLINK);
		break;

	case PR_symlinkat:
		newdirfd = peek_reg(tracee, CURRENT, SYSARG_2);

		status = get_sysarg_path(tracee, newpath, SYSARG_3);
		if (status < 0)
			break;

		status = translate_path2(tracee, newdirfd, newpath, SYSARG_3, SYMLINK);
		break;

	case PR_statx:
		newdirfd = peek_reg(tracee, CURRENT, SYSARG_1);

		status = get_sysarg_path(tracee, newpath, SYSARG_2);
		if (status < 0)
			break;

		status = translate_path2(
			tracee,
			newdirfd,
			newpath,
			SYSARG_2,
			(peek_reg(tracee, CURRENT, SYSARG_3) & AT_SYMLINK_NOFOLLOW) ? SYMLINK : REGULAR
		);
		break;

	case PR_prctl:
		/* Prevent tracees from setting dumpable flag.
		 * (Otherwise it could break tracee memory access)  */
		if (peek_reg(tracee, CURRENT, SYSARG_1) == PR_SET_DUMPABLE) {
			poke_reg(tracee, SYSARG_RESULT, 0);
			set_sysnum(tracee, PR_void);
			status = 0;
		}
		/* On kernels that don't support PTRACE_O_TRACESECCOMP,
		 * SECCOMP_RET_TRACE causes filtered syscalls to return
		 * -ENOSYS to the tracee without generating a ptrace event.
		 * If a tracee installs its own SECCOMP_MODE_FILTER, the
		 * syscalls proot must intercept (open, execve, ...) would
		 * silently fail from proot's perspective.  Block the filter
		 * installation so proot's PTRACE_SYSCALL path keeps working.
		 * This situation is typical on old ARM 32-bit Android kernels
		 * that backported seccomp but not PTRACE_O_TRACESECCOMP.  */
#ifndef SECCOMP_MODE_FILTER
#define SECCOMP_MODE_FILTER 2
#endif
		if (peek_reg(tracee, CURRENT, SYSARG_1) == PR_SET_SECCOMP
		    && peek_reg(tracee, CURRENT, SYSARG_2) == SECCOMP_MODE_FILTER
		    && !seccomp_ptrace_event_is_supported()) {
			VERBOSE(tracee, 1, "blocking tracee prctl(PR_SET_SECCOMP, "
				"SECCOMP_MODE_FILTER): kernel lacks "
				"PTRACE_EVENT_SECCOMP support");
			poke_reg(tracee, SYSARG_RESULT, (word_t) -EPERM);
			set_sysnum(tracee, PR_void);
			status = 0;
		}
		/* Need sysexit to patch AT_EXECFN in the returned buffer. */
#ifndef PR_GET_AUXV
#define PR_GET_AUXV 0x41555856
#endif
		if (peek_reg(tracee, CURRENT, SYSARG_1) == PR_GET_AUXV) {
			tracee->sysexit_pending = true;
			tracee->restart_how = PTRACE_SYSCALL;
		}
		/* PRoot always sets PR_SET_NO_NEW_PRIVS in the child before
		 * execve (a precondition for its seccomp filter), so the real
		 * flag is on even though the guest never asked for it.  Report
		 * the guest's own intent instead: answer PR_GET_NO_NEW_PRIVS
		 * from tracee->no_new_privs without running the real syscall,
		 * and observe the tracee's own PR_SET_NO_NEW_PRIVS at sysexit.
		 * Tools like sudo-rs refuse to run when the flag appears set. */
		if (peek_reg(tracee, CURRENT, SYSARG_1) == PR_GET_NO_NEW_PRIVS) {
			poke_reg(tracee, SYSARG_RESULT, tracee->no_new_privs ? 1 : 0);
			set_sysnum(tracee, PR_void);
			status = 0;
		}
		if (peek_reg(tracee, CURRENT, SYSARG_1) == PR_SET_NO_NEW_PRIVS) {
			tracee->sysexit_pending = true;
			tracee->restart_how = PTRACE_SYSCALL;
		}
		break;

	case PR_ioctl: {
		word_t cmd = peek_reg(tracee, CURRENT, SYSARG_2);
		word_t arg = peek_reg(tracee, CURRENT, SYSARG_3);

		/* SIOCGIFINDEX: Android often denies this with EACCES for
		 * real interfaces (and loopback), so resolve the index in
		 * the tracer instead — bubblewrap's loopback_setup() and
		 * ifaddr/zeroconf's interface lookup both depend on it.  */
		if (cmd == SIOCGIFINDEX && maybe_fake_siocgifindex(tracee, cmd, arg)) {
			poke_reg(tracee, SYSARG_RESULT, 0);
			set_sysnum(tracee, PR_void);
			break;
		}

#ifdef __ANDROID__
		/* Using literal value because Termux build system patches TCSAFLUSH */
		if (cmd == TCSETS + 2 /* + TCSAFLUSH */)
			poke_reg(tracee, SYSARG_2, TCSETS + TCSANOW);

		if (cmd == TCGETS2)
			poke_reg(tracee, SYSARG_2, TCGETS);

		if (cmd == TCSETS2)
			poke_reg(tracee, SYSARG_2, TCSETS);

		if (cmd == TCSETSW2)
			poke_reg(tracee, SYSARG_2, TCSETSW);

		if (cmd == TCSETSF2)
			poke_reg(tracee, SYSARG_2, TCSETS);
#endif

		break;
	}
	
	case PR_memfd_create:
		{
			char memfd_name[20] = {};
			if (read_string(tracee, memfd_name, peek_reg(tracee, CURRENT, SYSARG_1), sizeof(memfd_name) - 1) < 0) {
				/* Failed to read memfd name, do nothing and let normal memfd proceed.  */
				break;
			}
			/* If this memfd is one of those used by Qt/QML for executable code,
			 * deny memfd_create() call and let Qt fall back to anonymous mmap.  */
			if (0 == strncmp(memfd_name, "JITCode:", 8)) {
				status = -EACCES;
			}
			/* php8.3 attempts using memfd as lock through fcntl(F_SETLKW),
			 * which is not allowed on Android,
			 * deny memfd_create() call and let php fall back to open(O_TMPFILE).
			 * https://github.com/php/php-src/blob/26c432d850c153aaf79a1b24e4753bc0533e02b0/ext/opcache/zend_shared_alloc.c#L91
			 */
			if (0 == strcmp(memfd_name, "opcache_lock")) {
				status = -EACCES;
			}
			/* apk-tools v3 use memfd_create + execveat, which is not supported under PRoot
			 * https://github.com/termux/proot-distro/issues/595#issuecomment-3705344471
			 * https://git.alpinelinux.org/apk-tools/tree/src/package.c?h=v3.0.3#n737
			 */
			if (0 == strncmp(memfd_name, "lib/apk/exec/", 13)) {
				status = -EACCES;
			}
			break;
		}
	case PR_close: {
		int closed_fd = (int) peek_reg(tracee, CURRENT, SYSARG_1);

		/* Stop tracking auxv_fd once the tracee closes it. */
		if (tracee->auxv_fd >= 0 && closed_fd == tracee->auxv_fd)
			tracee->auxv_fd = -1;

		/* Drop the fd from the fake-AF_NETLINK tracking set,
		 * otherwise its number could be reused for an unrelated
		 * file and we'd keep intercepting sendto/recvfrom on
		 * it.  */
		unmark_fake_netlink_fd(tracee, closed_fd);
		break;
	}

	}


end:
	status2 = notify_extensions(tracee, SYSCALL_ENTER_END, status, 0);
	if (status2 < 0)
		status = status2;

	return status;
}

