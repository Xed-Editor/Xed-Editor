#ifndef KARBON_JVM_HPP
#define KARBON_JVM_HPP

#include <jni.h>

// Correct the function signature to include `JNIEnv* env`
void callJMethod(JNIEnv *env, const char *className, const char *methodName, jobject *args, int numArgs);

jstring toJstring(JNIEnv *env, const char *string);

#endif // KARBON_JVM_HPP
