#ifndef STATX_H
#define STATX_H

#include "tracee/tracee.h"
#include "sys/vfs.h"
#include "path/path.h"
#include "extension/extension.h"

/*
 * This structure is passed to extensions
 * for STATX_SYSCALL event
 */
struct statx_syscall_state {
	/* Host path to statx()'d file */
	char host_path[PATH_MAX];

	/* This is statx structure that will be returned
	 * Extensions can fill additional data in it
	 *
	 * After changing data there set updated_stats to true
	 */
	struct statx statx_buf;

	/* Flag indicating that statx_buf was changed
	 * and needs to be copied back to tracee
	 */
	bool updated_stats;
};

int handle_statx_syscall(Tracee *tracee, bool from_sigsys);


#endif // STATX_H
