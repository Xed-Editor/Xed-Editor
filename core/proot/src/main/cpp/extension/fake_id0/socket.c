#include <errno.h>       /* E*, */

#include "tracee/reg.h"
#include "extension/fake_id0/socket.h"

int handle_socket_exit_end(Tracee *tracee, Config *config) {
	word_t result;

	/* Override only permission errors.  */
	result = peek_reg(tracee, CURRENT, SYSARG_RESULT);
	if ((int) result != -EPERM && (int) result != -EACCES)
		return 0;

	/* Emulate audit functionality not compiled into kernel
	 * 		 * if tracee was supposed to have the capability.  */
	if (
	peek_reg(tracee, ORIGINAL, SYSARG_1) == 16 /* AF_NETLINK */ &&
	peek_reg(tracee, ORIGINAL, SYSARG_3) == 9 /* NETLINK_AUDIT */ &&
	config->euid == 0) /* TODO: || HAS_CAP(...) */
		return -EPROTONOSUPPORT;

	return 0;
}
