#define _GNU_SOURCE
// #include <dlfcn.h>
// #include <string.h>
#include <elf.h>
#include <errno.h>
#include <fcntl.h>
#include <libgen.h>
#include <limits.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/syscall.h>
#include <unistd.h>

#if UINTPTR_MAX == 0xffffffff
#define SYSTEM_LINKER_PATH "/system/bin/linker";
#elif UINTPTR_MAX == 0xffffffffffffffff
#define SYSTEM_LINKER_PATH "/system/bin/linker64";
#endif

#ifdef __aarch64__
#define EM_NATIVE EM_AARCH64
#elif defined(__arm__) || defined(__thumb__)
#define EM_NATIVE EM_ARM
#elif defined(__x86_64__)
#define EM_NATIVE EM_X86_64
#elif defined(__i386__)
#define EM_NATIVE EM_386
#else
#error "unknown arch"
#endif

#ifndef TERMUX_BASE_DIR
#define TERMUX_BASE_DIR "/data/data/com.termux/files"
#endif

#define TERMUX_BIN_PATH TERMUX_BASE_DIR "/usr/bin/"
#define TERMUX_BIN_PATH_LEN sizeof(TERMUX_BIN_PATH) - 1

#define LOG_PREFIX "[termux-exec] "

// Check if `string` starts with `prefix`.
static bool starts_with(const char *string, const char *prefix) { return strncmp(string, prefix, strlen(prefix)) == 0; }

// Rewrite e.g. "/bin/sh" and "/usr/bin/sh" to "${TERMUX_PREFIX}/bin/sh".
static const char *termux_rewrite_executable(const char *executable_path, char *buffer, int buffer_len) {
  if (executable_path[0] != '/') {
    return executable_path;
  }

  char *bin_match = strstr(executable_path, "/bin/");
  if (bin_match == executable_path || bin_match == (executable_path + 4)) {
    // Found "/bin/" or "/xxx/bin" at the start of executable_path.
    strcpy(buffer, TERMUX_BIN_PATH);
    char *dest = buffer + TERMUX_BIN_PATH_LEN;
    // Copy what comes after "/bin/":
    const char *src = bin_match + 5;
    size_t max_bytes_to_copy = buffer_len - TERMUX_BIN_PATH_LEN;
    strncpy(dest, src, max_bytes_to_copy);
    return buffer;
  } else {
    return executable_path;
  }
}

// If proc_self_exe is non-null, insert TERMUX_EXEC__PROC_SELF_EXE={proc_self_exe}.
// Since we are executing:
//    /system/bin/linker64 ${TERMUX_EXEC__PROC_SELF_EXE} ..arguments..
// processes cannot read /proc/self/exe but instead needs to be patched to read TERMUX_EXEC__PROC_SELF_EXE.
// If proc_self_exe is null, remove LD_LIBRARY_PATH, LD_PRELOAD and TERMUX_EXEC__PROC_SELF_EXE entries from envp.
static bool setup_env(char *const *envp, char ***allocation, char const *proc_self_exe, char **termux_self_exe) {
  bool cleanup_env = proc_self_exe == NULL;
  int env_length = 0;
  while (envp[env_length] != NULL) {
    env_length++;
  }

  char **new_envp = malloc(sizeof(char *) * (env_length + 2));
  if (new_envp == NULL) {
    return false;
  }
  *allocation = new_envp;
  int new_envp_idx = 0;
  int old_envp_idx = 0;

  if (!cleanup_env) {
    if (asprintf(termux_self_exe, "TERMUX_EXEC__PROC_SELF_EXE=%s", proc_self_exe) == -1) {
      return false;
    }
  }

  bool overwritten_self_exe = false;
  while (old_envp_idx < env_length) {
    bool do_transfer = true;
    if (cleanup_env) {
      if (starts_with(envp[old_envp_idx], "LD_LIBRARY_PATH=") || starts_with(envp[old_envp_idx], "LD_PRELOAD=") ||
          starts_with(envp[old_envp_idx], "TERMUX_EXEC__PROC_SELF_EXE=")) {
        do_transfer = false;
      }
    } else {
      if (starts_with(envp[old_envp_idx], "TERMUX_EXEC__PROC_SELF_EXE=")) {
        overwritten_self_exe = true;
        new_envp[new_envp_idx++] = *termux_self_exe;
        do_transfer = false;
      }
    }
    if (do_transfer) {
      new_envp[new_envp_idx++] = envp[old_envp_idx];
    }
    old_envp_idx++;
  }

  if (!cleanup_env && !overwritten_self_exe) {
    new_envp[new_envp_idx++] = *termux_self_exe;
  }

  new_envp[new_envp_idx] = NULL;
  return true;
}

// From https://stackoverflow.com/questions/4774116/realpath-without-resolving-symlinks/34202207#34202207
static const char *normalize_path(const char *src, char *result_buffer) {
  char pwd[PATH_MAX];
  if (getcwd(pwd, sizeof(pwd)) == NULL) {
    return src;
  }

  size_t res_len;
  size_t src_len = strlen(src);

  const char *ptr = src;
  const char *end = &src[src_len];
  const char *next;

  if (src_len == 0 || src[0] != '/') {
    // relative path
    size_t pwd_len = strlen(pwd);
    memcpy(result_buffer, pwd, pwd_len);
    res_len = pwd_len;
  } else {
    res_len = 0;
  }

  for (ptr = src; ptr < end; ptr = next + 1) {
    next = (char *)memchr(ptr, '/', end - ptr);
    if (next == NULL) {
      next = end;
    }
    size_t len = next - ptr;
    switch (len) {
    case 2:
      if (ptr[0] == '.' && ptr[1] == '.') {
        const char *slash = (char *)memrchr(result_buffer, '/', res_len);
        if (slash != NULL) {
          res_len = slash - result_buffer;
        }
        continue;
      }
      break;
    case 1:
      if (ptr[0] == '.') {
        continue;
      }
      break;
    case 0:
      continue;
    }

    if (res_len != 1) {
      result_buffer[res_len++] = '/';
    }

    memcpy(&result_buffer[res_len], ptr, len);
    res_len += len;
  }

  if (res_len == 0) {
    result_buffer[res_len++] = '/';
  }
  result_buffer[res_len] = '\0';
  return result_buffer;
}

struct file_header_info {
  bool is_elf;
  // If executing a 32-bit binary on a 64-bit host:
  bool is_non_native_elf;
  char interpreter_buf[256];
  char const *interpreter;
  char const *interpreter_arg;
};

static void inspect_file_header(char *header, size_t header_len, struct file_header_info *result) {
  if (header_len >= 20 && !memcmp(header, ELFMAG, SELFMAG)) {
    result->is_elf = true;
    Elf32_Ehdr *ehdr = (Elf32_Ehdr *)header;
    if (ehdr->e_machine != EM_NATIVE) {
      result->is_non_native_elf = true;
    }
    return;
  }

  if (header_len < 5 || !(header[0] == '#' && header[1] == '!')) {
    return;
  }

  // Check if the header contains a newline to end the shebang line:
  char *newline_location = memchr(header, '\n', header_len);
  if (newline_location == NULL) {
    return;
  }

  // Strip whitespace at end of shebang:
  while (*(newline_location - 1) == ' ') {
    newline_location--;
  }

  // Null terminate the shebang line:
  *newline_location = 0;

  // Skip whitespace to find interpreter start:
  char const *interpreter = header + 2;
  while (*interpreter == ' ') {
    interpreter++;
  }
  if (interpreter == newline_location) {
    // Just a blank line up until the newline.
    return;
  }

  // Check for whitespace following the interpreter:
  char *whitespace_pos = strchr(interpreter, ' ');
  if (whitespace_pos != NULL) {
    // Null-terminate the interpreter string.
    *whitespace_pos = 0;

    // Find start of argument:
    char *interpreter_arg = whitespace_pos + 1;
    while (*interpreter_arg != 0 && *interpreter_arg == ' ') {
      interpreter_arg++;
    }
    if (interpreter_arg != newline_location) {
      result->interpreter_arg = interpreter_arg;
    }
  }

  result->interpreter =
      termux_rewrite_executable(interpreter, result->interpreter_buf, sizeof(result->interpreter_buf));
}

static int execve_syscall(const char *executable_path, char *const argv[], char *const envp[]) {
  return syscall(SYS_execve, executable_path, argv, envp);
}

// Interceptor of the execve(2) system call using LD_PRELOAD.
__attribute__((visibility("default"))) int execve(const char *executable_path, char *const argv[], char *const envp[]) {
  if (getenv("TERMUX_EXEC_OPTOUT") != NULL) {
    return execve_syscall(executable_path, argv, envp);
  }

  if (executable_path == NULL || argv == NULL || envp == NULL) {
    errno = EFAULT;
    return -1;
  }

  const bool termux_exec_debug = getenv("TERMUX_EXEC_DEBUG") != NULL;
  if (termux_exec_debug) {
    fprintf(stderr, LOG_PREFIX "Intercepting execve('%s'):\n", executable_path);
    int tmp_argv_count = 0;
    if (argv != NULL) {
      while (argv[tmp_argv_count] != NULL) {
        fprintf(stderr, LOG_PREFIX "   argv[%d] = '%s'\n", tmp_argv_count, argv[tmp_argv_count]);
        tmp_argv_count++;
      }
    }
  }

  const char *orig_executable_path = executable_path;

  char executable_path_buffer[PATH_MAX];
  executable_path = termux_rewrite_executable(executable_path, executable_path_buffer, sizeof(executable_path_buffer));
  if (termux_exec_debug && executable_path_buffer == executable_path) {
    fprintf(stderr, LOG_PREFIX "Rewritten path: '%s'\n", executable_path);
  }

  if (access(executable_path, X_OK) != 0) {
    // Error out if the file is not executable:
    errno = EACCES;
    return -1;
  }

  int fd = open(executable_path, O_RDONLY);
  if (fd == -1) {
    // File is not readable - but this might be an attempt to exec /system/bin/su on KernelSU,
    // which does not actually exist but will be handled by the kernel:
    // https://github.com/termux-play-store/termux-apps/issues/10
    // Just clean up environment and call the raw execve() syscall.
    char **new_allocated_envp = NULL;
    if (!setup_env(envp, &new_allocated_envp, NULL, NULL)) {
      errno = ENOMEM;
      return -1;
    }
    int ret = execve_syscall(executable_path, argv, new_allocated_envp);
    // execve_syscall() returned, so error out.
    int saved_errno = errno;
    free(new_allocated_envp);
    errno = saved_errno;
    return ret;
  }

  // execve(2): "The kernel imposes a maximum length on the text that follows the "#!" characters
  // at the start of a script; characters beyond the limit are ignored. Before Linux 5.1, the
  // limit is 127 characters. Since Linux 5.1, the limit is 255 characters."
  // We use one more byte since inspect_file_header() will null terminate the buffer.
  char header[256];
  ssize_t read_bytes = read(fd, header, sizeof(header) - 1);
  close(fd);

  struct file_header_info info = {
      .interpreter = NULL,
      .interpreter_arg = NULL,
  };
  inspect_file_header(header, read_bytes, &info);

  if (!info.is_elf && info.interpreter == NULL) {
    if (termux_exec_debug) {
      fprintf(stderr, LOG_PREFIX "Not ELF or shebang, returning ENOEXEC\n");
    }
    errno = ENOEXEC;
    return -1;
  }

  if (info.interpreter != NULL) {
    executable_path = info.interpreter;
    if (termux_exec_debug) {
      fprintf(stderr, LOG_PREFIX "Path to interpreter from shebang: '%s'\n", info.interpreter);
    }
  }

  char normalized_path_buffer[PATH_MAX];
  executable_path = normalize_path(executable_path, normalized_path_buffer);

  char **new_allocated_envp = NULL;
  char *termux_self_exe = NULL;

  const char **new_argv = NULL;

  // Only wrap with linker if trying to execute a file under the termux base directory:
  char executable_path_resolved_buffer[PATH_MAX];
  char const *executable_path_resolved = realpath(executable_path, executable_path_resolved_buffer);
  char const *path_to_use = executable_path_resolved ? executable_path_resolved : executable_path;
  bool wrap_in_linker = (strstr(path_to_use, TERMUX_BASE_DIR) == path_to_use)
                        // /system/bin/sh is fine, it only uses libc++, libc, and libdl.
                        || (strcmp(path_to_use, "/system/bin/sh") == 0);

  // Avoid interfering with Android /system software by removing
  // LD_PRELOAD and LD_LIBRARY_PATH from env if executing something
  // there.
  if (!setup_env(envp, &new_allocated_envp, wrap_in_linker ? orig_executable_path : NULL, &termux_self_exe)) {
    if (new_allocated_envp) {
      free(new_allocated_envp);
    }
    errno = ENOMEM;
    return -1;
  }
  if (new_allocated_envp) {
    envp = new_allocated_envp;
  }
  if (!wrap_in_linker && termux_exec_debug) {
    fprintf(stderr, LOG_PREFIX "Cleanup up env LD_PRELOAD, LD_LIBRARY_PATH and TERMUX_EXEC__PROC_SELF_EXE from env\n");
  }

  if (wrap_in_linker) {
    int orig_argv_count = 0;
    while (argv[orig_argv_count] != NULL) {
      orig_argv_count++;
    }

    // We add up 4 entries:
    // 1. SYSTEM_LINKER_PATH
    // 2. Original executable path.
    // 3. info.interpreter_arg.
    // 4. NULL termination.
    new_argv = malloc(sizeof(char *) * (orig_argv_count + 4));
    int current_argc = 0;

    // Keep program name:
    new_argv[current_argc++] = argv[0];

    // Specify executable path if wrapping with linker:
    if (wrap_in_linker) {
      // Normalize path without resolving symlink. For instance, $PREFIX/bin/ls is
      // a symlink to $PREFIX/bin/coreutils, but we need to execute
      // "/system/bin/linker $PREFIX/bin/ls" so that coreutils knows what to execute.
      new_argv[current_argc++] = executable_path;
      executable_path = SYSTEM_LINKER_PATH;
    }

    // Add interpreter argument and script path if exec:ing a script with shebang:
    if (info.interpreter != NULL) {
      if (info.interpreter_arg) {
        new_argv[current_argc++] = info.interpreter_arg;
      }
      new_argv[current_argc++] = orig_executable_path;
    }

    for (int i = 1; i < orig_argv_count; i++) {
      new_argv[current_argc++] = argv[i];
    }
    new_argv[current_argc] = NULL;
    argv = (char **)new_argv;
  }

  if (termux_exec_debug) {
    fprintf(stderr, LOG_PREFIX "Calling syscall execve('%s'):\n", executable_path);
    int tmp_argv_count = 0;
    int arg_count = 0;
    while (argv[tmp_argv_count] != NULL) {
      fprintf(stderr, LOG_PREFIX "   argv[%d] = '%s'\n", arg_count++, argv[tmp_argv_count]);
      tmp_argv_count++;
    }
  }

  int syscall_ret = execve_syscall(executable_path, argv, envp);
  int saved_errno = errno;
  if (termux_exec_debug) {
    perror(LOG_PREFIX "execve() syscall failed");
  }
  free(new_argv);
  free(new_allocated_envp);
  free(termux_self_exe);
  errno = saved_errno;
  return syscall_ret;
}

#ifdef UNIT_TEST
#include <assert.h>

void assert_string_equals(const char *expected, const char *actual) {
  if (strcmp(actual, expected) != 0) {
    fprintf(stderr, "Assertion failed - expected '%s', was '%s'\n", expected, actual);
    exit(1);
  }
}

void test_starts_with() {
  assert(starts_with("/path/to/file", "/path"));
  assert(!starts_with("/path", "/path/to/file"));
}

void test_termux_rewrite_executable() {
  char buf[PATH_MAX];
  assert_string_equals(TERMUX_BIN_PATH "sh", termux_rewrite_executable("/bin/sh", buf, PATH_MAX));
  assert_string_equals(TERMUX_BIN_PATH "sh", termux_rewrite_executable("/usr/bin/sh", buf, PATH_MAX));
  assert_string_equals("/system/bin/sh", termux_rewrite_executable("/system/bin/sh", buf, PATH_MAX));
  assert_string_equals("/system/bin/tool", termux_rewrite_executable("/system/bin/tool", buf, PATH_MAX));
  assert_string_equals(TERMUX_BIN_PATH "sh", termux_rewrite_executable(TERMUX_BIN_PATH "sh", buf, PATH_MAX));
  assert_string_equals(TERMUX_BIN_PATH, termux_rewrite_executable("/bin/", buf, PATH_MAX));
  assert_string_equals("./ab/sh", termux_rewrite_executable("./ab/sh", buf, PATH_MAX));
}

void test_setup_env() {
  {
    char *test_env[] = {"MY_ENV=1", NULL};
    char **allocated_envp;
    setup_env(test_env, &allocated_envp, NULL, NULL);
    assert(allocated_envp != NULL);
    assert_string_equals("MY_ENV=1", allocated_envp[0]);
    assert(allocated_envp[1] == NULL);
    free(allocated_envp);
  }
  {
    char *test_env[] = {"MY_ENV=1", "LD_PRELOAD=a", NULL};
    char **allocated_envp;
    setup_env(test_env, &allocated_envp, NULL, NULL);
    assert(allocated_envp != NULL);
    assert_string_equals("MY_ENV=1", allocated_envp[0]);
    assert(allocated_envp[1] == NULL);
    free(allocated_envp);
  }
  {
    char *test_env[] = {"MY_ENV=1", "LD_PRELOAD=a", "A=B", "LD_LIBRARY_PATH=B", "B=C", NULL};
    char **allocated_envp;
    setup_env(test_env, &allocated_envp, NULL, NULL);
    assert(allocated_envp != NULL);
    assert_string_equals("MY_ENV=1", allocated_envp[0]);
    assert_string_equals("A=B", allocated_envp[1]);
    assert_string_equals("B=C", allocated_envp[2]);
    assert(allocated_envp[3] == NULL);
    free(allocated_envp);
  }
  {
    char *test_env[] = {"MY_ENV=1", "LD_PRELOAD=a", "A=B", "LD_LIBRARY_PATH=B", "B=C", NULL};
    char **allocated_envp;
    char *proc_self_exe = "/path/to/exe";
    char *allocated_env;
    setup_env(test_env, &allocated_envp, proc_self_exe, &allocated_env);
    assert(allocated_envp != NULL);
    assert(allocated_env != NULL);
    assert_string_equals("MY_ENV=1", allocated_envp[0]);
    assert_string_equals("LD_PRELOAD=a", allocated_envp[1]);
    assert_string_equals("A=B", allocated_envp[2]);
    assert_string_equals("LD_LIBRARY_PATH=B", allocated_envp[3]);
    assert_string_equals("B=C", allocated_envp[4]);
    assert_string_equals("TERMUX_EXEC__PROC_SELF_EXE=/path/to/exe", allocated_envp[5]);
    assert(allocated_envp[6] == NULL);
    free(allocated_envp);
    free(allocated_env);
  }
  {
    char *test_env[] = {"MY_ENV=1",          "LD_PRELOAD=a", "TERMUX_EXEC__PROC_SELF_EXE=/something",
                        "LD_LIBRARY_PATH=B", "B=C",          NULL};
    char **allocated_envp;
    char *proc_self_exe = "/path/to/exe";
    char *allocated_env;
    setup_env(test_env, &allocated_envp, proc_self_exe, &allocated_env);
    assert(allocated_envp != NULL);
    assert(allocated_env != NULL);
    assert_string_equals("MY_ENV=1", allocated_envp[0]);
    assert_string_equals("LD_PRELOAD=a", allocated_envp[1]);
    assert_string_equals("TERMUX_EXEC__PROC_SELF_EXE=/path/to/exe", allocated_envp[2]);
    assert_string_equals("LD_LIBRARY_PATH=B", allocated_envp[3]);
    assert_string_equals("B=C", allocated_envp[4]);
    assert(allocated_envp[5] == NULL);
    free(allocated_envp);
    free(allocated_env);
  }
}

void test_normalize_path() {
  char expected[PATH_MAX * 2];
  char pwd[PATH_MAX];
  char normalized_path_buffer[PATH_MAX];

  assert(getcwd(pwd, sizeof(pwd)) != NULL);

  sprintf(expected, "%s/path/to/binary", pwd);
  assert_string_equals(expected, normalize_path("path/to/binary", normalized_path_buffer));
  assert_string_equals(expected, normalize_path("path/../path/to/binary", normalized_path_buffer));
  assert_string_equals(expected, normalize_path("./path/to/../to/binary", normalized_path_buffer));
  assert_string_equals(
      "/usr/bin/sh", normalize_path("../../../../../../../../../../../../usr/./bin/../bin/sh", normalized_path_buffer));
}

void test_inspect_file_header() {
  char header[256];
  struct file_header_info info = {.interpreter_arg = NULL};

  sprintf(header, "#!/bin/sh\n");
  inspect_file_header(header, 256, &info);
  assert(!info.is_elf);
  assert(!info.is_non_native_elf);
  assert_string_equals(TERMUX_BIN_PATH "sh", info.interpreter);
  assert(info.interpreter_arg == NULL);

  sprintf(header, "#!/bin/sh -x\n");
  inspect_file_header(header, 256, &info);
  assert(!info.is_elf);
  assert(!info.is_non_native_elf);
  assert_string_equals(TERMUX_BIN_PATH "sh", info.interpreter);
  assert_string_equals("-x", info.interpreter_arg);

  sprintf(header, "#! /bin/sh -x\n");
  inspect_file_header(header, 256, &info);
  assert(!info.is_elf);
  assert(!info.is_non_native_elf);
  assert_string_equals(TERMUX_BIN_PATH "sh", info.interpreter);
  assert_string_equals("-x", info.interpreter_arg);

  sprintf(header, "#!/bin/sh -x \n");
  inspect_file_header(header, 256, &info);
  assert(!info.is_elf);
  assert(!info.is_non_native_elf);
  assert_string_equals(TERMUX_BIN_PATH "sh", info.interpreter);
  assert_string_equals("-x", info.interpreter_arg);

  sprintf(header, "#!/bin/sh -x \n");
  inspect_file_header(header, 256, &info);
  assert(!info.is_elf);
  assert(!info.is_non_native_elf);
  assert_string_equals(TERMUX_BIN_PATH "sh", info.interpreter);
  assert_string_equals("-x", info.interpreter_arg);

  info.interpreter = NULL;
  info.interpreter_arg = NULL;
  // An ELF header for a 32-bit file.
  // See https://en.wikipedia.org/wiki/Executable_and_Linkable_Format#File_header
  sprintf(header, "\177ELF");
  // Native instruction set:
  header[0x12] = EM_NATIVE;
  header[0x13] = 0;
  inspect_file_header(header, 256, &info);
  assert(info.is_elf);
  assert(!info.is_non_native_elf);
  assert(info.interpreter == NULL);
  assert(info.interpreter_arg == NULL);

  info.interpreter = NULL;
  info.interpreter_arg = NULL;
  // An ELF header for a 64-bit file.
  // See https://en.wikipedia.org/wiki/Executable_and_Linkable_Format#File_header
  sprintf(header, "\177ELF");
  // 'Fujitsu MMA Multimedia Accelerator' instruction set - likely non-native.
  header[0x12] = 0x36;
  header[0x13] = 0;
  inspect_file_header(header, 256, &info);
  assert(info.is_elf);
  assert(info.is_non_native_elf);
  assert(info.interpreter == NULL);
  assert(info.interpreter_arg == NULL);
}

int main() {
  test_starts_with();
  test_termux_rewrite_executable();
  test_setup_env();
  test_normalize_path();
  test_inspect_file_header();
  return 0;
}
#endif
