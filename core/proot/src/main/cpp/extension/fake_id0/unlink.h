#ifndef FAKE_ID0_UNLINK_H
#define FAKE_ID0_UNLINK_H

#include "tracee/tracee.h"
#include "tracee/reg.h"
#include "extension/fake_id0/config.h"

extern int handle_unlink_enter_end(Tracee *tracee, Reg fd_sysarg, Reg path_sysarg, Config *config);

#endif /* FAKE_ID0_UNLINK_H */
