#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
#include <inttypes.h>
#include <pthread.h>
#include <unistd.h>
#include <jni.h>
#include <android/log.h>

extern int main();

/*
 * JNI init
 */

JavaVM* javaVM;
JNIEnv* jniEnv;

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved)
{
    if ((*vm)->GetEnv(vm, (void**)&jniEnv, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR; // JNI version not supported
    }
    javaVM = vm;
    return  JNI_VERSION_1_6;
}

static int pfd[2];
static pthread_t pumpThread;
static pthread_t mainThread;

static void* runOutputPump(void* ctx)
{
    int readSize;
    char buff[128];

    while ((readSize = read(pfd[0], buff, sizeof(buff) - 1)) > 0)
    {
        buff[readSize] = '\0';

        // Log the output to Logcat
        __android_log_print(ANDROID_LOG_INFO, "com.rk.wasm3.Wasm3", "%s", buff);
    }

    return 0;
}

static void* runMain(void* ctx)
{
    (*javaVM)->AttachCurrentThread(javaVM, &jniEnv, NULL);
    main();
    return NULL;
}

JNIEXPORT void JNICALL
Java_com_rk_wasm3_Wasm3_00024Companion_runMain(JNIEnv* env, jobject instance)
{
    setvbuf(stdout, 0, _IOLBF, 0); // stdout: line-buffered
    setvbuf(stderr, 0, _IONBF, 0); // stderr: unbuffered

    // create the pipe and redirect stdout and stderr
    pipe(pfd);
    dup2(pfd[1], 1);
    dup2(pfd[1], 2);

    pthread_attr_t  threadAttr;
    pthread_attr_init(&threadAttr);
    pthread_attr_setdetachstate(&threadAttr, PTHREAD_CREATE_DETACHED);

    pthread_create(&pumpThread, &threadAttr, runOutputPump, NULL);
    pthread_create(&mainThread, &threadAttr, runMain, NULL);

    pthread_attr_destroy(&threadAttr);
    printf("wasm3 loaded");
}