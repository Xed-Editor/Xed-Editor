#include <iostream>
#include <string>
#include "socket.h"
#include <sstream>



int main(int argc, char *argv[]) {
    if (argc > 1) {
        std::string action = argv[1];

        if (action == "edit") {
            if (argc > 2) {

                // Suppose argc and argv are available in your main or function
                std::ostringstream oss;
                for (int i = 2; i < argc; ++i) {
                    if (i > 2) oss << " "; // Add space between arguments
                    oss << argv[i];
                }
                std::string path = oss.str();
                char cwd[PATH_MAX];
                if (getcwd(cwd, sizeof(cwd)) != nullptr) {} else {
                    perror("getcwd() error");
                    return -1;
                }

                std::string json =
                        "{"
                        "\"action\":\"" + action + "\","
                                                   "\"args\":\"" + path + "\","
                                                                          "\"pwd\":\"" + cwd + "\""
                                                                                               "}";

                connectToSocket(json.c_str());
            } else {
                std::cerr << "Error: No path provided for 'edit' action." << std::endl;
                return 1;
            }
        } else {
            std::cerr << "Unknown action." << std::endl;
            return 1;
        }

    } else {
        std::cerr << "No argument provided." << std::endl;
        return 1;
    }
    return 0;
}
