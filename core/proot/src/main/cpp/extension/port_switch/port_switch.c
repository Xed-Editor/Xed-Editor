#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <asm/types.h>
#include <linux/net.h>
#include <stdint.h>
#include <string.h>

#include "extension/extension.h"
#include "tracee/tracee.h"
#include "tracee/mem.h"

#define PORT_THRESHOLD  1024
#define PORT_ADDITION   2000

/** A function to modify the port number of the socket address sent to system calls by 1024. 
 *  Uses tracee to modify the system call.
 *  Uses is_socketcall to determine if the system call is the socketcall wrapper.
 *  Uses is_bind during bind calls, so that a modification message can be printed.
 *  Uses is_udp to determine if the call is *likely* a UDP communication, in which case the location of
 *  the system call arguments are different.
 *  Uses my_sockaddr to hold the structure that contains the port number to modify.
 *  Uses socketcall_arg2 in the socketcall case, as socketcall's second argument is a long pointer.
 */
void    mod_port(Tracee *tracee, bool is_socketcall, bool is_bind, bool is_udp, struct sockaddr_storage *my_sockaddr, long socketcall_arg2[]);
 
/** A function to determine whether the IP address of a system call is localhost.
 *  Uses my_sockaddr to hold the structure that contains the IP address. 
 */
bool    is_localhost(struct sockaddr_storage *my_sockaddr);

int port_switch_callback(Extension *extension, ExtensionEvent event, intptr_t data1 UNUSED, intptr_t data2 UNUSED) {
    switch (event) {
    case INITIALIZATION: {
        static FilteredSysnum filtered_sysnums[] = {
            { PR_bind, FILTER_SYSEXIT },
            { PR_connect, FILTER_SYSEXIT },
            { PR_socketcall, FILTER_SYSEXIT },
            { PR_sendto, FILTER_SYSEXIT },
            { PR_recvfrom, FILTER_SYSEXIT },
            FILTERED_SYSNUM_END     
        };
        extension->filtered_sysnums = filtered_sysnums;
        return 0;
    }
    
    case SYSCALL_ENTER_END: {
        Tracee *tracee = TRACEE(extension);
        
        /** The 4 system calls that will be changed are bind, connect, sendto, and socketcall. 
         *  Socketcall is a wrapper function for the i386 architecture that wraps the first 3 system calls.
         *  In every bind case the port is modified, as there is a high likelihood that it will be binding a socket locally.
         *  For connect and sendto, the port is only modified if the calls are attempting to reach localhost. This is
         *  to save users hassle when connecting to localhost while allowing them to reach servers outside their system
         *  without a difference from their regular usability.
         */
        switch(get_sysnum(tracee, ORIGINAL)) {

            case PR_bind: {
                struct sockaddr_storage my_sockaddr;    
                read_data(tracee, &my_sockaddr, peek_reg(tracee, ORIGINAL, SYSARG_2), sizeof(struct sockaddr_storage));
                mod_port(tracee, false, true, false, &my_sockaddr, NULL);
                
                return 0;
            }

            case PR_connect: {
                struct sockaddr_storage my_sockaddr;
                read_data(tracee, &my_sockaddr, peek_reg(tracee, ORIGINAL, SYSARG_2), sizeof(struct sockaddr_storage));
                if(is_localhost(&my_sockaddr)) 
                    mod_port(tracee, false, false, false, &my_sockaddr, NULL);
                
                return 0;
            }

            case PR_sendto: {
                struct sockaddr_storage my_sockaddr;
                /** There are some cases where sendto() is called during a TCP communication, even though
                 *  send() is the norm. The port doesn't need to be modified in these cases. If sendto()
                 *  is used while in a connected case (non-UDP), its sockaddr argument is null. To avoid
                 *  modifying the port, these cases are ignored.
                 */
                if(peek_reg(tracee, ORIGINAL, SYSARG_5) != 0) {
                    read_data(tracee, &my_sockaddr, peek_reg(tracee, ORIGINAL, SYSARG_5), sizeof(struct sockaddr_storage));
                    if(is_localhost(&my_sockaddr)) 
                        mod_port(tracee, false, false, true, &my_sockaddr, NULL);
                }
                return 0;
            }

            case PR_socketcall: {
                /** Socketcall's 1st argument is an int that signifies which actual socket system call it is being used to wrap.
                 *  Socketcall's 2nd argument is a long* that leads to the location in memory where the arguments to that system
                 *  call are. Those arguments are extracted first, and then the sockaddr can be extracted from those.
                 */
                int call;
                long a[6];
                call = peek_reg(tracee, ORIGINAL, SYSARG_1);
                read_data(tracee, a, peek_reg(tracee, ORIGINAL, SYSARG_2), sizeof(a));
                switch(call) {

                    case SYS_BIND: {
                        struct sockaddr_storage my_sockaddr;
                        read_data(tracee, &my_sockaddr, a[1], sizeof(struct sockaddr_storage));
                        mod_port(tracee, true, true, false, &my_sockaddr, a);

                        break;
                    }

                    case SYS_CONNECT: {
                        struct sockaddr_storage my_sockaddr;
                        read_data(tracee, &my_sockaddr, a[1], sizeof(struct sockaddr_storage));
                        if(is_localhost(&my_sockaddr)) 
                            mod_port(tracee, true, false, false, &my_sockaddr, a);

                        break;
                    }

                    case SYS_SENDTO: {
                        struct sockaddr_storage my_sockaddr;
                        if(a[4] != 0) {
                            read_data(tracee, &my_sockaddr, a[4], sizeof(struct sockaddr_storage));
                            if(is_localhost(&my_sockaddr)) 
                                mod_port(tracee, true, false, true, &my_sockaddr, a);
                        }

                        break;
                    }
                    default:
                        break;
                }

                return 0;
            }

            default:
                return 0;
        }
    }   
    default: 
        return 0;
    }
}

void mod_port(Tracee *tracee, bool is_socketcall, bool is_bind, bool is_udp, struct sockaddr_storage *my_sockaddr, long socketcall_arg2[]) {   
    /** IPv4 and IPv6 addresses are stored in different structures. Because of this, the port must be changed
     *  in different ways depending on the address family. 
     *
     *  Socketcall stores the arguments to the system call it wraps in a pointer, the modified sockaddr must be written 
     *  separately to an array and then to the tracee.
     *  
     *  Modifying the port is avoided if the port is not within the section of ports that Android reserves. This is to
     *  avoid problems in UDP server/client communications, in cases where the server sends a message back to the client.
     *  Since the client normally doesn't specifically call bind(), a port >1024 is usually assigned for it to recvfrom.
     *
     *  UDP communications are also treated differently, as the sendto() function has the sockaddr in its 5th location
     *  instead of the 2nd as every other call does.
     */
    switch(my_sockaddr->ss_family) {
        case AF_INET: {
            struct sockaddr_in *in = (struct sockaddr_in *)my_sockaddr;
            if(ntohs(in->sin_port) > 0 && ntohs(in->sin_port) < PORT_THRESHOLD) {

                if(is_bind) {
                    printf("\nATTENTION: A bind system call was requested on port: %d\n", ntohs(in->sin_port));
                    in->sin_port = htons(ntohs(in->sin_port) + PORT_ADDITION);
                    printf("The port has been changed. If connecting from outside Termux, use: %d\n\n", ntohs(in->sin_port));
                }
                else
                   in->sin_port = htons(ntohs(in->sin_port) + PORT_ADDITION); 

                if(is_socketcall && is_udp) {
                    write_data(tracee, socketcall_arg2[4], in, sizeof(in));
                    write_data(tracee, peek_reg(tracee, CURRENT, SYSARG_2), socketcall_arg2, sizeof(socketcall_arg2));
                }

                else if(!is_socketcall && is_udp)
                    write_data(tracee, peek_reg(tracee, CURRENT, SYSARG_5), in, sizeof(in));
                
                else if(is_socketcall && !is_udp) {
                    write_data(tracee, socketcall_arg2[1], in, sizeof(in));
                    write_data(tracee, peek_reg(tracee, CURRENT, SYSARG_2), socketcall_arg2, sizeof(socketcall_arg2));
                }

                else if(!is_socketcall && !is_udp)
                    write_data(tracee, peek_reg(tracee, CURRENT, SYSARG_2), in, sizeof(in));
            }
            break;
        }

        case AF_INET6: {
            struct sockaddr_in6 *in6 = (struct sockaddr_in6 *)my_sockaddr;
            if(ntohs(in6->sin6_port) > 0 && ntohs(in6->sin6_port) < PORT_THRESHOLD) {
                if(is_bind) {
                    printf("\nATTENTION: A bind system call was requested on port: %d\n", ntohs(in6->sin6_port));
                    in6->sin6_port = htons(ntohs(in6->sin6_port) + PORT_ADDITION);
                    printf("The port has been changed. If connecting from outside Termux, use: %d\n\n", ntohs(in6->sin6_port));
                }
                else
                    in6->sin6_port = htons(ntohs(in6->sin6_port) + PORT_ADDITION);
    
                if(is_socketcall && is_udp) { 
                    write_data(tracee, socketcall_arg2[4], in6, sizeof(in6));
                    write_data(tracee, peek_reg(tracee, CURRENT, SYSARG_2), socketcall_arg2, sizeof(socketcall_arg2));
                }

                else if(is_socketcall && is_udp)
                    write_data(tracee, peek_reg(tracee, CURRENT, SYSARG_5), in6, sizeof(in6));

                else if(is_socketcall && !is_udp) { 
                    write_data(tracee, socketcall_arg2[1], in6, sizeof(in6));
                    write_data(tracee, peek_reg(tracee, CURRENT, SYSARG_2), socketcall_arg2, sizeof(socketcall_arg2));
                }

                else if(!is_socketcall && !is_udp)
                    write_data(tracee, peek_reg(tracee, CURRENT, SYSARG_2), in6, sizeof(in6));
            }
            break;
        }

        default:
            break; 
    }
}

bool is_localhost(struct sockaddr_storage *my_sockaddr) {
    /** Localhost is represented differently in the two IPv families, so determining if an address's destination is localhost
     *  must be done differently for each family. Both must also be compared to 0, which is the enumeration for INADDR_ANY
     *  which is often used bind() calls.
     */
    switch(my_sockaddr->ss_family) {

        case AF_INET: {
            struct sockaddr_in *in = (struct sockaddr_in *)my_sockaddr;
            char ipAddress[INET_ADDRSTRLEN];
            inet_ntop(AF_INET, &(in->sin_addr), ipAddress, INET_ADDRSTRLEN);
            if(strcmp(ipAddress, "127.0.0.1") == 0)
                return true;
            else
                return false;
        }
    
        case AF_INET6: {
            struct sockaddr_in6 *in6 = (struct sockaddr_in6 *)my_sockaddr;
            char ipAddress[INET6_ADDRSTRLEN];
            inet_ntop(AF_INET6, &(in6->sin6_addr), ipAddress, INET6_ADDRSTRLEN);
            if(strcmp(ipAddress, "::1") == 0)
                return true;
            else
                return false;
        }

        default: 
            return false;
    }
}
