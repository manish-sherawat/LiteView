#include <jni.h>
#include <string>
#include <android/log.h>
#include <android/bitmap.h>
#include <dlfcn.h>
#include "LibreOfficeKit/LibreOfficeKit.h"

#define TAG "NexusLOKitBridge"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

typedef LibreOfficeKit* (*LokInitFunc)(const char* pInstallPath, const char* pUserProfileUrl);

static void* sLibreOfficeHandle = nullptr;
static LokInitFunc sLokInitFunc = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_nexus_feature_reader_presentation_engine_LOKitBridge_nativeIsEngineAvailable(
    JNIEnv* env,
    jobject /* this */
) {
    if (sLokInitFunc != nullptr) {
        return JNI_TRUE;
    }
    // Check if libreoffice library is already loaded or in system path
    void* handle = dlopen("liblo-native-code.so", RTLD_NOW | RTLD_GLOBAL);
    if (!handle) {
        handle = dlopen("libsofficeapp.so", RTLD_NOW | RTLD_GLOBAL);
    }
    if (handle) {
        auto initFunc = (LokInitFunc)dlsym(handle, "libreofficekit_hook_2");
        if (initFunc) {
            sLibreOfficeHandle = handle;
            sLokInitFunc = initFunc;
            return JNI_TRUE;
        }
    }
    return JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_nexus_feature_reader_presentation_engine_LOKitBridge_nativeInit(
    JNIEnv* env,
    jobject /* this */,
    jstring jInstallPath,
    jstring jUserProfileUrl
) {
    const char* installPath = jInstallPath ? env->GetStringUTFChars(jInstallPath, nullptr) : "";
    const char* userProfileUrl = jUserProfileUrl ? env->GetStringUTFChars(jUserProfileUrl, nullptr) : "";

    LOGI("nativeInit: initializing LibreOfficeKit with installPath=%s", installPath);

    if (sLokInitFunc == nullptr) {
        // Attempt dlopen
        void* handle = dlopen("liblo-native-code.so", RTLD_NOW | RTLD_GLOBAL);
        if (!handle) {
            handle = dlopen("libsofficeapp.so", RTLD_NOW | RTLD_GLOBAL);
        }
        if (handle) {
            sLibreOfficeHandle = handle;
            sLokInitFunc = (LokInitFunc)dlsym(handle, "libreofficekit_hook_2");
        }
    }

    if (sLokInitFunc == nullptr) {
        LOGE("nativeInit: libreofficekit_hook_2 not found (native binaries not loaded)");
        if (jInstallPath) env->ReleaseStringUTFChars(jInstallPath, installPath);
        if (jUserProfileUrl) env->ReleaseStringUTFChars(jUserProfileUrl, userProfileUrl);
        return 0;
    }

    LibreOfficeKit* pKit = sLokInitFunc(installPath, userProfileUrl);

    if (jInstallPath) env->ReleaseStringUTFChars(jInstallPath, installPath);
    if (jUserProfileUrl) env->ReleaseStringUTFChars(jUserProfileUrl, userProfileUrl);

    if (!pKit) {
        LOGE("nativeInit: lok_init failed to return instance");
        return 0;
    }

    LOGI("nativeInit: LibreOfficeKit initialized successfully at %p", pKit);
    return reinterpret_cast<jlong>(pKit);
}

JNIEXPORT jlong JNICALL
Java_com_nexus_feature_reader_presentation_engine_LOKitBridge_nativeDocumentLoad(
    JNIEnv* env,
    jobject /* this */,
    jlong lokHandle,
    jstring jDocPath,
    jstring jOptions
) {
    auto* pKit = reinterpret_cast<LibreOfficeKit*>(lokHandle);
    if (!pKit || !pKit->pClass) {
        LOGE("nativeDocumentLoad: invalid LibreOfficeKit handle");
        return 0;
    }

    const char* docPath = env->GetStringUTFChars(jDocPath, nullptr);
    const char* options = jOptions ? env->GetStringUTFChars(jOptions, nullptr) : nullptr;

    LOGI("nativeDocumentLoad: loading document %s", docPath);

    LibreOfficeKitDocument* pDoc = nullptr;
    if (options && pKit->pClass->documentLoadWithOptions) {
        pDoc = pKit->pClass->documentLoadWithOptions(pKit, docPath, options);
    } else if (pKit->pClass->documentLoad) {
        pDoc = pKit->pClass->documentLoad(pKit, docPath);
    }

    env->ReleaseStringUTFChars(jDocPath, docPath);
    if (jOptions && options) env->ReleaseStringUTFChars(jOptions, options);

    if (!pDoc) {
        const char* err = pKit->pClass->getError ? pKit->pClass->getError(pKit) : "unknown error";
        LOGE("nativeDocumentLoad failed: %s", err ? err : "null");
        return 0;
    }

    LOGI("nativeDocumentLoad: document loaded successfully at %p", pDoc);
    return reinterpret_cast<jlong>(pDoc);
}

JNIEXPORT jint JNICALL
Java_com_nexus_feature_reader_presentation_engine_LOKitBridge_nativeGetParts(
    JNIEnv* /* env */,
    jobject /* this */,
    jlong docHandle
) {
    auto* pDoc = reinterpret_cast<LibreOfficeKitDocument*>(docHandle);
    if (!pDoc || !pDoc->pClass || !pDoc->pClass->getParts) {
        return 0;
    }
    return pDoc->pClass->getParts(pDoc);
}

JNIEXPORT jstring JNICALL
Java_com_nexus_feature_reader_presentation_engine_LOKitBridge_nativeGetPartName(
    JNIEnv* env,
    jobject /* this */,
    jlong docHandle,
    jint partIndex
) {
    auto* pDoc = reinterpret_cast<LibreOfficeKitDocument*>(docHandle);
    if (!pDoc || !pDoc->pClass || !pDoc->pClass->getPartName) {
        return nullptr;
    }
    char* name = pDoc->pClass->getPartName(pDoc, partIndex);
    return name ? env->NewStringUTF(name) : nullptr;
}

JNIEXPORT jlongArray JNICALL
Java_com_nexus_feature_reader_presentation_engine_LOKitBridge_nativeGetDocumentSize(
    JNIEnv* env,
    jobject /* this */,
    jlong docHandle
) {
    auto* pDoc = reinterpret_cast<LibreOfficeKitDocument*>(docHandle);
    if (!pDoc || !pDoc->pClass || !pDoc->pClass->getDocumentSize) {
        return nullptr;
    }

    long widthTwips = 0;
    long heightTwips = 0;
    pDoc->pClass->getDocumentSize(pDoc, &widthTwips, &heightTwips);

    jlongArray result = env->NewLongArray(2);
    jlong buffer[2] = { static_cast<jlong>(widthTwips), static_cast<jlong>(heightTwips) };
    env->SetLongArrayRegion(result, 0, 2, buffer);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_nexus_feature_reader_presentation_engine_LOKitBridge_nativeRenderSlideToBitmap(
    JNIEnv* env,
    jobject /* this */,
    jlong docHandle,
    jint partIndex,
    jobject jBitmap,
    jint width,
    jint height
) {
    auto* pDoc = reinterpret_cast<LibreOfficeKitDocument*>(docHandle);
    if (!pDoc || !pDoc->pClass || !pDoc->pClass->paintTile) {
        LOGE("nativeRenderSlideToBitmap: invalid document handle or missing paintTile");
        return JNI_FALSE;
    }

    // Set active slide/part
    if (pDoc->pClass->setPart) {
        pDoc->pClass->setPart(pDoc, partIndex);
    }

    long docWidthTwips = 0;
    long docHeightTwips = 0;
    if (pDoc->pClass->getDocumentSize) {
        pDoc->pClass->getDocumentSize(pDoc, &docWidthTwips, &docHeightTwips);
    }
    if (docWidthTwips <= 0) docWidthTwips = 28800; // Default 16:9 twips fallback
    if (docHeightTwips <= 0) docHeightTwips = 16200;

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, jBitmap, &info) < 0) {
        LOGE("nativeRenderSlideToBitmap: failed to get bitmap info");
        return JNI_FALSE;
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, jBitmap, &pixels) < 0) {
        LOGE("nativeRenderSlideToBitmap: failed to lock bitmap pixels");
        return JNI_FALSE;
    }

    // Render full slide into the bitmap buffer
    pDoc->pClass->paintTile(
        pDoc,
        static_cast<unsigned char*>(pixels),
        width,
        height,
        0, 0, // tile coordinates (0, 0)
        static_cast<int>(docWidthTwips),
        static_cast<int>(docHeightTwips)
    );

    AndroidBitmap_unlockPixels(env, jBitmap);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_nexus_feature_reader_presentation_engine_LOKitBridge_nativeCloseDocument(
    JNIEnv* /* env */,
    jobject /* this */,
    jlong docHandle
) {
    auto* pDoc = reinterpret_cast<LibreOfficeKitDocument*>(docHandle);
    if (pDoc && pDoc->pClass && pDoc->pClass->destroy) {
        pDoc->pClass->destroy(pDoc);
        LOGI("nativeCloseDocument: destroyed document %p", pDoc);
    }
}

JNIEXPORT void JNICALL
Java_com_nexus_feature_reader_presentation_engine_LOKitBridge_nativeDestroy(
    JNIEnv* /* env */,
    jobject /* this */,
    jlong lokHandle
) {
    auto* pKit = reinterpret_cast<LibreOfficeKit*>(lokHandle);
    if (pKit && pKit->pClass && pKit->pClass->destroy) {
        pKit->pClass->destroy(pKit);
        LOGI("nativeDestroy: destroyed LibreOfficeKit %p", pKit);
    }
}

} // extern "C"
