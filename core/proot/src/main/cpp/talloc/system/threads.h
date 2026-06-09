#ifndef _system_threads_h
#define _system_threads_h
/*
   Unix SMB/CIFS implementation.

   macros to go along with the lib/replace/ portability layer code

   Copyright (C) Volker Lendecke 2012

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

#include <pthread.h>

#if defined(HAVE_PTHREAD_MUTEXATTR_SETROBUST_NP) && \
	!defined(HAVE_PTHREAD_MUTEXATTR_SETROBUST)
#define pthread_mutexattr_setrobust pthread_mutexattr_setrobust_np
#endif

#if defined(HAVE_DECL_PTHREAD_MUTEX_ROBUST_NP) && \
	!defined(HAVE_DECL_PTHREAD_MUTEX_ROBUST)
#define PTHREAD_MUTEX_ROBUST PTHREAD_MUTEX_ROBUST_NP
#endif

#if defined(HAVE_PTHREAD_MUTEX_CONSISTENT_NP) && \
	!defined(HAVE_PTHREAD_MUTEX_CONSISTENT)
#define pthread_mutex_consistent pthread_mutex_consistent_np
#endif

#ifdef HAVE_STDATOMIC_H
#include <stdatomic.h>
#endif

#ifndef HAVE_ATOMIC_THREAD_FENCE
#ifdef HAVE___ATOMIC_THREAD_FENCE
#define atomic_thread_fence(__ignore_order) __atomic_thread_fence(__ATOMIC_SEQ_CST)
#define HAVE_ATOMIC_THREAD_FENCE 1
#endif /* HAVE___ATOMIC_THREAD_FENCE */
#endif /* not HAVE_ATOMIC_THREAD_FENCE */

#ifndef HAVE_ATOMIC_THREAD_FENCE
#ifdef HAVE___SYNC_SYNCHRONIZE
#define atomic_thread_fence(__ignore_order) __sync_synchronize()
#define HAVE_ATOMIC_THREAD_FENCE 1
#endif /* HAVE___SYNC_SYNCHRONIZE */
#endif /* not HAVE_ATOMIC_THREAD_FENCE */

#ifndef HAVE_ATOMIC_THREAD_FENCE
#ifdef HAVE_ATOMIC_THREAD_FENCE_SUPPORT
#error mismatch_error_between_configure_test_and_header
#endif
/* make sure the build fails if someone uses it without checking the define */
#define atomic_thread_fence(__order) \
        __function__atomic_thread_fence_not_available_on_this_platform__()
#endif /* not HAVE_ATOMIC_THREAD_FENCE */

#endif
