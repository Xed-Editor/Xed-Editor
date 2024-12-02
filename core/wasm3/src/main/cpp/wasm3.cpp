#include <jni.h>
#include <string>
#include <fstream>
#include <vector>
#include <iostream>
#include <cstdio>
#include "m3/m3_env.h"
#include "m3/m3_api_wasi.h"
#include "m3/m3_api_libc.h"
#include "JvmUtils.hpp"
#include <android/log.h>


#define LOG_TAG "WASM3"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

JNIEnv *jenv;

m3ApiRawFunction(toast) {
    m3ApiGetArg(int32_t, msgPtr);
    const char *msg = (const char *) m3ApiOffsetToPtr(msgPtr);
    LOGI("--------------------------------------");
    LOGI(msg);
    LOGI("--------------------------------------");

    jobject args[1];
    args[0] = toJstring(jenv,msg);
    callJMethod(jenv,"com/rk/wasm3/Wasm3Api","toast",args,1);

    m3ApiSuccess();
}

static
M3Result SuppressLookupFailure(M3Result i_result) {
    if (i_result == m3Err_functionLookupFailed)
        return m3Err_none;
    else
        return i_result;
}

void execWasm(const uint8_t *wasmCode, size_t codeSize, const std::vector<std::string> &functions) {
    IM3Environment env = m3_NewEnvironment();
    if (!env) {
        LOGE("Failed to create wasm3 environment");
        return;
    }
    IM3Runtime runtime = m3_NewRuntime(env, 1024, NULL);
    if (!runtime) {
        LOGE("Failed to create wasm3 runtime");
        m3_FreeEnvironment(env);
        return;
    }
    IM3Module module;
    M3Result result = m3_ParseModule(env, &module, wasmCode, codeSize);
    if (result) {
        LOGE("Failed to parse wasm module: %s", result);
        m3_FreeRuntime(runtime);
        m3_FreeEnvironment(env);
        return;
    }
    result = m3_LoadModule(runtime, module);
    if (result) {
        LOGE("Failed to load wasm module: %s", result);
        m3_FreeRuntime(runtime);
        m3_FreeEnvironment(env);
        return;
    }

    result = m3_LinkLibC(module);
    if (result) {
        LOGE("Failed to link libc: %s", result);
        m3_FreeRuntime(runtime);
        m3_FreeEnvironment(env);
        return;
    }

    (SuppressLookupFailure(m3_LinkRawFunction(module, "env", "toast", "v(i)", toast)));

    IM3Function f;
    for (const auto &functionName: functions) {
        result = m3_FindFunction(&f, runtime, functionName.c_str());
        if (result) {
            LOGE("Function '%s' not found: %s", functionName.c_str(), result);
            LOGE(std::string("Function '" + functionName + "' not found: " + result).c_str());
            return;
        } else {
            result = m3_CallV(f);
            if (result) {
                LOGE("Failed to call function '%s': %s", functionName.c_str(), result);
                LOGE(std::string("Failed to call function '" + functionName + "': " + result).c_str());
                return;
            } else {
                LOGI("Function '%s' called successfully", functionName.c_str());
            }
        }
    }
    m3_FreeRuntime(runtime);
    m3_FreeEnvironment(env);
}




std::vector<std::string> objectArrayToVector(JNIEnv *env, jobjectArray jArray) {
    std::vector<std::string> result;

    jenv = env;

    jsize arrayLength = env->GetArrayLength(jArray);

    for (jsize i = 0; i < arrayLength; ++i) {
        auto jStr = (jstring) env->GetObjectArrayElement(jArray, i);

        const char *charStr = env->GetStringUTFChars(jStr, nullptr);
        result.emplace_back(charStr);

        env->ReleaseStringUTFChars(jStr, charStr);
    }

    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_rk_wasm3_Wasm3_loadWasm(JNIEnv *env, jobject thiz, jstring path, jobjectArray function_name) {
    const char *wasmFilePath = env->GetStringUTFChars(path, 0);
    if (!wasmFilePath) {
        return;
    }
    std::ifstream wasmFile(wasmFilePath, std::ios::binary | std::ios::ate);
    if (!wasmFile) {
        LOGE("Failed to open wasm file: %s", wasmFilePath);
        env->ReleaseStringUTFChars(path, wasmFilePath);
        return;
    }
    std::streamsize wasmSize = wasmFile.tellg();
    wasmFile.seekg(0, std::ios::beg);
    std::vector<uint8_t> wasmBytes(wasmSize);
    if (!wasmFile.read(reinterpret_cast<char *>(wasmBytes.data()), wasmSize)) {
        LOGE("Failed to read wasm file: %s", wasmFilePath);
        env->ReleaseStringUTFChars(path, wasmFilePath);
        return;
    }

    execWasm(wasmBytes.data(), wasmSize, objectArrayToVector(env, function_name));

    env->ReleaseStringUTFChars(path, wasmFilePath);
}

