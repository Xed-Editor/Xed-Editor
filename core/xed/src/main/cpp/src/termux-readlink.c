#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <sys/syscall.h>
#include <unistd.h>

__attribute__((visibility("default"))) ssize_t readlink(const char *restrict pathname, char *restrict buf,
                                                        size_t bufsiz) {
  if (strcmp(pathname, "/proc/self/exe") == 0) {
    const char *termux_self_exe = getenv("TERMUX_EXEC__PROC_SELF_EXE");
    if (termux_self_exe) {
      char resolved_path_buf[PATH_MAX];
      char *resolved_path = realpath(termux_self_exe, resolved_path_buf);
      if (resolved_path) {
        termux_self_exe = resolved_path_buf;
      }
      size_t termux_self_exe_len = strlen(termux_self_exe);
      size_t bytes_to_copy = (termux_self_exe_len < bufsiz) ? termux_self_exe_len : bufsiz;
      memcpy(buf, termux_self_exe, bytes_to_copy);
      return bytes_to_copy;
    }
  } else if (strncmp(pathname, "/proc/", strlen("/proc/")) == 0) {
    // See if /proc/$PID/exe is being resolved.
    size_t path_len = strlen(pathname);
    if (pathname[path_len - 4] == '/' && pathname[path_len - 3] == 'e' && pathname[path_len - 2] == 'x' &&
        pathname[path_len - 1] == 'e' && path_len < 30) {
      char environ_path_buf[PATH_MAX];
      memcpy(environ_path_buf, pathname, path_len - 3);
      memcpy(environ_path_buf + (path_len - 3), "environ", sizeof("environ"));
      int environ_fd = TEMP_FAILURE_RETRY(open(environ_path_buf, O_RDONLY));
      if (environ_fd > 0) {
        char environ_buf[16 * 4096];
        ssize_t environ_size = TEMP_FAILURE_RETRY(read(environ_fd, environ_buf, sizeof(environ_buf)));
        close(environ_fd);
        if (environ_size > 0) {
          size_t start_offset = 0;
          for (size_t i = 0; i < (size_t)environ_size; i++) {
            if (environ_buf[i] == 0 && start_offset != i) {
              if (strncmp(&environ_buf[start_offset],
                          "TERMUX_EXEC__PROC_SELF_EXE=", strlen("TERMUX_EXEC__PROC_SELF_EXE=")) == 0) {
                char *termux_self_exe = &environ_buf[start_offset + strlen("TERMUX_EXEC__PROC_SELF_EXE=")];
                char resolved_path_buf[PATH_MAX];
                char *resolved_path = realpath(termux_self_exe, resolved_path_buf);
                if (resolved_path) {
                  termux_self_exe = resolved_path_buf;
                }
                size_t termux_self_exe_len = strlen(termux_self_exe);
                size_t bytes_to_copy = (termux_self_exe_len < bufsiz) ? termux_self_exe_len : bufsiz;
                memcpy(buf, termux_self_exe, bytes_to_copy);
                return bytes_to_copy;
              }
              start_offset = i + 1;
            }
          }
        }
      }
    }
  }

  return syscall(SYS_readlinkat, AT_FDCWD, pathname, buf, bufsiz);
}
