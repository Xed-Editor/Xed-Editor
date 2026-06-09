#ifndef FAKE_ID0_SYMLINK_H
#define FAKE_ID0_SYMLINK_H

#include "tracee/tracee.h"
#include "tracee/reg.h"
#include "extension/fake_id0/config.h"

int handle_symlink_enter_end(Tracee *tracee, Reg oldpath_sysarg, Reg newdirfd_sysarg, Reg newpath_sysarg, Config *config);

#endif /* FAKE_ID0_SYMLINK_H */
