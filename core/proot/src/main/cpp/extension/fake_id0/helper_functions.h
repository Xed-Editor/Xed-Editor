#ifndef FAKE_ID0_HELPER_FUNCTIONS_H
#define FAKE_ID0_HELPER_FUNCTIONS_H

#include <linux/limits.h>

#include "tracee/tracee.h"
#include "tracee/reg.h"
#include "extension/fake_id0/config.h"

#define IGNORE_SYSARG (Reg)2000

int check_dir_perms(Tracee *tracee, char type, char path[PATH_MAX], char rel_path[PATH_MAX], Config *config);

int get_dir_path(char path[PATH_MAX], char dir_path[PATH_MAX]);

int dtoo(int n);
int otod(int n);

int get_meta_path(char orig_path[PATH_MAX], char meta_path[PATH_MAX]);

int read_meta_file(char path[PATH_MAX], mode_t *mode, uid_t *owner, gid_t *group, Config *config);

int write_meta_file(char path[PATH_MAX], mode_t mode, uid_t owner, gid_t group, bool is_creat, Config *config);

char * get_name(char path[PATH_MAX]);

int get_permissions(char meta_path[PATH_MAX], Config *config, bool uses_real);

int path_exists(char path[PATH_MAX]);

int get_fd_path(Tracee *tracee, char path[PATH_MAX], Reg fd_sysarg, RegVersion version);

int read_sysarg_path(Tracee *tracee, char path[PATH_MAX], Reg path_sysarg, RegVersion version);

#endif /* FAKE_ID0_HELPER_FUNCTIONS_H */
