#include <string>
#include <iostream>
#include <vector>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <climits>
#include <cstring>

void print_help(std::ostream& os) {
    os << "Usage: xed [options] <file|directory...>" << std::endl << std::endl;
    os << "Open files or directories in Xed Editor." << std::endl << std::endl;
    os << "Options:" << std::endl;
    os << "  -h, --help    Show this help message" << std::endl << std::endl;
    os << "Examples:" << std::endl;
    os << "  xed .               Open current directory" << std::endl;
    os << "  xed main.kt         Open main.kt" << std::endl;
    os << "  xed main.kt:10:5    Open main.kt at line 10, column 5" << std::endl;
}

int main(int argc, char** argv) {
    if (argc == 1) {
        std::cerr << "xed: Error: No file or directory specified." << std::endl << std::endl;
        print_help(std::cerr);
        return 1;
    }

    if (argc == 2 && (std::string(argv[1]) == "-h" || std::string(argv[1]) == "--help")) {
        print_help(std::cout);
        return 0;
    }
    // Get current working directory
    char cwd[PATH_MAX];
    if (getcwd(cwd, sizeof(cwd)) == nullptr) {
        std::cerr << "Failed to get current working directory" << std::endl;
        return 1;
    }

    // Create UNIX domain socket
    int sock = socket(AF_UNIX, SOCK_STREAM, 0);
    if (sock < 0) {
        std::cerr << "Failed to create socket" << std::endl;
        return 1;
    }

    struct sockaddr_un addr{};
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;

    // Abstract namespace socket: first byte is null, followed by name
    const char* socket_name = "xed_socket";
    addr.sun_path[0] = '\0';
    strncpy(addr.sun_path + 1, socket_name, sizeof(addr.sun_path) - 2);

    // Calculate length of the abstract address
    socklen_t len = sizeof(addr.sun_family) + 1 + strlen(socket_name);

    if (connect(sock, (struct sockaddr*)&addr, len) < 0) {
        std::cerr << "Failed to connect to Xed editor service. Is Terminal Service running?" << std::endl;
        close(sock);
        return 1;
    }

    // Protocol:
    // 1. CWD (null terminated)
    // 2. Each argument from argv[1] to argv[argc-1] (null terminated)
    // Send CWD
    std::string cwd_str(cwd);
    if (send(sock, cwd_str.c_str(), cwd_str.length() + 1, 0) < 0) {
        std::cerr << "Failed to send CWD" << std::endl;
        close(sock);
        return 1;
    }

    // Send arguments
    for (int i = 1; i < argc; ++i) {
        std::string arg(argv[i]);
        if (send(sock, arg.c_str(), arg.length() + 1, 0) < 0) {
            std::cerr << "Failed to send argument" << std::endl;
            close(sock);
            return 1;
        }
    }

    close(sock);
    return 0;
}