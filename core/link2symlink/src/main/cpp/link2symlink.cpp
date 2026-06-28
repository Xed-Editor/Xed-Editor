#include <unistd.h>
#include <fcntl.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <climits>
#include <cerrno>
#include <sys/stat.h>
#include <sys/types.h>
#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "link2symlink"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#define LOGI(...) do { fprintf(stderr, "[link2symlink] INFO: "); fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n"); } while(0)
#define LOGE(...) do { fprintf(stderr, "[link2symlink] ERROR: "); fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n"); } while(0)
#endif

#ifndef AT_EMPTY_PATH
#define AT_EMPTY_PATH 0x1000
#endif
#ifndef AT_SYMLINK_FOLLOW
#define AT_SYMLINK_FOLLOW 0x400
#endif

#define EXPORT __attribute__((visibility("default")))

// Helper to resolve the absolute path of oldpath relative to dirfd.
// Writes the result to out_path, which must be at least PATH_MAX bytes.
// Returns 0 on success, -1 on error (sets errno).
static int resolve_to_absolute(int dirfd, const char *path, int flags, char *out_path) {
    if (!path || !out_path) {
        errno = EINVAL;
        return -1;
    }

    // Handle AT_EMPTY_PATH: if path is empty, reference the fd directly
    if ((flags & AT_EMPTY_PATH) && (path[0] == '\0')) {
        char fd_path[64];
        snprintf(fd_path, sizeof(fd_path), "/proc/self/fd/%d", dirfd);
        ssize_t len = readlink(fd_path, out_path, PATH_MAX - 1);
        if (len == -1) {
            return -1;
        }
        out_path[len] = '\0';
        return 0;
    }

    // If path is already absolute, just copy it
    if (path[0] == '/') {
        strncpy(out_path, path, PATH_MAX - 1);
        out_path[PATH_MAX - 1] = '\0';
        return 0;
    }

    char base_path[PATH_MAX];
    if (dirfd == AT_FDCWD) {
        if (getcwd(base_path, sizeof(base_path)) == nullptr) {
            return -1;
        }
    } else {
        char fd_path[64];
        snprintf(fd_path, sizeof(fd_path), "/proc/self/fd/%d", dirfd);
        ssize_t len = readlink(fd_path, base_path, sizeof(base_path) - 1);
        if (len == -1) {
            return -1;
        }
        base_path[len] = '\0';
    }

    // Concatenate base_path and path
    size_t base_len = strlen(base_path);
    if (base_len > 0 && base_path[base_len - 1] == '/') {
        snprintf(out_path, PATH_MAX, "%s%s", base_path, path);
    } else {
        snprintf(out_path, PATH_MAX, "%s/%s", base_path, path);
    }

    return 0;
}

extern "C" {

EXPORT int link(const char *oldpath, const char *newpath) {
    LOGI("link: Intercepted call link(\"%s\", \"%s\")", oldpath ? oldpath : "NULL", newpath ? newpath : "NULL");

    // Verify source exists
    struct stat st{};
    if (fstatat(AT_FDCWD, oldpath, &st, AT_SYMLINK_NOFOLLOW) != 0) {
        LOGE("link: Source file verification failed (errno: %d, %s)", errno, strerror(errno));
        return -1;
    }

    char target[PATH_MAX];
    if (resolve_to_absolute(AT_FDCWD, oldpath, 0, target) != 0) {
        LOGE("link: Failed to resolve path \"%s\" (errno: %d, %s)", oldpath, errno, strerror(errno));
        return -1;
    }

    LOGI("link: Redirecting to symlink(\"%s\", \"%s\")", target, newpath);
    int ret = symlink(target, newpath);
    if (ret == -1) {
        LOGE("link: symlink failed (errno: %d, %s)", errno, strerror(errno));
    } else {
        LOGI("link: symlink succeeded");
    }
    return ret;
}

EXPORT int linkat(int olddirfd, const char *oldpath, int newdirfd, const char *newpath, int flags) {
    LOGI("linkat: Intercepted call linkat(%d, \"%s\", %d, \"%s\", 0x%x)",
         olddirfd, oldpath ? oldpath : "NULL", newdirfd, newpath ? newpath : "NULL", flags);

    // Verify source exists
    struct stat st{};
    int fstat_flags = 0;
    if (!(flags & AT_SYMLINK_FOLLOW)) {
        fstat_flags |= AT_SYMLINK_NOFOLLOW;
    }
    if (flags & AT_EMPTY_PATH) {
        fstat_flags |= AT_EMPTY_PATH;
    }
    if (fstatat(olddirfd, oldpath, &st, fstat_flags) != 0) {
        LOGE("linkat: Source file verification failed (errno: %d, %s)", errno, strerror(errno));
        return -1;
    }

    char target[PATH_MAX];
    if (resolve_to_absolute(olddirfd, oldpath, flags, target) != 0) {
        LOGE("linkat: Failed to resolve oldpath (errno: %d, %s)", errno, strerror(errno));
        return -1;
    }

    if (flags & AT_SYMLINK_FOLLOW) {
        char final_target[PATH_MAX];
        if (realpath(target, final_target) != nullptr) {
            strncpy(target, final_target, PATH_MAX - 1);
            target[PATH_MAX - 1] = '\0';
        }
    }

    LOGI("linkat: Redirecting to symlinkat(\"%s\", %d, \"%s\")", target, newdirfd, newpath);
    int ret = symlinkat(target, newdirfd, newpath);
    if (ret == -1) {
        LOGE("linkat: symlinkat failed (errno: %d, %s)", errno, strerror(errno));
    } else {
        LOGI("linkat: symlinkat succeeded");
    }
    return ret;
}

}
