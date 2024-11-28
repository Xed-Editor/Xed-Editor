#include "JvmUtils.hpp"
#include <iostream>
#include <jni.h>

void callJMethod(JNIEnv* env, const char* className, const char* methodName, jobject* args, int numArgs) {
    // Step 1: Find the class
    jclass clazz = env->FindClass(className);
    if (clazz == nullptr) {
        std::cerr << "Class not found: " << className << std::endl;
        return;
    }

    // Step 2: Get the method ID (use the appropriate signature)
    jmethodID methodID = nullptr;

    // If the method is static, use GetStaticMethodID
    if (numArgs == 0) {
        methodID = env->GetStaticMethodID(clazz, methodName, "()V");
    } else if (numArgs == 1) {
        methodID = env->GetStaticMethodID(clazz, methodName, "(Ljava/lang/String;)V");  // Adjust signature for 1 argument
    }

    if (methodID == nullptr) {
        std::cerr << "Method not found: " << methodName << std::endl;
        return;
    }

    // Step 3: Call the static method using the method ID and arguments
    if (numArgs == 1) {
        env->CallStaticVoidMethod(clazz, methodID, args[0]);
    }
    // You can extend this for more arguments as needed
}

jstring toJstring(JNIEnv *jenv,const char* string){
    return jenv->NewStringUTF(string);
}
