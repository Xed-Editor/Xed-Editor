#ifndef FAKE_ID0_LINK_H
#define FAKE_ID0_LINK_H

#include "tracee/tracee.h"
#include "tracee/reg.h"
#include "extension/fake_id0/config.h"

int handle_link_enter_end(Tracee *tracee, Reg olddirfd_sysarg, Reg oldpath_sysarg, Reg newdirfd_sysarg, Reg newpath_sysarg, Config *config);

#endif /* FAKE_ID0_LINK_H */
