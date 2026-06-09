#ifndef FAKE_ID0_RENAME_H
#define FAKE_ID0_RENAME_H

#include "tracee/tracee.h"
#include "tracee/reg.h"
#include "extension/fake_id0/config.h"

int handle_rename_enter_end(Tracee *tracee, Reg oldfd_sysarg, Reg oldpath_sysarg, Reg newfd_sysarg, Reg newpath_sysarg, Config *config);

#endif /* FAKE_ID0_RENAME_H */
