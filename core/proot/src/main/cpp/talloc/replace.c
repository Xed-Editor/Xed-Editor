/* 
   Unix SMB/CIFS implementation.
   replacement routines for broken systems
   Copyright (C) Andrew Tridgell 1992-1998
   Copyright (C) Jelmer Vernooij 2005-2008
   Copyright (C) Matthieu Patou  2010

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

#include "replace.h"

#include "system/filesys.h"
#include "system/time.h"
#include "system/network.h"
#include "system/passwd.h"
#include "system/syslog.h"
#include "system/locale.h"
#include "system/wait.h"

#ifdef HAVE_SYS_SYSCALL_H
#include <sys/syscall.h>
#endif

#ifdef _WIN32
#define mkdir(d,m) _mkdir(d)
#endif

void replace_dummy(void);
void replace_dummy(void) {}

#ifndef HAVE_FTRUNCATE
 /*******************************************************************
ftruncate for operating systems that don't have it
********************************************************************/
int rep_ftruncate(int f, off_t l)
{
#ifdef HAVE_CHSIZE
      return chsize(f,l);
#elif defined(F_FREESP)
      struct  flock   fl;

      fl.l_whence = 0;
      fl.l_len = 0;
      fl.l_start = l;
      fl.l_type = F_WRLCK;
      return fcntl(f, F_FREESP, &fl);
#else
#error "you must have a ftruncate function"
#endif
}
#endif /* HAVE_FTRUNCATE */


#ifndef HAVE_STRLCPY
/*
 * Like strncpy but does not 0 fill the buffer and always null
 * terminates. bufsize is the size of the destination buffer.
 * Returns the length of s.
 */
size_t rep_strlcpy(char *d, const char *s, size_t bufsize)
{
	size_t len = strlen(s);
	size_t ret = len;

	if (bufsize <= 0) {
		return 0;
	}
	if (len >= bufsize) {
		len = bufsize - 1;
	}
	memcpy(d, s, len);
	d[len] = 0;
	return ret;
}
#endif

#ifndef HAVE_STRLCAT
/* like strncat but does not 0 fill the buffer and always null 
   terminates. bufsize is the length of the buffer, which should
   be one more than the maximum resulting string length */
size_t rep_strlcat(char *d, const char *s, size_t bufsize)
{
	size_t len1 = strnlen(d, bufsize);
	size_t len2 = strlen(s);
	size_t ret = len1 + len2;

	if (len1+len2 >= bufsize) {
		if (bufsize < (len1+1)) {
			return ret;
		}
		len2 = bufsize - (len1+1);
	}
	if (len2 > 0) {
		memcpy(d+len1, s, len2);
		d[len1+len2] = 0;
	}
	return ret;
}
#endif

#ifndef HAVE_MKTIME
/*******************************************************************
a mktime() replacement for those who don't have it - contributed by 
C.A. Lademann <cal@zls.com>
Corrections by richard.kettlewell@kewill.com
********************************************************************/

#define  MINUTE  60
#define  HOUR    60*MINUTE
#define  DAY             24*HOUR
#define  YEAR    365*DAY
time_t rep_mktime(struct tm *t)
{
  struct tm       *u;
  time_t  epoch = 0;
  int n;
  int             mon [] = { 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31 },
  y, m, i;

  if(t->tm_year < 70)
    return((time_t)-1);

  n = t->tm_year + 1900 - 1;
  epoch = (t->tm_year - 70) * YEAR + 
    ((n / 4 - n / 100 + n / 400) - (1969 / 4 - 1969 / 100 + 1969 / 400)) * DAY;

  y = t->tm_year + 1900;
  m = 0;

  for(i = 0; i < t->tm_mon; i++) {
    epoch += mon [m] * DAY;
    if(m == 1 && y % 4 == 0 && (y % 100 != 0 || y % 400 == 0))
      epoch += DAY;
    
    if(++m > 11) {
      m = 0;
      y++;
    }
  }

  epoch += (t->tm_mday - 1) * DAY;
  epoch += t->tm_hour * HOUR + t->tm_min * MINUTE + t->tm_sec;
  
  if((u = localtime(&epoch)) != NULL) {
    t->tm_sec = u->tm_sec;
    t->tm_min = u->tm_min;
    t->tm_hour = u->tm_hour;
    t->tm_mday = u->tm_mday;
    t->tm_mon = u->tm_mon;
    t->tm_year = u->tm_year;
    t->tm_wday = u->tm_wday;
    t->tm_yday = u->tm_yday;
    t->tm_isdst = u->tm_isdst;
  }

  return(epoch);
}
#endif /* !HAVE_MKTIME */


#ifndef HAVE_INITGROUPS
/****************************************************************************
 some systems don't have an initgroups call 
****************************************************************************/
int rep_initgroups(char *name, gid_t id)
{
#ifndef HAVE_SETGROUPS
	/* yikes! no SETGROUPS or INITGROUPS? how can this work? */
	errno = ENOSYS;
	return -1;
#else /* HAVE_SETGROUPS */

#include <grp.h>

	gid_t *grouplst = NULL;
	int max_gr = NGROUPS_MAX;
	int ret;
	int    i,j;
	struct group *g;
	char   *gr;
	
	if((grouplst = malloc(sizeof(gid_t) * max_gr)) == NULL) {
		errno = ENOMEM;
		return -1;
	}

	grouplst[0] = id;
	i = 1;
	while (i < max_gr && ((g = (struct group *)getgrent()) != (struct group *)NULL)) {
		if (g->gr_gid == id)
			continue;
		j = 0;
		gr = g->gr_mem[0];
		while (gr && (*gr != (char)NULL)) {
			if (strcmp(name,gr) == 0) {
				grouplst[i] = g->gr_gid;
				i++;
				gr = (char *)NULL;
				break;
			}
			gr = g->gr_mem[++j];
		}
	}
	endgrent();
	ret = setgroups(i, grouplst);
	free(grouplst);
	return ret;
#endif /* HAVE_SETGROUPS */
}
#endif /* HAVE_INITGROUPS */


#ifndef HAVE_MEMMOVE
/*******************************************************************
safely copies memory, ensuring no overlap problems.
this is only used if the machine does not have its own memmove().
this is not the fastest algorithm in town, but it will do for our
needs.
********************************************************************/
void *rep_memmove(void *dest,const void *src,int size)
{
	unsigned long d,s;
	int i;
	if (dest==src || !size) return(dest);

	d = (unsigned long)dest;
	s = (unsigned long)src;

	if ((d >= (s+size)) || (s >= (d+size))) {
		/* no overlap */
		memcpy(dest,src,size);
		return(dest);
	}

	if (d < s) {
		/* we can forward copy */
		if (s-d >= sizeof(int) && 
		    !(s%sizeof(int)) && 
		    !(d%sizeof(int)) && 
		    !(size%sizeof(int))) {
			/* do it all as words */
			int *idest = (int *)dest;
			int *isrc = (int *)src;
			size /= sizeof(int);
			for (i=0;i<size;i++) idest[i] = isrc[i];
		} else {
			/* simplest */
			char *cdest = (char *)dest;
			char *csrc = (char *)src;
			for (i=0;i<size;i++) cdest[i] = csrc[i];
		}
	} else {
		/* must backward copy */
		if (d-s >= sizeof(int) && 
		    !(s%sizeof(int)) && 
		    !(d%sizeof(int)) && 
		    !(size%sizeof(int))) {
			/* do it all as words */
			int *idest = (int *)dest;
			int *isrc = (int *)src;
			size /= sizeof(int);
			for (i=size-1;i>=0;i--) idest[i] = isrc[i];
		} else {
			/* simplest */
			char *cdest = (char *)dest;
			char *csrc = (char *)src;
			for (i=size-1;i>=0;i--) cdest[i] = csrc[i];
		}      
	}
	return(dest);
}
#endif /* HAVE_MEMMOVE */

#ifndef HAVE_STRDUP
/****************************************************************************
duplicate a string
****************************************************************************/
char *rep_strdup(const char *s)
{
	size_t len;
	char *ret;

	if (!s) return(NULL);

	len = strlen(s)+1;
	ret = (char *)malloc(len);
	if (!ret) return(NULL);
	memcpy(ret,s,len);
	return(ret);
}
#endif /* HAVE_STRDUP */

#ifndef HAVE_SETLINEBUF
void rep_setlinebuf(FILE *stream)
{
	setvbuf(stream, (char *)NULL, _IOLBF, 0);
}
#endif /* HAVE_SETLINEBUF */

#ifndef HAVE_VSYSLOG
#ifdef HAVE_SYSLOG
void rep_vsyslog (int facility_priority, const char *format, va_list arglist)
{
	char *msg = NULL;
	vasprintf(&msg, format, arglist);
	if (!msg)
		return;
	syslog(facility_priority, "%s", msg);
	free(msg);
}
#endif /* HAVE_SYSLOG */
#endif /* HAVE_VSYSLOG */

#ifndef HAVE_STRNLEN
/**
 Some platforms don't have strnlen
**/
 size_t rep_strnlen(const char *s, size_t max)
{
        size_t len;
  
        for (len = 0; len < max; len++) {
                if (s[len] == '\0') {
                        break;
                }
        }
        return len;  
}
#endif
  
#ifndef HAVE_STRNDUP
/**
 Some platforms don't have strndup.
**/
char *rep_strndup(const char *s, size_t n)
{
	char *ret;
	
	n = strnlen(s, n);
	ret = malloc(n+1);
	if (!ret)
		return NULL;
	memcpy(ret, s, n);
	ret[n] = 0;

	return ret;
}
#endif

#if !defined(HAVE_WAITPID) && defined(HAVE_WAIT4)
int rep_waitpid(pid_t pid,int *status,int options)
{
  return wait4(pid, status, options, NULL);
}
#endif

#ifndef HAVE_SETEUID
int rep_seteuid(uid_t euid)
{
#ifdef HAVE_SETRESUID
	return setresuid(-1, euid, -1);
#else
	errno = ENOSYS;
	return -1;
#endif
}
#endif

#ifndef HAVE_SETEGID
int rep_setegid(gid_t egid)
{
#ifdef HAVE_SETRESGID
	return setresgid(-1, egid, -1);
#else
	errno = ENOSYS;
	return -1;
#endif
}
#endif

/*******************************************************************
os/2 also doesn't have chroot
********************************************************************/
#ifndef HAVE_CHROOT
int rep_chroot(const char *dname)
{
	errno = ENOSYS;
	return -1;
}
#endif

/*****************************************************************
 Possibly replace mkstemp if it is broken.
*****************************************************************/  

#ifndef HAVE_SECURE_MKSTEMP
int rep_mkstemp(char *template)
{
	/* have a reasonable go at emulating it. Hope that
	   the system mktemp() isn't completely hopeless */
	mktemp(template);
	if (template[0] == 0)
		return -1;
	return open(template, O_CREAT|O_EXCL|O_RDWR, 0600);
}
#endif

#ifndef HAVE_MKDTEMP
char *rep_mkdtemp(char *template)
{
	char *dname;
	
	if ((dname = mktemp(template))) {
		if (mkdir(dname, 0700) >= 0) {
			return dname;
		}
	}

	return NULL;
}
#endif

/*****************************************************************
 Watch out: this is not thread safe.
*****************************************************************/

#ifndef HAVE_PREAD
ssize_t rep_pread(int __fd, void *__buf, size_t __nbytes, off_t __offset)
{
	if (lseek(__fd, __offset, SEEK_SET) != __offset) {
		return -1;
	}
	return read(__fd, __buf, __nbytes);
}
#endif

/*****************************************************************
 Watch out: this is not thread safe.
*****************************************************************/

#ifndef HAVE_PWRITE
ssize_t rep_pwrite(int __fd, const void *__buf, size_t __nbytes, off_t __offset)
{
	if (lseek(__fd, __offset, SEEK_SET) != __offset) {
		return -1;
	}
	return write(__fd, __buf, __nbytes);
}
#endif

#ifndef HAVE_STRCASESTR
char *rep_strcasestr(const char *haystack, const char *needle)
{
	const char *s;
	size_t nlen = strlen(needle);
	for (s=haystack;*s;s++) {
		if (toupper(*needle) == toupper(*s) &&
		    strncasecmp(s, needle, nlen) == 0) {
			return (char *)((uintptr_t)s);
		}
	}
	return NULL;
}
#endif

#ifndef HAVE_STRSEP
char *rep_strsep(char **pps, const char *delim)
{
	char *ret = *pps;
	char *p = *pps;

	if (p == NULL) {
		return NULL;
	}
	p += strcspn(p, delim);
	if (*p == '\0') {
		*pps = NULL;
	} else {
		*p = '\0';
		*pps = p + 1;
	}
	return ret;
}
#endif

#ifndef HAVE_STRTOK_R
/* based on GLIBC version, copyright Free Software Foundation */
char *rep_strtok_r(char *s, const char *delim, char **save_ptr)
{
	char *token;

	if (s == NULL) s = *save_ptr;

	s += strspn(s, delim);
	if (*s == '\0') {
		*save_ptr = s;
		return NULL;
	}

	token = s;
	s = strpbrk(token, delim);
	if (s == NULL) {
		*save_ptr = token + strlen(token);
	} else {
		*s = '\0';
		*save_ptr = s + 1;
	}

	return token;
}
#endif


#ifndef HAVE_STRTOLL
long long int rep_strtoll(const char *str, char **endptr, int base)
{
#ifdef HAVE_STRTOQ
	return strtoq(str, endptr, base);
#elif defined(HAVE___STRTOLL) 
	return __strtoll(str, endptr, base);
#elif SIZEOF_LONG == SIZEOF_LONG_LONG
	return (long long int) strtol(str, endptr, base);
#else
# error "You need a strtoll function"
#endif
}
#else
#ifdef HAVE_BSD_STRTOLL
#undef strtoll
long long int rep_strtoll(const char *str, char **endptr, int base)
{
	int saved_errno = errno;
	long long int nb = strtoll(str, endptr, base);
	/* With glibc EINVAL is only returned if base is not ok */
	if (errno == EINVAL) {
		if (base == 0 || (base >1 && base <37)) {
			/* Base was ok so it's because we were not
			 * able to make the conversion.
			 * Let's reset errno.
			 */
			errno = saved_errno;
		}
	}
	return nb;
}
#endif /* HAVE_BSD_STRTOLL */
#endif /* HAVE_STRTOLL */


#ifndef HAVE_STRTOULL
unsigned long long int rep_strtoull(const char *str, char **endptr, int base)
{
#ifdef HAVE_STRTOUQ
	return strtouq(str, endptr, base);
#elif defined(HAVE___STRTOULL) 
	return __strtoull(str, endptr, base);
#elif SIZEOF_LONG == SIZEOF_LONG_LONG
	return (unsigned long long int) strtoul(str, endptr, base);
#else
# error "You need a strtoull function"
#endif
}
#else
#ifdef HAVE_BSD_STRTOLL
#undef strtoull
unsigned long long int rep_strtoull(const char *str, char **endptr, int base)
{
	int saved_errno = errno;
	unsigned long long int nb = strtoull(str, endptr, base);
	/* With glibc EINVAL is only returned if base is not ok */
	if (errno == EINVAL) {
		if (base == 0 || (base >1 && base <37)) {
			/* Base was ok so it's because we were not
			 * able to make the conversion.
			 * Let's reset errno.
			 */
			errno = saved_errno;
		}
	}
	return nb;
}
#endif /* HAVE_BSD_STRTOLL */
#endif /* HAVE_STRTOULL */

#ifndef HAVE_SETENV
int rep_setenv(const char *name, const char *value, int overwrite) 
{
	char *p;
	size_t l1, l2;
	int ret;

	if (!overwrite && getenv(name)) {
		return 0;
	}

	l1 = strlen(name);
	l2 = strlen(value);

	p = malloc(l1+l2+2);
	if (p == NULL) {
		return -1;
	}
	memcpy(p, name, l1);
	p[l1] = '=';
	memcpy(p+l1+1, value, l2);
	p[l1+l2+1] = 0;

	ret = putenv(p);
	if (ret != 0) {
		free(p);
	}

	return ret;
}
#endif

#ifndef HAVE_UNSETENV
int rep_unsetenv(const char *name)
{
	extern char **environ;
	size_t len = strlen(name);
	size_t i, count;

	if (environ == NULL || getenv(name) == NULL) {
		return 0;
	}

	for (i=0;environ[i];i++) /* noop */ ;

	count=i;
	
	for (i=0;i<count;) {
		if (strncmp(environ[i], name, len) == 0 && environ[i][len] == '=') {
			/* note: we do _not_ free the old variable here. It is unsafe to 
			   do so, as the pointer may not have come from malloc */
			memmove(&environ[i], &environ[i+1], (count-i)*sizeof(char *));
			count--;
		} else {
			i++;
		}
	}

	return 0;
}
#endif

#ifndef HAVE_UTIME
int rep_utime(const char *filename, const struct utimbuf *buf)
{
	errno = ENOSYS;
	return -1;
}
#endif

#ifndef HAVE_UTIMES
int rep_utimes(const char *filename, const struct timeval tv[2])
{
	struct utimbuf u;

	u.actime = tv[0].tv_sec;
	if (tv[0].tv_usec > 500000) {
		u.actime += 1;
	}

	u.modtime = tv[1].tv_sec;
	if (tv[1].tv_usec > 500000) {
		u.modtime += 1;
	}

	return utime(filename, &u);
}
#endif

#ifndef HAVE_DUP2
int rep_dup2(int oldfd, int newfd) 
{
	errno = ENOSYS;
	return -1;
}
#endif

#ifndef HAVE_CHOWN
/**
chown isn't used much but OS/2 doesn't have it
**/
int rep_chown(const char *fname, uid_t uid, gid_t gid)
{
	errno = ENOSYS;
	return -1;
}
#endif

#ifndef HAVE_LINK
int rep_link(const char *oldpath, const char *newpath)
{
	errno = ENOSYS;
	return -1;
}
#endif

#ifndef HAVE_READLINK
int rep_readlink(const char *path, char *buf, size_t bufsiz)
{
	errno = ENOSYS;
	return -1;
}
#endif

#ifndef HAVE_SYMLINK
int rep_symlink(const char *oldpath, const char *newpath)
{
	errno = ENOSYS;
	return -1;
}
#endif

#ifndef HAVE_LCHOWN
int rep_lchown(const char *fname,uid_t uid,gid_t gid)
{
	errno = ENOSYS;
	return -1;
}
#endif

#ifndef HAVE_REALPATH
char *rep_realpath(const char *path, char *resolved_path)
{
	/* As realpath is not a system call we can't return ENOSYS. */
	errno = EINVAL;
	return NULL;
}
#endif


#ifndef HAVE_MEMMEM
void *rep_memmem(const void *haystack, size_t haystacklen,
		 const void *needle, size_t needlelen)
{
	if (needlelen == 0) {
		return discard_const(haystack);
	}
	while (haystacklen >= needlelen) {
		char *p = (char *)memchr(haystack, *(const char *)needle,
					 haystacklen-(needlelen-1));
		if (!p) return NULL;
		if (memcmp(p, needle, needlelen) == 0) {
			return p;
		}
		haystack = p+1;
		haystacklen -= (p - (const char *)haystack) + 1;
	}
	return NULL;
}
#endif

#if !defined(HAVE_VDPRINTF) || !defined(HAVE_C99_VSNPRINTF)
int rep_vdprintf(int fd, const char *format, va_list ap)
{
	char *s = NULL;
	int ret;

	vasprintf(&s, format, ap);
	if (s == NULL) {
		errno = ENOMEM;
		return -1;
	}
	ret = write(fd, s, strlen(s));
	free(s);
	return ret;
}
#endif

#if !defined(HAVE_DPRINTF) || !defined(HAVE_C99_VSNPRINTF)
int rep_dprintf(int fd, const char *format, ...)
{
	int ret;
	va_list ap;

	va_start(ap, format);
	ret = vdprintf(fd, format, ap);
	va_end(ap);

	return ret;
}
#endif

#ifndef HAVE_GET_CURRENT_DIR_NAME
char *rep_get_current_dir_name(void)
{
	char buf[PATH_MAX+1];
	char *p;
	p = getcwd(buf, sizeof(buf));
	if (p == NULL) {
		return NULL;
	}
	return strdup(p);
}
#endif

#ifndef HAVE_STRERROR_R
int rep_strerror_r(int errnum, char *buf, size_t buflen)
{
	char *s = strerror(errnum);
	if (strlen(s)+1 > buflen) {
		errno = ERANGE;
		return -1;
	}
	strncpy(buf, s, buflen);
	return 0;
}
#elif (!defined(STRERROR_R_XSI_NOT_GNU))
#undef strerror_r
int rep_strerror_r(int errnum, char *buf, size_t buflen)
{
	char *s = strerror_r(errnum, buf, buflen);
	if (s == NULL) {
		/* Shouldn't happen, should always get a string */
		return EINVAL;
	}
	if (s != buf) {
		strlcpy(buf, s, buflen);
		if (strlen(s) > buflen - 1) {
			return ERANGE;
		}
	}
	return 0;

}
#endif

#ifndef HAVE_CLOCK_GETTIME
int rep_clock_gettime(clockid_t clk_id, struct timespec *tp)
{
	struct timeval tval;
	switch (clk_id) {
		case 0: /* CLOCK_REALTIME :*/
#if defined(HAVE_GETTIMEOFDAY_TZ) || defined(HAVE_GETTIMEOFDAY_TZ_VOID)
			gettimeofday(&tval,NULL);
#else
			gettimeofday(&tval);
#endif
			tp->tv_sec = tval.tv_sec;
			tp->tv_nsec = tval.tv_usec * 1000;
			break;
		default:
			errno = EINVAL;
			return -1;
	}
	return 0;
}
#endif

#ifndef HAVE_MEMALIGN
void *rep_memalign( size_t align, size_t size )
{
#if defined(HAVE_POSIX_MEMALIGN)
	void *p = NULL;
	int ret = posix_memalign( &p, align, size );
	if ( ret == 0 )
		return p;

	return NULL;
#else
	/* On *BSD systems memaligns doesn't exist, but memory will
	 * be aligned on allocations of > pagesize. */
#if defined(SYSCONF_SC_PAGESIZE)
	size_t pagesize = (size_t)sysconf(_SC_PAGESIZE);
#elif defined(HAVE_GETPAGESIZE)
	size_t pagesize = (size_t)getpagesize();
#else
	size_t pagesize = (size_t)-1;
#endif
	if (pagesize == (size_t)-1) {
		errno = ENOSYS;
		return NULL;
	}
	if (size < pagesize) {
		size = pagesize;
	}
	return malloc(size);
#endif
}
#endif

#ifndef HAVE_GETPEEREID
int rep_getpeereid(int s, uid_t *uid, gid_t *gid)
{
#if defined(HAVE_PEERCRED)
	struct ucred cred;
	socklen_t cred_len = sizeof(struct ucred);
	int ret;

#undef getsockopt
	ret = getsockopt(s, SOL_SOCKET, SO_PEERCRED, (void *)&cred, &cred_len);
	if (ret != 0) {
		return -1;
	}

	if (cred_len != sizeof(struct ucred)) {
		errno = EINVAL;
		return -1;
	}

	*uid = cred.uid;
	*gid = cred.gid;
	return 0;
#else
	errno = ENOSYS;
	return -1;
#endif
}
#endif

#ifndef HAVE_USLEEP
int rep_usleep(useconds_t sec)
{
	struct timeval tval;
	/*
	 * Fake it with select...
	 */
	tval.tv_sec = 0;
	tval.tv_usec = usecs/1000;
	select(0,NULL,NULL,NULL,&tval);
	return 0;
}
#endif /* HAVE_USLEEP */

#ifndef HAVE_SETPROCTITLE
void rep_setproctitle(const char *fmt, ...)
{
}
#endif
#ifndef HAVE_SETPROCTITLE_INIT
void rep_setproctitle_init(int argc, char *argv[], char *envp[])
{
}
#endif

#ifndef HAVE_MEMSET_S
# ifndef RSIZE_MAX
#  define RSIZE_MAX (SIZE_MAX >> 1)
# endif

int rep_memset_s(void *dest, size_t destsz, int ch, size_t count)
{
	if (dest == NULL) {
		return EINVAL;
	}

	if (destsz > RSIZE_MAX ||
	    count > RSIZE_MAX ||
	    count > destsz) {
		return ERANGE;
	}

#if defined(HAVE_MEMSET_EXPLICIT)
	memset_explicit(dest, ch, count);
#else /* HAVE_MEMSET_EXPLICIT */
	memset(dest, ch, count);
# if defined(HAVE_GCC_VOLATILE_MEMORY_PROTECTION)
	/* See http://llvm.org/bugs/show_bug.cgi?id=15495 */
	__asm__ volatile("" : : "g"(dest) : "memory");
# endif /* HAVE_GCC_VOLATILE_MEMORY_PROTECTION */
#endif /* HAVE_MEMSET_EXPLICIT */

	return 0;
}
#endif /* HAVE_MEMSET_S */

#ifndef HAVE_GETPROGNAME
# ifndef HAVE_PROGRAM_INVOCATION_SHORT_NAME
# define PROGNAME_SIZE 32
static char rep_progname[PROGNAME_SIZE];
# endif /* HAVE_PROGRAM_INVOCATION_SHORT_NAME */

const char *rep_getprogname(void)
{
#ifdef HAVE_PROGRAM_INVOCATION_SHORT_NAME
	return program_invocation_short_name;
#else /* HAVE_PROGRAM_INVOCATION_SHORT_NAME */
	FILE *fp = NULL;
	char cmdline[4096] = {0};
	char *p = NULL;
	pid_t pid;
	size_t nread;
	int len;
	int rc;

	if (rep_progname[0] != '\0') {
		return rep_progname;
	}

	len = snprintf(rep_progname, sizeof(rep_progname), "%s", "<unknown>");
	if (len <= 0) {
		return NULL;
	}

	pid = getpid();
	if (pid <= 1 || pid == (pid_t)-1) {
		return NULL;
	}

	len = snprintf(cmdline,
		       sizeof(cmdline),
		       "/proc/%u/cmdline",
		       (unsigned int)pid);
	if (len <= 0 || len == sizeof(cmdline)) {
		return NULL;
	}

	fp = fopen(cmdline, "r");
	if (fp == NULL) {
		return NULL;
	}

	nread = fread(cmdline, 1, sizeof(cmdline) - 1, fp);

	rc = fclose(fp);
	if (rc != 0) {
		return NULL;
	}

	if (nread == 0) {
		return NULL;
	}

	cmdline[nread] = '\0';

	p = strrchr(cmdline, '/');
	if (p != NULL) {
		p++;
	} else {
		p = cmdline;
	}

	len = strlen(p);
	if (len > PROGNAME_SIZE) {
		p[PROGNAME_SIZE - 1] = '\0';
	}

	(void)snprintf(rep_progname, sizeof(rep_progname), "%s", p);

	return rep_progname;
#endif /* HAVE_PROGRAM_INVOCATION_SHORT_NAME */
}
#endif /* HAVE_GETPROGNAME */

#ifndef HAVE_COPY_FILE_RANGE
ssize_t rep_copy_file_range(int fd_in,
			    loff_t *off_in,
			    int fd_out,
			    loff_t *off_out,
			    size_t len,
			    unsigned int flags)
{
# ifdef HAVE_SYSCALL_COPY_FILE_RANGE
	return syscall(__NR_copy_file_range,
		       fd_in,
		       off_in,
		       fd_out,
		       off_out,
		       len,
		       flags);
# endif /* HAVE_SYSCALL_COPY_FILE_RANGE */
	errno = ENOSYS;
	return -1;
}
#endif /* HAVE_COPY_FILE_RANGE */

#ifdef HAVE_LINUX_IOCTL
# include <linux/fs.h>
# include <sys/ioctl.h>
#endif

ssize_t rep_copy_reflink(int src_fd,
			 off_t src_off,
			 int dst_fd,
			 off_t dst_off,
			 off_t to_copy)
{
#ifdef HAVE_LINUX_IOCTL
	struct file_clone_range cr;

	cr = (struct file_clone_range) {
		.src_fd = src_fd,
		.src_offset = (uint64_t)src_off,
		.dest_offset = (uint64_t)dst_off,
		.src_length = (uint64_t)to_copy,
	};

	return ioctl(dst_fd, FICLONERANGE, &cr);
#else
	errno = ENOSYS;
	return -1;
#endif
}

#ifndef HAVE_OPENAT2

/* fallback known wellknown __NR_openat2 values */
#ifndef __NR_openat2
# if defined(LINUX) && defined(HAVE_SYS_SYSCALL_H)
#  if defined(__i386__)
#   define __NR_openat2 437
#  elif defined(__x86_64__) && defined(__LP64__)
#   define __NR_openat2 437 /* 437 0x1B5 */
#  elif defined(__x86_64__) && defined(__ILP32__)
#   define __NR_openat2 1073742261 /* 1073742261 0x400001B5 */
#  elif defined(__aarch64__)
#   define __NR_openat2 437
#  elif defined(__arm__)
#   define __NR_openat2 437
#  elif defined(__sparc__)
#   define __NR_openat2 437
#  endif
# endif /* defined(LINUX) && defined(HAVE_SYS_SYSCALL_H) */
#endif /* !__NR_openat2 */

#ifdef DISABLE_OPATH
/*
 * systems without O_PATH also don't have openat2,
 * so make sure we at a realistic combination.
 */
#undef __NR_openat2
#endif /* DISABLE_OPATH */

long rep_openat2(int dirfd, const char *pathname,
		 struct open_how *how, size_t size)
{
#ifdef __NR_openat2
#if _FILE_OFFSET_BITS == 64 && SIZE_MAX == 0xffffffffUL && defined(O_LARGEFILE)
	struct open_how __how;

#if defined(O_PATH) && ! defined(DISABLE_OPATH)
	if ((how->flags & O_PATH) == 0)
#endif
	{
		if (sizeof(__how) == size) {
			__how = *how;

			__how.flags |= O_LARGEFILE;
			how = &__how;
		}
	}
#endif

	return syscall(__NR_openat2,
		       dirfd,
		       pathname,
		       how,
		       size);
#else
	errno = ENOSYS;
	return -1;
#endif
}
#endif /* !HAVE_OPENAT2 */

#ifndef HAVE_RENAMEAT2

/* fallback to wellknown __NR_renameat2 values */
#ifndef __NR_renameat2
# if defined(LINUX) && defined(HAVE_SYS_SYSCALL_H)
#  if defined(__i386__)
#   define __NR_renameat2 353
#  elif defined(__x86_64__) && defined(__LP64__)
#   define __NR_renameat2 316 /* 316 0x13C */
#  elif defined(__x86_64__) && defined(__ILP32__)
#   define __NR_renameat2 1073742140 /* 1073742140 0x4000013C */
#  elif defined(__aarch64__)
#   define __NR_renameat2 276
#  elif defined(__arm__)
#   define __NR_renameat2 382
#  elif defined(__sparc__)
#   define __NR_renameat2 345
#  endif
# endif /* defined(LINUX) && defined(HAVE_SYS_SYSCALL_H) */
#endif /* !__NR_renameat2 */

#ifdef DISABLE_OPATH
/*
 * systems without O_PATH also don't have renameat2,
 * so make sure we at a realistic combination.
 */
#undef __NR_renameat2
#endif /* DISABLE_OPATH */

int rep_renameat2(int __oldfd, const char *__old, int __newfd,
		  const char *__new, unsigned int __flags)
{
	if (__flags != 0) {
#ifdef __NR_renameat2
		int ret;

		ret = syscall(__NR_renameat2,
			      __oldfd,
			      __old,
			      __newfd,
			      __new,
			      __flags);
		if (ret != -1 || errno != ENOSYS) {
			/*
			 * if it's ENOSYS, we fallback
			 * to EINVAL below, otherwise
			 * we return what the kernel
			 * did.
			 */
			return ret;
		}
#endif
		errno = EINVAL;
		return -1;
	}

	return renameat(__oldfd, __old, __newfd, __new);
}
#endif /* ! HAVE_RENAMEAT2 */

const char hexchars_lower[] = "0123456789abcdef";
const char hexchars_upper[] = "0123456789ABCDEF";
