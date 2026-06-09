#ifndef FAKE_ID0_CONFIG_H
#define FAKE_ID0_CONFIG_H

#include <stdbool.h>     /* bool */
#include <sys/types.h>   /* uid_t, gid_t */

typedef struct {
	uid_t ruid;
	uid_t euid;
	uid_t suid;
	uid_t fsuid;

	gid_t rgid;
	gid_t egid;
	gid_t sgid;
	gid_t fsgid;

	mode_t umask;

	/* Whether the process effectively holds CAP_SETUID/CAP_SETGID under
	 * proot's fake-root model.  Initialized to true when proot was
	 * launched as fake root (uid == 0).  Cleared when a setuid-family
	 * syscall makes none of r/e/s uid be 0 while at least one was 0
	 * before, mirroring the kernel rule that clears capabilities on a
	 * permanent UID drop -- unless keep_caps is set.  Re-asserted on
	 * execve of a setuid-root binary. */
	bool caps_active;

	/* Mirror of the tracee's prctl(PR_SET_KEEPCAPS) flag.  When set,
	 * caps_active survives a UID drop, matching how PR_SET_KEEPCAPS plus
	 * a follow-up capset() lets a process retain CAP_SETUID/CAP_SETGID
	 * across setresuid().  Cleared by execve, per Linux semantics. */
	bool keep_caps;
} Config;

#endif /* FAKE_ID0_CONFIG_H */
