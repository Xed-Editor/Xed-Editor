#ifndef FAKE_ID0_CHOWN_H
#define FAKE_ID0_CHOWN_H

#include "tracee/tracee.h"
#include "tracee/reg.h"
#include "extension/fake_id0/config.h"

#ifndef USERLAND
int handle_chown_enter_end(Tracee *tracee, Config *config, Reg uid_sysarg, Reg gid_sysarg);
#endif /* ifndef USERLAND */

#ifdef USERLAND
int handle_chown_enter_end(Tracee *tracee, Reg path_sysarg, Reg owner_sysarg, Reg group_sysarg, Reg fd_sysarg, Reg dirfd_sysarg, Config *config);
#endif /* ifdef USERLAND */

#endif /* FAKE_ID0_CHOWN_H */
