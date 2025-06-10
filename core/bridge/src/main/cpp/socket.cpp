#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <iostream>
#include <cstring>
#include <cstddef> // for offsetof

int connectToSocket(const char* msg) {
    int sock = socket(AF_UNIX, SOCK_STREAM, 0);
    if (sock == -1) {
        perror("socket");
        return -1;
    }

    sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;

    // Use the exact same name as in the Kotlin server
    const char* abstract_name = "bridge";

    // Abstract namespace: sun_path[0] = '\0', then name
    addr.sun_path[0] = '\0';
    strncpy(addr.sun_path + 1, abstract_name, sizeof(addr.sun_path) - 2);

    // Address length: offsetof + 1 (for null) + name length
    size_t name_len = strlen(abstract_name);
    size_t addrLen = offsetof(sockaddr_un, sun_path) + 1 + name_len;

    if (connect(sock, (struct sockaddr*)&addr, addrLen) == -1) {
        perror("connect");
        close(sock);
        return -1;
    }

    // Send the provided message
    std::string msg_with_newline = std::string(msg) + "\n";
    if (write(sock, msg_with_newline.c_str(), msg_with_newline.length()) == -1) {
        perror("write");
        close(sock);
        return -1;
    }

    // Receive events
    char buf[256];
    while (true) {
        ssize_t n = read(sock, buf, sizeof(buf) - 1);
        if (n > 0) {
            buf[n] = '\0';

            if (strcmp(buf, "ok") == 0) {
                // buf contains "ok"
                exit(0);
            } else if (strcmp(buf, "err") == 0) {
                // buf contains "err"
                exit(1);
            }else{
                std::cout << buf << std::endl;
                exit(-1);
            }

        } else {
            break;
        }
    }

    close(sock);
    return 0;
}
