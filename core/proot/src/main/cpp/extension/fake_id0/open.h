#ifndef FAKE_ID0_OPEN_H
#define FAKE_ID0_OPEN_H

#include "tracee/tracee.h"
#include "tracee/reg.h"
#include "extension/fake_id0/config.h"

int handle_open_enter_end(Tracee *tracee, Reg fd_sysarg, Reg path_sysarg, Reg flags_sysarg, Reg mode_sysarg, Config *config);

#endif /* FAKE_ID0_OPEN_H */
