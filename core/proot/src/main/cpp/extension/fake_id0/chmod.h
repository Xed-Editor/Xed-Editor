#ifndef FAKE_ID0_CHMOD_H
#define FAKE_ID0_CHMOD_H

#include "tracee/tracee.h"
#include "tracee/reg.h"
#include "extension/fake_id0/config.h"

int handle_chmod_enter_end(Tracee *tracee, Reg path_sysarg, Reg mode_sysarg, Reg fd_sysarg, Reg dirfd_sysarg, Config *config);

#endif /* FAKE_ID0_CHMOD_H */
