#ifndef SYSVIPC_H
#define SYSVIPC_H

#include "extension/extension.h"

int sysvipc_callback(Extension *extension, ExtensionEvent event, intptr_t data1, intptr_t data2);
void sysvipc_shm_helper_main() __attribute__((noreturn));

#endif // SYSVIPC_H
