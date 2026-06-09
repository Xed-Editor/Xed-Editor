/*
 * Unix SMB/CIFS implementation.
 * Samba utility functions
 * Copyright (C) Volker Lendecke 2016
 *
 *   ** NOTE! The following LGPL license applies to the replace
 *   ** library. This does NOT imply that all of Samba is released
 *   ** under the LGPL
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, see <http://www.gnu.org/licenses/>.
 */

#include "replace.h"
#include <dirent.h>
#include <unistd.h>
#include <limits.h>

static int closefrom_sysconf(int lower)
{
	long max_files, fd;

	max_files = sysconf(_SC_OPEN_MAX);
	if (max_files == -1) {
		max_files = 65536;
	}

	for (fd=lower; fd<max_files; fd++) {
		close(fd);
	}

	return 0;
}

static int closefrom_procfs(int lower)
{
	DIR *dirp;
	int dir_fd;
	struct dirent *dp;
	int *fds = NULL;
	size_t num_fds = 0;
	size_t fd_array_size = 0;
	size_t i;
	int ret = ENOMEM;

	dirp = opendir("/proc/self/fd");
	if (dirp == NULL) {
		return errno;
	}

	dir_fd = dirfd(dirp);
	if (dir_fd == -1) {
		ret = errno;
		goto fail;
	}

	while ((dp = readdir(dirp)) != NULL) {
		char *endptr;
		unsigned long long fd;

		errno = 0;

		fd = strtoull(dp->d_name, &endptr, 10);
		if ((fd == 0) && (errno == EINVAL)) {
			continue;
		}
		if ((fd == ULLONG_MAX) && (errno == ERANGE)) {
			continue;
		}
		if (*endptr != '\0') {
			continue;
		}
		if (fd == dir_fd) {
			continue;
		}
		if (fd > INT_MAX) {
			continue;
		}
		if (fd < lower) {
			continue;
		}

		if (num_fds >= (fd_array_size / sizeof(int))) {
			void *tmp;

			if (fd_array_size == 0) {
				fd_array_size = 16 * sizeof(int);
			} else {
				if (fd_array_size + fd_array_size <
				    fd_array_size) {
					/* overflow */
					goto fail;
				}
				fd_array_size = fd_array_size + fd_array_size;
			}

			tmp = realloc(fds, fd_array_size);
			if (tmp == NULL) {
				goto fail;
			}
			fds = tmp;
		}

		fds[num_fds++] = fd;
	}

	for (i=0; i<num_fds; i++) {
		close(fds[i]);
	}

	ret = 0;
fail:
	closedir(dirp);
	free(fds);
	return ret;
}

int rep_closefrom(int lower)
{
	int ret;

	ret = closefrom_procfs(lower);
	if (ret == 0) {
		return 0;
	}

	return closefrom_sysconf(lower);
}
