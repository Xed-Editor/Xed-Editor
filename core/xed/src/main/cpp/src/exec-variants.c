// These exec variants, which ends up calling execve(), comes from bionic:
// https://android.googlesource.com/platform/bionic/+/refs/heads/main/libc/bionic/exec.cpp
//
// For some reason these are only necessary starting with Android 14 - before
// that intercepting execve() is enough.
//
// See the test-program.c for how to test the different variants.

#define _GNU_SOURCE
#include <errno.h>
#include <paths.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

enum { ExecL, ExecLE, ExecLP };

static int __exec_as_script(const char *buf, char *const *argv, char *const *envp) {
  size_t arg_count = 1;
  while (argv[arg_count] != NULL)
    ++arg_count;

  const char *script_argv[arg_count + 2];
  script_argv[0] = "sh";
  script_argv[1] = buf;
  memcpy(script_argv + 2, argv + 1, arg_count * sizeof(char *));
  return execve(_PATH_BSHELL, (char **const)script_argv, envp);
}

__attribute__((visibility("default"))) int execv(const char *name, char *const *argv) {
  return execve(name, argv, environ);
}

__attribute__((visibility("default"))) int execvp(const char *name, char *const *argv) {
  return execvpe(name, argv, environ);
}

__attribute__((visibility("default"))) int execvpe(const char *name, char *const *argv, char *const *envp) {
  if (name == NULL || *name == '\0') {
    errno = ENOENT;
    return -1;
  }

  // If it's an absolute or relative path name, it's easy.
  if (strchr(name, '/') && execve(name, argv, envp) == -1) {
    if (errno == ENOEXEC)
      return __exec_as_script(name, argv, envp);
    return -1;
  }

  // Get the path we're searching.
  const char *path = getenv("PATH");
  if (path == NULL)
    path = _PATH_DEFPATH;

  // Make a writable copy.
  size_t len = strlen(path) + 1;
  char writable_path[len];
  memcpy(writable_path, path, len);

  bool saw_EACCES = false;

  // Try each element of $PATH in turn...
  char *strsep_buf = writable_path;
  const char *dir;
  while ((dir = strsep(&strsep_buf, ":"))) {
    // It's a shell path: double, leading and trailing colons
    // mean the current directory.
    if (*dir == '\0')
      dir = ".";

    size_t dir_len = strlen(dir);
    size_t name_len = strlen(name);

    char buf[dir_len + 1 + name_len + 1];
    mempcpy(mempcpy(mempcpy(buf, dir, dir_len), "/", 1), name, name_len + 1);

    execve(buf, argv, envp);
    switch (errno) {
    case EISDIR:
    case ELOOP:
    case ENAMETOOLONG:
    case ENOENT:
    case ENOTDIR:
      break;
    case ENOEXEC:
      return __exec_as_script(buf, argv, envp);
      return -1;
    case EACCES:
      saw_EACCES = true;
      break;
    default:
      return -1;
    }
  }
  if (saw_EACCES)
    errno = EACCES;
  return -1;
}

static int __execl(int variant, const char *name, const char *argv0, va_list ap) {
  // Count the arguments.
  va_list count_ap;
  va_copy(count_ap, ap);
  size_t n = 1;
  while (va_arg(count_ap, char *) != NULL) {
    ++n;
  }
  va_end(count_ap);

  // Construct the new argv.
  char *argv[n + 1];
  argv[0] = (char *)argv0;
  n = 1;
  while ((argv[n] = va_arg(ap, char *)) != NULL) {
    ++n;
  }

  // Collect the argp too.
  char **argp = (variant == ExecLE) ? va_arg(ap, char **) : environ;

  va_end(ap);

  return (variant == ExecLP) ? execvp(name, argv) : execve(name, argv, argp);
}

__attribute__((visibility("default"))) int execl(const char *name, const char *arg, ...) {
  va_list ap;
  va_start(ap, arg);
  int result = __execl(ExecL, name, arg, ap);
  va_end(ap);
  return result;
}

__attribute__((visibility("default"))) int execle(const char *name, const char *arg, ...) {
  va_list ap;
  va_start(ap, arg);
  int result = __execl(ExecLE, name, arg, ap);
  va_end(ap);
  return result;
}

__attribute__((visibility("default"))) int execlp(const char *name, const char *arg, ...) {
  va_list ap;
  va_start(ap, arg);
  int result = __execl(ExecLP, name, arg, ap);
  va_end(ap);
  return result;
}

__attribute__((visibility("default"))) int fexecve(int fd, char *const *argv, char *const *envp) {
  char buf[40];
  snprintf(buf, sizeof(buf), "/proc/self/fd/%d", fd);
  execve(buf, argv, envp);
  if (errno == ENOENT)
    errno = EBADF;
  return -1;
}
