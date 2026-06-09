#ifndef FAKE_ID0_UTIMENSAT_H
#define FAKE_ID0_UTIMENSAT_H

#include "tracee/tracee.h"
#include "tracee/reg.h"
#include "extension/fake_id0/config.h"

int handle_utimensat_enter_end(Tracee *tracee, Reg dirfd_sysarg, Reg path_sysarg, Reg times_sysarg, Config *config);

#endif /* FAKE_ID0_UTIMENSAT_H */
