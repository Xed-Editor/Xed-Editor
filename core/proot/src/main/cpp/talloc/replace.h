/*
   Unix SMB/CIFS implementation.

   macros to go along with the lib/replace/ portability layer code

   Copyright (C) Andrew Tridgell 2005
   Copyright (C) Jelmer Vernooij 2006-2008
   Copyright (C) Jeremy Allison 2007.

     ** NOTE! The following LGPL license applies to the replace
     ** library. This does NOT imply that all of Samba is released
     ** under the LGPL

   This library is free software; you can redistribute it and/or
   modify it under the terms of the GNU Lesser General Public
   License as published by the Free Software Foundation; either
   version 3 of the License, or (at your option) any later version.

   This library is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
   Lesser General Public License for more details.

   You should have received a copy of the GNU Lesser General Public
   License along with this library; if not, see <http://www.gnu.org/licenses/>.
*/

#ifndef _LIBREPLACE_REPLACE_H
#define _LIBREPLACE_REPLACE_H

#ifndef NO_CONFIG_H
#include "config.h"
#endif

#ifdef HAVE_STANDARDS_H
#include <standards.h>
#endif

/*
 * Needs to be defined before std*.h and string*.h are included
 * As it's also needed when Python.h is the first header we
 * require a global -D__STDC_WANT_LIB_EXT1__=1
 */
#if __STDC_WANT_LIB_EXT1__ != 1
#error -D__STDC_WANT_LIB_EXT1__=1 required
#endif

#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <errno.h>

#ifndef HAVE_DECL_EWOULDBLOCK
#define EWOULDBLOCK EAGAIN
#endif

#if defined(_MSC_VER) || defined(__MINGW32__)
#include "win32_replace.h"
#endif


#ifdef HAVE_INTTYPES_H
#define __STDC_FORMAT_MACROS
#include <inttypes.h>
#elif defined(HAVE_STDINT_H)
#include <stdint.h>
/* force off HAVE_INTTYPES_H so that roken doesn't try to include both,
   which causes a warning storm on irix */
#undef HAVE_INTTYPES_H
#endif

#ifdef HAVE_MALLOC_H
#include <malloc.h>
#endif

#ifndef __PRI64_PREFIX
# if __WORDSIZE == 64 && ! defined __APPLE__
#  define __PRI64_PREFIX	"l"
# else
#  define __PRI64_PREFIX	"ll"
# endif
#endif

/* Decimal notation.  */
#ifndef PRId8
# define PRId8		"d"
#endif
#ifndef PRId16
# define PRId16		"d"
#endif
#ifndef PRId32
# define PRId32		"d"
#endif
#ifndef PRId64
# define PRId64		__PRI64_PREFIX "d"
#endif

#ifndef PRIi8
# define PRIi8		"i"
#endif
#ifndef PRIi16
# define PRIi16		"i"
#endif
#ifndef PRIi32
# define PRIi32		"i"
#endif
#ifndef PRIi64
# define PRIi64		__PRI64_PREFIX "i"
#endif

#ifndef PRIu8
# define PRIu8		"u"
#endif
#ifndef PRIu16
# define PRIu16		"u"
#endif
#ifndef PRIu32
# define PRIu32		"u"
#endif
#ifndef PRIu64
# define PRIu64		__PRI64_PREFIX "u"
#endif

#ifndef SCNd8
# define SCNd8		"hhd"
#endif
#ifndef SCNd16
# define SCNd16		"hd"
#endif
#ifndef SCNd32
# define SCNd32		"d"
#endif
#ifndef SCNd64
# define SCNd64		__PRI64_PREFIX "d"
#endif

#ifndef SCNi8
# define SCNi8		"hhi"
#endif
#ifndef SCNi16
# define SCNi16		"hi"
#endif
#ifndef SCNi32
# define SCNi32		"i"
#endif
#ifndef SCNi64
# define SCNi64		__PRI64_PREFIX "i"
#endif

#ifndef SCNu8
# define SCNu8		"hhu"
#endif
#ifndef SCNu16
# define SCNu16		"hu"
#endif
#ifndef SCNu32
# define SCNu32		"u"
#endif
#ifndef SCNu64
# define SCNu64		__PRI64_PREFIX "u"
#endif

#ifdef HAVE_BSD_STRING_H
#include <bsd/string.h>
#endif

#ifdef HAVE_BSD_UNISTD_H
#include <bsd/unistd.h>
#endif

#ifdef HAVE_UNISTD_H
#include <unistd.h>
#endif

#ifdef HAVE_STRING_H
#include <string.h>
#endif

#ifdef HAVE_STRINGS_H
#include <strings.h>
#endif

#ifdef HAVE_SYS_TYPES_H
#include <sys/types.h>
#endif

#ifdef HAVE_SYS_SYSMACROS_H
#include <sys/sysmacros.h>
#endif

#ifdef HAVE_SETPROCTITLE_H
#include <setproctitle.h>
#endif

#if STDC_HEADERS
#include <stdlib.h>
#include <stddef.h>
#endif

#ifdef HAVE_LINUX_TYPES_H
/*
 * This is needed as some broken header files require this to be included early
 */
#include <linux/types.h>
#endif

#ifndef HAVE_STRERROR
extern const char *const sys_errlist[];
#define strerror(i) sys_errlist[i]
#endif

#ifndef HAVE_ERRNO_DECL
extern int errno;
#endif

#ifndef HAVE_STRDUP
#define strdup rep_strdup
char *rep_strdup(const char *s);
#endif

#ifndef HAVE_MEMMOVE
#define memmove rep_memmove
void *rep_memmove(void *dest,const void *src,int size);
#endif

#ifndef HAVE_MEMMEM
#define memmem rep_memmem
void *rep_memmem(const void *haystack, size_t haystacklen,
		 const void *needle, size_t needlelen);
#endif

#ifndef HAVE_MEMALIGN
#define memalign rep_memalign
void *rep_memalign(size_t boundary, size_t size);
#endif

#ifndef HAVE_MKTIME
#define mktime rep_mktime
/* prototype is in "system/time.h" */
#endif

#ifndef HAVE_TIMEGM
#define timegm rep_timegm
/* prototype is in "system/time.h" */
#endif

#ifndef HAVE_UTIME
#define utime rep_utime
/* prototype is in "system/time.h" */
#endif

#ifndef HAVE_UTIMES
#define utimes rep_utimes
/* prototype is in "system/time.h" */
#endif

#ifndef HAVE_STRLCPY
#define strlcpy rep_strlcpy
size_t rep_strlcpy(char *d, const char *s, size_t bufsize);
#endif

#ifndef HAVE_STRLCAT
#define strlcat rep_strlcat
size_t rep_strlcat(char *d, const char *s, size_t bufsize);
#endif

#ifndef HAVE_CLOSEFROM
#define closefrom rep_closefrom
int rep_closefrom(int lower);
#endif


#if (defined(BROKEN_STRNDUP) || !defined(HAVE_STRNDUP))
#undef HAVE_STRNDUP
#define strndup rep_strndup
char *rep_strndup(const char *s, size_t n);
#endif

#if (defined(BROKEN_STRNLEN) || !defined(HAVE_STRNLEN))
#undef HAVE_STRNLEN
#define strnlen rep_strnlen
size_t rep_strnlen(const char *s, size_t n);
#endif

#if !defined(HAVE_DECL_ENVIRON)
# ifdef __APPLE__
#   include <crt_externs.h>
#   define environ (*_NSGetEnviron())
# else /* __APPLE__ */
extern char **environ;
# endif /* __APPLE */
#endif /* !defined(HAVE_DECL_ENVIRON) */

#ifndef HAVE_SETENV
#define setenv rep_setenv
int rep_setenv(const char *name, const char *value, int overwrite);
#else
#ifndef HAVE_SETENV_DECL
int setenv(const char *name, const char *value, int overwrite);
#endif
#endif

#ifndef HAVE_UNSETENV
#define unsetenv rep_unsetenv
int rep_unsetenv(const char *name);
#endif

#ifndef HAVE_SETEUID
#define seteuid rep_seteuid
int rep_seteuid(uid_t);
#endif

#ifndef HAVE_SETEGID
#define setegid rep_setegid
int rep_setegid(gid_t);
#endif

#if (defined(USE_SETRESUID) && !defined(HAVE_SETRESUID_DECL))
/* stupid glibc */
int setresuid(uid_t ruid, uid_t euid, uid_t suid);
#endif
#if (defined(USE_SETRESUID) && !defined(HAVE_SETRESGID_DECL))
int setresgid(gid_t rgid, gid_t egid, gid_t sgid);
#endif

#ifndef HAVE_CHOWN
#define chown rep_chown
int rep_chown(const char *path, uid_t uid, gid_t gid);
#endif

#ifndef HAVE_CHROOT
#define chroot rep_chroot
int rep_chroot(const char *dirname);
#endif

#ifndef HAVE_LINK
#define link rep_link
int rep_link(const char *oldpath, const char *newpath);
#endif

#ifndef HAVE_READLINK
#define readlink rep_readlink
ssize_t rep_readlink(const char *path, char *buf, size_t bufsize);
#endif

#ifndef HAVE_SYMLINK
#define symlink rep_symlink
int rep_symlink(const char *oldpath, const char *newpath);
#endif

#ifndef HAVE_REALPATH
#define realpath rep_realpath
char *rep_realpath(const char *path, char *resolved_path);
#endif

#ifndef HAVE_LCHOWN
#define lchown rep_lchown
int rep_lchown(const char *fname,uid_t uid,gid_t gid);
#endif

#ifdef HAVE_UNIX_H
#include <unix.h>
#endif

#ifndef HAVE_SETLINEBUF
#define setlinebuf rep_setlinebuf
void rep_setlinebuf(FILE *);
#endif

#ifndef HAVE_STRCASESTR
#define strcasestr rep_strcasestr
char *rep_strcasestr(const char *haystack, const char *needle);
#endif

#ifndef HAVE_STRSEP
#define strsep rep_strsep
char *rep_strsep(char **pps, const char *delim);
#endif

#ifndef HAVE_STRTOK_R
#define strtok_r rep_strtok_r
char *rep_strtok_r(char *s, const char *delim, char **save_ptr);
#endif



#ifndef HAVE_STRTOLL
#define strtoll rep_strtoll
long long int rep_strtoll(const char *str, char **endptr, int base);
#else
#ifdef HAVE_BSD_STRTOLL
#define strtoll rep_strtoll
long long int rep_strtoll(const char *str, char **endptr, int base);
#endif
#endif

#ifndef HAVE_STRTOULL
#define strtoull rep_strtoull
unsigned long long int rep_strtoull(const char *str, char **endptr, int base);
#else
#ifdef HAVE_BSD_STRTOLL /* yes, it's not HAVE_BSD_STRTOULL */
#define strtoull rep_strtoull
unsigned long long int rep_strtoull(const char *str, char **endptr, int base);
#endif
#endif

#ifndef HAVE_FTRUNCATE
#define ftruncate rep_ftruncate
int rep_ftruncate(int,off_t);
#endif

#ifndef HAVE_INITGROUPS
#define initgroups rep_initgroups
int rep_initgroups(char *name, gid_t id);
#endif

#ifndef HAVE_DLERROR
#define dlerror rep_dlerror
char *rep_dlerror(void);
#endif

#ifndef HAVE_DLOPEN
#define dlopen rep_dlopen
#ifdef DLOPEN_TAKES_UNSIGNED_FLAGS
void *rep_dlopen(const char *name, unsigned int flags);
#else
void *rep_dlopen(const char *name, int flags);
#endif
#endif

#ifndef HAVE_DLSYM
#define dlsym rep_dlsym
void *rep_dlsym(void *handle, const char *symbol);
#endif

#ifndef HAVE_DLCLOSE
#define dlclose rep_dlclose
int rep_dlclose(void *handle);
#endif

#ifndef HAVE_SOCKETPAIR
#define socketpair rep_socketpair
/* prototype is in system/network.h */
#endif

/* for old gcc releases that don't have the feature test macro __has_attribute */
#ifndef __has_attribute
#define __has_attribute(x) 0
#endif

#ifndef PRINTF_ATTRIBUTE
#if __has_attribute(format) || (__GNUC__ >= 3)
/** Use gcc attribute to check printf fns.  a1 is the 1-based index of
 * the parameter containing the format, and a2 the index of the first
 * argument. Note that some gcc 2.x versions don't handle this
 * properly **/
#define PRINTF_ATTRIBUTE(a1, a2) __attribute__ ((format (__printf__, a1, a2)))
#else
#define PRINTF_ATTRIBUTE(a1, a2)
#endif
#endif

#ifndef _DEPRECATED_
#if __has_attribute(deprecated) || (__GNUC__ >= 3)
#define _DEPRECATED_ __attribute__ ((deprecated))
#else
#define _DEPRECATED_
#endif
#endif

#if !defined(HAVE_VDPRINTF) || !defined(HAVE_C99_VSNPRINTF)
#define vdprintf rep_vdprintf
int rep_vdprintf(int fd, const char *format, va_list ap) PRINTF_ATTRIBUTE(2,0);
#endif

#if !defined(HAVE_DPRINTF) || !defined(HAVE_C99_VSNPRINTF)
#define dprintf rep_dprintf
int rep_dprintf(int fd, const char *format, ...) PRINTF_ATTRIBUTE(2,3);
#endif

#if !defined(HAVE_VASPRINTF) || !defined(HAVE_C99_VSNPRINTF)
#define vasprintf rep_vasprintf
int rep_vasprintf(char **ptr, const char *format, va_list ap) PRINTF_ATTRIBUTE(2,0);
#endif

#if !defined(HAVE_SNPRINTF) || !defined(HAVE_C99_VSNPRINTF)
#define snprintf rep_snprintf
int rep_snprintf(char *,size_t ,const char *, ...) PRINTF_ATTRIBUTE(3,4);
#endif

#if !defined(HAVE_VSNPRINTF) || !defined(HAVE_C99_VSNPRINTF)
#define vsnprintf rep_vsnprintf
int rep_vsnprintf(char *,size_t ,const char *, va_list ap) PRINTF_ATTRIBUTE(3,0);
#endif

#if !defined(HAVE_ASPRINTF) || !defined(HAVE_C99_VSNPRINTF)
#define asprintf rep_asprintf
int rep_asprintf(char **,const char *, ...) PRINTF_ATTRIBUTE(2,3);
#endif

#if !defined(HAVE_C99_VSNPRINTF)
#ifdef REPLACE_BROKEN_PRINTF
/*
 * We do not redefine printf by default
 * as it breaks the build if system headers
 * use __attribute__((format(printf, 3, 0)))
 * instead of __attribute__((format(__printf__, 3, 0)))
 */
#define printf rep_printf
#endif
int rep_printf(const char *, ...) PRINTF_ATTRIBUTE(1,2);
#endif

#if !defined(HAVE_C99_VSNPRINTF)
#define fprintf rep_fprintf
int rep_fprintf(FILE *stream, const char *, ...) PRINTF_ATTRIBUTE(2,3);
#endif

#ifndef HAVE_VSYSLOG
#ifdef HAVE_SYSLOG
#define vsyslog rep_vsyslog
void rep_vsyslog (int facility_priority, const char *format, va_list arglist) PRINTF_ATTRIBUTE(2,0);
#endif
#endif

/* we used to use these fns, but now we have good replacements
   for snprintf and vsnprintf */
#define slprintf snprintf


#ifndef HAVE_VA_COPY
#undef va_copy
#ifdef HAVE___VA_COPY
#define va_copy(dest, src) __va_copy(dest, src)
#else
#define va_copy(dest, src) (dest) = (src)
#endif
#endif

#ifndef HAVE_VOLATILE
#define volatile
#endif

#ifndef HAVE_COMPARISON_FN_T
typedef int (*comparison_fn_t)(const void *, const void *);
#endif

#ifndef HAVE_WORKING_STRPTIME
#define strptime rep_strptime
struct tm;
char *rep_strptime(const char *buf, const char *format, struct tm *tm);
#endif

#ifndef HAVE_DUP2
#define dup2 rep_dup2
int rep_dup2(int oldfd, int newfd);
#endif

/* Load header file for dynamic linking stuff */
#ifdef HAVE_DLFCN_H
#include <dlfcn.h>
#endif

#ifndef RTLD_LAZY
#define RTLD_LAZY 0
#endif
#ifndef RTLD_NOW
#define RTLD_NOW 0
#endif
#ifndef RTLD_GLOBAL
#define RTLD_GLOBAL 0
#endif

#ifndef HAVE_SECURE_MKSTEMP
#define mkstemp(path) rep_mkstemp(path)
int rep_mkstemp(char *temp);
#endif

#ifndef HAVE_MKDTEMP
#define mkdtemp rep_mkdtemp
char *rep_mkdtemp(char *template);
#endif

#ifndef HAVE_PREAD
#define pread rep_pread
ssize_t rep_pread(int __fd, void *__buf, size_t __nbytes, off_t __offset);
#define LIBREPLACE_PREAD_REPLACED 1
#else
#define LIBREPLACE_PREAD_NOT_REPLACED 1
#endif

#ifndef HAVE_PWRITE
#define pwrite rep_pwrite
ssize_t rep_pwrite(int __fd, const void *__buf, size_t __nbytes, off_t __offset);
#define LIBREPLACE_PWRITE_REPLACED 1
#else
#define LIBREPLACE_PWRITE_NOT_REPLACED 1
#endif

#if !defined(HAVE_INET_NTOA) || defined(REPLACE_INET_NTOA)
#define inet_ntoa rep_inet_ntoa
/* prototype is in "system/network.h" */
#endif

#ifndef HAVE_INET_PTON
#define inet_pton rep_inet_pton
/* prototype is in "system/network.h" */
#endif

#ifndef HAVE_INET_NTOP
#define inet_ntop rep_inet_ntop
/* prototype is in "system/network.h" */
#endif

#ifndef HAVE_INET_ATON
#define inet_aton rep_inet_aton
/* prototype is in "system/network.h" */
#endif

#ifndef HAVE_CONNECT
#define connect rep_connect
/* prototype is in "system/network.h" */
#endif

#ifndef HAVE_GETHOSTBYNAME
#define gethostbyname rep_gethostbyname
/* prototype is in "system/network.h" */
#endif

#ifndef HAVE_GETIFADDRS
#define getifaddrs rep_getifaddrs
/* prototype is in "system/network.h" */
#endif

#ifndef HAVE_FREEIFADDRS
#define freeifaddrs rep_freeifaddrs
/* prototype is in "system/network.h" */
#endif

#ifndef HAVE_GET_CURRENT_DIR_NAME
#define get_current_dir_name rep_get_current_dir_name
char *rep_get_current_dir_name(void);
#endif

#if (!defined(HAVE_STRERROR_R) || !defined(STRERROR_R_XSI_NOT_GNU))
#define strerror_r rep_strerror_r
int rep_strerror_r(int errnum, char *buf, size_t buflen);
#endif

#if !defined(HAVE_CLOCK_GETTIME)
#define clock_gettime rep_clock_gettime
#endif

#ifdef HAVE_LIMITS_H
#include <limits.h>
#endif

#ifdef HAVE_SYS_PARAM_H
#include <sys/param.h>
#endif

/* The extra casts work around common compiler bugs.  */
#define _TYPE_SIGNED(t) (! ((t) 0 < (t) -1))
/* The outer cast is needed to work around a bug in Cray C 5.0.3.0.
   It is necessary at least when t == time_t.  */
#define _TYPE_MINIMUM(t) ((t) (_TYPE_SIGNED (t) \
  			      ? ~ (t) 0 << (sizeof (t) * CHAR_BIT - 1) : (t) 0))
#define _TYPE_MAXIMUM(t) ((t) (~ (t) 0 - _TYPE_MINIMUM (t)))

#ifndef UINT16_MAX
#define UINT16_MAX 65535
#endif

#ifndef UINT32_MAX
#define UINT32_MAX (4294967295U)
#endif

#ifndef UINT64_MAX
#define UINT64_MAX ((uint64_t)-1)
#endif

#ifndef INT64_MAX
#define INT64_MAX 9223372036854775807LL
#endif

#ifndef CHAR_BIT
#define CHAR_BIT 8
#endif

#ifndef INT32_MAX
#define INT32_MAX _TYPE_MAXIMUM(int32_t)
#endif

#ifdef HAVE_STDBOOL_H
#include <stdbool.h>
#endif

#ifndef HAVE_BOOL
#error Need a real boolean type
#endif

#if !defined(HAVE_INTPTR_T)
typedef long long intptr_t ;
#define __intptr_t_defined
#endif

#if !defined(HAVE_UINTPTR_T)
typedef unsigned long long uintptr_t ;
#define __uintptr_t_defined
#endif

#if !defined(HAVE_PTRDIFF_T)
typedef unsigned long long ptrdiff_t ;
#endif

/*
 * to prevent <rpcsvc/yp_prot.h> from doing a redefine of 'bool'
 *
 * IRIX, HPUX, MacOS 10 and Solaris need BOOL_DEFINED
 * Tru64 needs _BOOL_EXISTS
 * AIX needs _BOOL,_TRUE,_FALSE
 */
#ifndef BOOL_DEFINED
#define BOOL_DEFINED
#endif
#ifndef _BOOL_EXISTS
#define _BOOL_EXISTS
#endif
#ifndef _BOOL
#define _BOOL
#endif

#ifndef __bool_true_false_are_defined
#define __bool_true_false_are_defined
#endif

#ifndef true
#define true (1)
#endif
#ifndef false
#define false (0)
#endif

#ifndef _TRUE
#define _TRUE true
#endif
#ifndef _FALSE
#define _FALSE false
#endif

#ifndef HAVE_FUNCTION_MACRO
#ifdef HAVE_func_MACRO
#define __FUNCTION__ __func__
#else
#define __FUNCTION__ ("")
#endif
#endif


#ifndef MIN
#define MIN(a,b) ((a)<(b)?(a):(b))
#endif

#ifndef MAX
#define MAX(a,b) ((a)>(b)?(a):(b))
#endif

#if !defined(HAVE_VOLATILE)
#define volatile
#endif

/**
  this is a warning hack. The idea is to use this everywhere that we
  get the "discarding const" warning from gcc. That doesn't actually
  fix the problem of course, but it means that when we do get to
  cleaning them up we can do it by searching the code for
  discard_const.

  It also means that other error types aren't as swamped by the noise
  of hundreds of const warnings, so we are more likely to notice when
  we get new errors.

  Please only add more uses of this macro when you find it
  _really_ hard to fix const warnings. Our aim is to eventually use
  this function in only a very few places.

  Also, please call this via the discard_const_p() macro interface, as that
  makes the return type safe.
*/
#define discard_const(ptr) ((void *)((uintptr_t)(ptr)))

/** Type-safe version of discard_const */
#define discard_const_p(type, ptr) ((type *)discard_const(ptr))

#ifndef __STRING
#define __STRING(x)    #x
#endif

#ifndef __STRINGSTRING
#define __STRINGSTRING(x) __STRING(x)
#endif

#ifndef __LINESTR__
#define __LINESTR__ __STRINGSTRING(__LINE__)
#endif

#ifndef __location__
#define __location__ __FILE__ ":" __LINESTR__
#endif

/**
 * Zero a structure.
 */
#define ZERO_STRUCT(x) memset_s((char *)&(x), sizeof(x), 0, sizeof(x))

/**
 * Zero a structure given a pointer to the structure.
 */
#define ZERO_STRUCTP(x) do { \
	if ((x) != NULL) { \
		memset_s((char *)(x), sizeof(*(x)), 0, sizeof(*(x))); \
	} \
} while(0)

/**
 * Zero a structure given a pointer to the structure - no zero check
 */
#define ZERO_STRUCTPN(x) memset_s((char *)(x), sizeof(*(x)), 0, sizeof(*(x)))

/**
 * Zero an array - note that sizeof(array) must work - ie. it must not be a
 * pointer
 */
#define ZERO_ARRAY(x) memset_s((char *)(x), sizeof(x), 0, sizeof(x))

/**
 * Zero a given len of an array
 */
#define ZERO_ARRAY_LEN(x, l) memset_s((char *)(x), (l), 0, (l))

/**
 * Explicitly zero data from memory. This is guaranteed to be not optimized
 * away.
 */
#define BURN_DATA(x) memset_s((char *)&(x), sizeof(x), 0, sizeof(x))

/**
 * Explicitly zero data from memory. This is guaranteed to be not optimized
 * away.
 */
#define BURN_DATA_SIZE(x, s) memset_s((char *)&(x), (s), 0, (s))

/**
 * Explicitly zero data from memory. This is guaranteed to be not optimized
 * away.
 */
#define BURN_PTR_SIZE(x, s) memset_s((x), (s), 0, (s))

/**
 * Explicitly zero data in string. This is guaranteed to be not optimized
 * away.
 */
#define BURN_STR(x)	do { \
				if ((x) != NULL) { \
					size_t s = strlen(x); \
					memset_s((x), s, 0, s); \
				} \
			} while(0)

/**
 * Work out how many elements there are in a static array.
 */
#ifdef ARRAY_SIZE
#undef ARRAY_SIZE
#endif
#define ARRAY_SIZE(a) (sizeof(a)/sizeof(a[0]))

/**
 * Remove an array element by moving the rest one down
 */
#define ARRAY_DEL_ELEMENT(a,i,n) \
if((i)<((n)-1)){memmove(&((a)[(i)]),&((a)[(i)+1]),(sizeof(*(a))*((n)-(i)-1)));}

/**
 * Insert an array element by moving the rest one up
 *
 */
#define ARRAY_INSERT_ELEMENT(__array,__old_last_idx,__new_elem,__new_idx) do { \
	if ((__new_idx) < (__old_last_idx)) { \
		const void *__src = &((__array)[(__new_idx)]); \
		void *__dst = &((__array)[(__new_idx)+1]); \
		size_t __num = (__old_last_idx)-(__new_idx); \
		size_t __len = sizeof(*(__array)) * __num; \
		memmove(__dst, __src, __len); \
	} \
	(__array)[(__new_idx)] = (__new_elem); \
} while(0)

/**
 * Pointer difference macro
 */
#define PTR_DIFF(p1,p2) ((ptrdiff_t)(((const char *)(p1)) - (const char *)(p2)))

#ifdef __COMPAR_FN_T
#define QSORT_CAST (__compar_fn_t)
#endif

#ifndef QSORT_CAST
#define QSORT_CAST (int (*)(const void *, const void *))
#endif

#ifndef PATH_MAX
#define PATH_MAX 1024
#endif

#ifndef MAX_DNS_NAME_LENGTH
#define MAX_DNS_NAME_LENGTH 256 /* Actually 255 but +1 for terminating null. */
#endif

#ifdef HAVE_CRYPT_H
#include <crypt.h>
#endif

/* these macros gain us a few percent of speed on gcc */
#if (__GNUC__ >= 3)
/* the strange !! is to ensure that __builtin_expect() takes either 0 or 1
   as its first argument */
#ifndef likely
#define likely(x)   __builtin_expect(!!(x), 1)
#endif
#ifndef unlikely
#define unlikely(x) __builtin_expect(!!(x), 0)
#endif
#else
#ifndef likely
#define likely(x) (x)
#endif
#ifndef unlikely
#define unlikely(x) (x)
#endif
#endif

#ifndef HAVE_FDATASYNC
#define fdatasync(fd) fsync(fd)
#elif !defined(HAVE_DECL_FDATASYNC)
int fdatasync(int );
#endif

/* these are used to mark symbols as local to a shared lib, or
 * publicly available via the shared lib API */
#ifndef _PUBLIC_
#ifdef HAVE_VISIBILITY_ATTR
#define _PUBLIC_ __attribute__((visibility("default")))
#else
#define _PUBLIC_
#endif
#endif

#ifndef _PRIVATE_
#ifdef HAVE_VISIBILITY_ATTR
#  define _PRIVATE_ __attribute__((visibility("hidden")))
#else
#  define _PRIVATE_
#endif
#endif

#ifndef HAVE_POLL
#define poll rep_poll
/* prototype is in "system/network.h" */
#endif

#ifndef HAVE_GETPEEREID
#define getpeereid rep_getpeereid
int rep_getpeereid(int s, uid_t *uid, gid_t *gid);
#endif

#ifndef HAVE_USLEEP
#define usleep rep_usleep
typedef long useconds_t;
int usleep(useconds_t);
#endif

#ifndef HAVE_SETPROCTITLE
#define setproctitle rep_setproctitle
void rep_setproctitle(const char *fmt, ...) PRINTF_ATTRIBUTE(1, 2);
#endif

#ifndef HAVE_SETPROCTITLE_INIT
#define setproctitle_init rep_setproctitle_init
void rep_setproctitle_init(int argc, char *argv[], char *envp[]);
#endif

#ifndef HAVE_MEMSET_S
#define memset_s rep_memset_s
int rep_memset_s(void *dest, size_t destsz, int ch, size_t count);
#endif

#ifndef HAVE_GETPROGNAME
#define getprogname rep_getprogname
const char *rep_getprogname(void);
#endif

#ifndef HAVE_COPY_FILE_RANGE
#define copy_file_range rep_copy_file_range
ssize_t rep_copy_file_range(int fd_in,
			    loff_t *off_in,
			    int fd_out,
			    loff_t *off_out,
			    size_t len,
			    unsigned int flags);
#endif /* HAVE_COPY_FILE_RANGE */


#define copy_reflink rep_copy_reflink

ssize_t rep_copy_reflink(int src_fd,
			 off_t src_off,
			 int dst_fd,
			 off_t dst_off,
			 off_t to_copy);

#ifndef FALL_THROUGH
# ifdef HAVE_FALLTHROUGH_ATTRIBUTE
#  define FALL_THROUGH __attribute__ ((fallthrough))
# else /* HAVE_FALLTHROUGH_ATTRIBUTE */
#  define FALL_THROUGH ((void)0)
# endif /* HAVE_FALLTHROUGH_ATTRIBUTE */
#endif /* FALL_THROUGH */

struct __rep_cwrap_enabled_state {
	const char *fnname;
	bool cached;
	bool retval;
};

static inline bool __rep_cwrap_enabled_fn(struct __rep_cwrap_enabled_state *state)
{
	bool (*__wrapper_enabled_fn)(void) = NULL;

	if (state->cached) {
		return state->retval;
	}
	state->retval = false;
	state->cached = true;

	__wrapper_enabled_fn = (bool (*)(void))dlsym(RTLD_DEFAULT, state->fnname);
	if (__wrapper_enabled_fn == NULL) {
		return state->retval;
	}

	state->retval = __wrapper_enabled_fn();
	return state->retval;
}

static inline bool nss_wrapper_enabled(void)
{
	struct __rep_cwrap_enabled_state state = {
		.fnname = "nss_wrapper_enabled",
	};
	return __rep_cwrap_enabled_fn(&state);
}
static inline bool nss_wrapper_hosts_enabled(void)
{
	struct __rep_cwrap_enabled_state state = {
		.fnname = "nss_wrapper_hosts_enabled",
	};
	return __rep_cwrap_enabled_fn(&state);
}
static inline bool socket_wrapper_enabled(void)
{
	struct __rep_cwrap_enabled_state state = {
		.fnname = "socket_wrapper_enabled",
	};
	return __rep_cwrap_enabled_fn(&state);
}
static inline bool uid_wrapper_enabled(void)
{
	struct __rep_cwrap_enabled_state state = {
		.fnname = "uid_wrapper_enabled",
	};
	return __rep_cwrap_enabled_fn(&state);
}

static inline bool _hexcharval(char c, uint8_t *val)
{
	if ((c >= '0') && (c <= '9')) { *val = c - '0';      return true; }
        c &= 0xDF; /* map lower to upper case -- thanks libnfs :-) */
	if ((c >= 'A') && (c <= 'F')) { *val = c - 'A' + 10; return true; }
	return false;
}

static inline bool hex_byte(const char *in, uint8_t *out)
{
	uint8_t hi=0, lo=0;
	bool ok = _hexcharval(in[0], &hi) && _hexcharval(in[1], &lo);
	*out = (hi<<4)+lo;
	return ok;
}

extern const char hexchars_lower[];
extern const char hexchars_upper[];

/* Needed for Solaris atomic_add_XX functions. */
#if defined(HAVE_SYS_ATOMIC_H)
#include <sys/atomic.h>
#endif

/*
 * This handles the case of missing pthread support and ensures code can use
 * __thread unconditionally, such that when built on a platform without pthread
 * support, the __thread qualifier is an empty define.
 */
#ifndef HAVE___THREAD
# ifdef HAVE_PTHREAD
# error Configure failed to detect pthread library with missing TLS support
# endif
#define HAVE___THREAD
#endif

#endif /* _LIBREPLACE_REPLACE_H */
