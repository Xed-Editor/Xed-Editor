/*
 * Author: Dieter Mueller
 * Date:   6/12/2015
 *
 * Description: An extension to allow getdents to hide files
 * with a given prefix. Useful for keeping files from being
 * deleted by wildcard expressions.
 */

#include "extension/extension.h"
#include "tracee/mem.h"
#include "syscall/chain.h"
#include "path/path.h"

/* Change the HIDDEN_PREFIX to change which files are hidden */
#define HIDDEN_PREFIX ".proot"

struct linux_dirent {
    unsigned long d_ino;
    unsigned long d_off;
    unsigned short d_reclen;
    char d_name[];
};

struct linux_dirent64 {
    unsigned long long d_ino;
    long long d_off;
    unsigned short d_reclen;
    unsigned char d_type;
    char d_name[];
};

/*
 * Blind copies the given num of bytes from src to dst
 */
static void mybcopy(char *src, char *dst, unsigned int num) {
    while(num--) { *(dst++) = *(src++); }
}

/*
 * Compares the given prefix with the given string.
 * If str has the given prefix, return 1. Otherwise
 * return 0
 */

static int hasprefix(char *prefix, char *str) {
    while (*prefix && *str && (*(prefix) == *(str))) {
        prefix++;
        str++;
    }
  
    /* If there is not any prefix left after stepping
     * through the strings, then it matches */
    if (!(*prefix)) { return 1; }
    return 0;
}

/**
 * Hide all files with a given PREFIX so they don't exist to
 * the user
 */
static int handle_getdents(Tracee *tracee)
{
    switch (get_sysnum(tracee, ORIGINAL)) {
    case PR_getdents64: 
    case PR_getdents: {
        /* get the result of the syscall, which is the number of bytes read by getdents */
        unsigned int res = peek_reg(tracee, CURRENT, SYSARG_RESULT);
        if (res <= 0) {
            return res;
        }

        /* get the system call arguments */
        word_t orig_start = peek_reg(tracee, CURRENT, SYSARG_2);
        unsigned int count = peek_reg(tracee, CURRENT, SYSARG_3);
        char orig[count];

        char path[PATH_MAX];
        int status = readlink_proc_pid_fd(tracee->pid, peek_reg(tracee, ORIGINAL, SYSARG_1), path);
        if (status < 0) {
           return 0;
        }
        if(!belongs_to_guestfs(tracee, path))
           return 0;

        /* retrieve the data from getdents */
        status = read_data(tracee, orig, orig_start, res);
        if (status < 0) {
            return status;
        }

        /* allocate a space for the copy of the data we want */
        char copy[count];
        /* curr will hold the current struct we're examining */
        struct linux_dirent64 *curr64;
        struct linux_dirent *curr32;
        /* pos keeps track of where in memory the copy is */
        char *pos = copy;
        /* ptr keeps track of where in memory the original is */
        char *ptr = orig;
        /* nleft keeps track of how many bytes we've saved */
        unsigned int nleft = 0;

        /* while we're still within the memory allowed */
        if (get_sysnum(tracee, ORIGINAL) == PR_getdents64) {
            while (ptr < orig + res) {

                /* get the current struct */
                curr64 = (struct linux_dirent64 *)ptr;

                /* if the name does not matche a given prefix */
                if (!hasprefix(HIDDEN_PREFIX, curr64->d_name)) {

                    /* copy the information */
                    mybcopy(ptr, pos, curr64->d_reclen);

                    /* move the pos and nleft */
                    pos += curr64->d_reclen;
                    nleft += curr64->d_reclen;
                }
                /* move to the next linux_dirent */
                ptr += curr64->d_reclen;
            }
        } else {
            while (ptr < orig + res) {

                /* get the current struct */
                curr32 = (struct linux_dirent *)ptr;

                /* if the name does not matche a given prefix */
                if (!hasprefix(HIDDEN_PREFIX, curr32->d_name)) {

                    /* copy the information */
                    mybcopy(ptr, pos, curr32->d_reclen);

                    /* move the pos and nleft */
                    pos += curr32->d_reclen;
                    nleft += curr32->d_reclen;
                }
                /* move to the next linux_dirent */
                ptr += curr32->d_reclen;
            }
        }
        /* If there is nothing left */
        if (!nleft) {
            /* call getdents again */
            if (get_sysnum(tracee, ORIGINAL) == PR_getdents64)
                register_chained_syscall(tracee, PR_getdents64, peek_reg(tracee, ORIGINAL, SYSARG_1), orig_start, count, 0, 0, 0);
            else
                register_chained_syscall(tracee, PR_getdents, peek_reg(tracee, ORIGINAL, SYSARG_1), orig_start, count, 0, 0, 0);
        }
        else {
            /* copy the data back into the register */
            status = write_data(tracee, orig_start, copy, nleft);
            if (status < 0) {
                return status;
            }
            /* update the return value to match the data */
            poke_reg(tracee, SYSARG_RESULT, nleft);
        }

        /* return successful */
        return 0;
    }

    default:
        return 0;
    }
}

/**
 * Handler for this @extension.  It is triggered each time an @event
 * occured.  See ExtensionEvent for the meaning of @data1 and @data2.
 */
int hidden_files_callback(Extension *extension, ExtensionEvent event,
        intptr_t data1 UNUSED, intptr_t data2 UNUSED)
{
    switch (event) {
    case INITIALIZATION: {
        /* List of syscalls handled by this extension */
        static FilteredSysnum filtered_sysnums[] = {
            { PR_getdents,    FILTER_SYSEXIT },
            { PR_getdents64,  FILTER_SYSEXIT },
            FILTERED_SYSNUM_END,
        };
        extension->filtered_sysnums = filtered_sysnums;
        return 0;
    }

    case SYSCALL_CHAINED_EXIT:
    case SYSCALL_EXIT_END: {
        return handle_getdents(TRACEE(extension));
    }

    default:
        return 0;
    }
}
