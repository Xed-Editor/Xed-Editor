#include <jni.h>
#include <string>
#include <fstream>
#include <vector>
#include <iostream>
#include <cstdio>
#include "m3/m3_env.h"
#include "m3/m3_api_wasi.h"

std::string jstring2string(JNIEnv *env, jstring jStr) {
    if (!jStr)
        return "";

    const jclass stringClass = env->GetObjectClass(jStr);
    const jmethodID getBytes = env->GetMethodID(stringClass, "getBytes", "(Ljava/lang/String;)[B");
    const auto stringJbytes = (jbyteArray) env->CallObjectMethod(jStr, getBytes, env->NewStringUTF("UTF-8"));

    auto length = (size_t) env->GetArrayLength(stringJbytes);
    jbyte *pBytes = env->GetByteArrayElements(stringJbytes, NULL);

    std::string ret = std::string((char *) pBytes, length);
    env->ReleaseByteArrayElements(stringJbytes, pBytes, JNI_ABORT);

    env->DeleteLocalRef(stringJbytes);
    env->DeleteLocalRef(stringClass);
    return ret;
}

uint8_t *loadWasmFile(const char *filename, size_t &wasmSize) {
    FILE *file = fopen(filename, "rb");
    if (!file) {
        std::cerr << "Failed to open WASM file\n";
        return nullptr;
    }

    fseek(file, 0, SEEK_END);
    wasmSize = ftell(file);
    fseek(file, 0, SEEK_SET);

    auto *wasmData = new uint8_t[wasmSize];
    fread(wasmData, 1, wasmSize, file);
    fclose(file);

    return wasmData;
}

// Cleanup function to free resources
void cleanupWasmEnvironment(IM3Environment env, IM3Runtime runtime, IM3Module module, const uint8_t* wasmData) {
    if (module) {
        m3_FreeModule(module);
    }
    if (runtime) {
        m3_FreeRuntime(runtime);
    }
    if (env) {
        m3_FreeEnvironment(env);
    }

        delete[] wasmData;

}

/**
 * Initialize the com.rk.wasm3.Wasm3 environment and runtime, loading the WASM file.
 * This function creates the environment and runtime, parses and loads the WASM module,
 * and prepares it for function calls.
 */
extern "C" JNIEXPORT int JNICALL
Java_com_rk_wasm3_Wasm3_initializeWasmEnvironment(JNIEnv *jenv, jobject obj, jstring filePath) {

    std::string wasmFilePath = jstring2string(jenv, filePath);
    printf("Initializing WASM environment with file %s\n", wasmFilePath.c_str());

    size_t wasmSize;
    uint8_t *wasmData = loadWasmFile(wasmFilePath.c_str(), wasmSize);
    if (!wasmData) return 1;

    // Initialize com.rk.wasm3.Wasm3 environment and runtime
    IM3Environment env = m3_NewEnvironment();
    if (!env) {
        std::cerr << "Failed to create com.rk.wasm3.Wasm3 environment\n";
        delete[] wasmData;
        return 1;
    }

    IM3Runtime runtime = m3_NewRuntime(env, 64 * 1024, nullptr);
    if (!runtime) {
        std::cerr << "Failed to create com.rk.wasm3.Wasm3 runtime\n";
        m3_FreeEnvironment(env);
        delete[] wasmData;
        return 1;
    }

    // Parse and load the WASM module
    IM3Module module;
    M3Result result = m3_ParseModule(env, &module, wasmData, wasmSize);
    if (result) {
        std::cerr << "Failed to parse module: " << result << "\n";
        cleanupWasmEnvironment(env, runtime, nullptr, wasmData);
        return 1;
    }
    delete[] wasmData;

    result = m3_LoadModule(runtime, module);
    if (result) {
        std::cerr << "Failed to load module: " << result << "\n";
        cleanupWasmEnvironment(env, runtime, module, nullptr);
        return 1;
    }

    /*result = m3_LinkWASI(module);
    if (result) {
        std::cerr << "Failed to link WASI: " << result << "\n";
        cleanupWasmEnvironment(env, runtime, module, nullptr);
        return 1;
    }*/

    // Retrieve the class reference of the Java object
    jclass clazz = jenv->GetObjectClass(obj);

    // Retrieve the field IDs for the fields that will hold the environment and runtime
    jfieldID envField = jenv->GetFieldID(clazz, "envField", "J");
    jfieldID runtimeField = jenv->GetFieldID(clazz, "runtimeField", "J");

    // Store the environment and runtime for later use in other function calls
    jenv->SetLongField(obj, envField, (jlong) env);
    jenv->SetLongField(obj, runtimeField, (jlong) runtime);

    return 0;
}

/**
 * Calls a function in the already-initialized WASM environment and runtime.
 * The function name is passed as a string, and the function must not accept arguments and return an int as an exit code.
 */
extern "C" JNIEXPORT int JNICALL
Java_com_rk_wasm3_Wasm3_callFunctionInWasm(JNIEnv *jenv, jobject obj, jstring funcName) {

    std::string functionName = jstring2string(jenv, funcName);

    // Retrieve the class reference of the Java object
    jclass clazz = jenv->GetObjectClass(obj);

    // Retrieve the field IDs for the fields that hold the environment and runtime
    jfieldID envField = jenv->GetFieldID(clazz, "envField", "J");
    jfieldID runtimeField = jenv->GetFieldID(clazz, "runtimeField", "J");

    // Retrieve the long values stored in the Java object
    auto env = (IM3Environment) jenv->GetLongField(obj, envField);
    auto runtime = (IM3Runtime) jenv->GetLongField(obj, runtimeField);

    if (!env || !runtime) {
        std::cerr << "Wasm environment or runtime is not initialized.\n";
        return 1;
    }

    printf("Calling function %s\n", functionName.c_str());

    // Dynamically find the function based on the function name passed in
    IM3Function function;
    M3Result result = m3_FindFunction(&function, runtime, functionName.c_str());
    if (result) {
        std::cerr << "Function " << functionName << " not found: " << result << "\n";
        return 1;
    }

    // Call the function (assuming it takes no arguments and returns an int)
    result = m3_CallV(function);
    if (result) {
        std::cerr << "Failed to call " << functionName << ": " << result << "\n";
        return 1;
    }

    // Retrieve result (assuming the function returns an integer exit code)
    int32_t exit_code;
    result = m3_GetResultsV(function, &exit_code);
    if (result) {
        std::cerr << "Failed to retrieve result: " << result << "\n";
    } else {
        std::cout << "Exit code: " << exit_code << "\n";
    }

    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_rk_wasm3_Wasm3_cleanupWasm(JNIEnv *jenv, jobject obj) {
    // Retrieve the class reference of the Java object
    jclass clazz = jenv->GetObjectClass(obj);

    // Retrieve the field IDs for the fields that hold the environment and runtime
    jfieldID envField = jenv->GetFieldID(clazz, "envField", "J");
    jfieldID runtimeField = jenv->GetFieldID(clazz, "runtimeField", "J");

    // Retrieve the long values stored in the Java object
    auto env = (IM3Environment) jenv->GetLongField(obj, envField);
    auto runtime = (IM3Runtime) jenv->GetLongField(obj, runtimeField);

    // Cleanup resources if they exist
    if (env) {
        m3_FreeEnvironment(env);
        jenv->SetLongField(obj, envField, (jlong) 0); // Set to 0 to avoid future invalid access
    }
    if (runtime) {
        m3_FreeRuntime(runtime);
        jenv->SetLongField(obj, runtimeField, (jlong) 0); // Set to 0 to avoid future invalid access
    }

}
