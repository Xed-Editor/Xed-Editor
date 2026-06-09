#ifndef F2FS_BUG_H
#define F2FS_BUG_H

#include <stdbool.h>       /* bool, true, false,  */
#include "tracee/tracee.h" /* Tracee,  */

bool should_skip_file_access_due_to_f2fs_bug(const Tracee *tracee, const char *path);

#endif /* F2FS_BUG_H */
