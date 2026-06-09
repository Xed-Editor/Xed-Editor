#ifndef FAKE_ID0_EXEC_H
#define FAKE_ID0_EXEC_H

#include "tracee/tracee.h"
#include "tracee/reg.h"
#include "extension/fake_id0/config.h"

extern int handle_exec_enter_end(Tracee *tracee, Reg filename_sysarg, Config *config);

#endif /* FAKE_ID0_EXEC_H */
