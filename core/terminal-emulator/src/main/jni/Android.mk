LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE:= libtermux
LOCAL_SRC_FILES:= termux.c

LOCAL_CFLAGS += -ffile-prefix-map=$(LOCAL_PATH)=.
LOCAL_LDFLAGS += -Wl,--build-id=none

include $(BUILD_SHARED_LIBRARY)
