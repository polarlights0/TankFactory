#include <jni.h>
#include <android/bitmap.h>
#include <cstdlib>
#include <cstring>

#include "prism_algo.h"

namespace {

struct LockedBitmap {
    JNIEnv* env = nullptr;
    jobject bitmap = nullptr;
    void* pixels = nullptr;
    AndroidBitmapInfo info{};

    bool lock(JNIEnv* e, jobject bmp) {
        env = e;
        bitmap = bmp;
        if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return false;
        if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return false;
        if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return false;
        return true;
    }

    void unlock() {
        if (pixels != nullptr) {
            AndroidBitmap_unlockPixels(env, bitmap);
            pixels = nullptr;
        }
    }

    uint32_t* data() const { return (uint32_t*)pixels; }
};

jobject createArgbBitmap(JNIEnv* env, int width, int height) {
    jclass bitmap_class = env->FindClass("android/graphics/Bitmap");
    if (bitmap_class == nullptr) return nullptr;
    jclass config_class = env->FindClass("android/graphics/Bitmap$Config");
    if (config_class == nullptr) {
        env->DeleteLocalRef(bitmap_class);
        return nullptr;
    }
    jfieldID argb_field = env->GetStaticFieldID(config_class, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    if (argb_field == nullptr) {
        env->DeleteLocalRef(config_class);
        env->DeleteLocalRef(bitmap_class);
        return nullptr;
    }
    jobject argb_config = env->GetStaticObjectField(config_class, argb_field);
    jmethodID create_method = env->GetStaticMethodID(
            bitmap_class, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jobject result = nullptr;
    if (create_method != nullptr && argb_config != nullptr) {
        result = env->CallStaticObjectMethod(bitmap_class, create_method, (jint)width, (jint)height, argb_config);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            result = nullptr;
        }
    }
    if (argb_config != nullptr) env->DeleteLocalRef(argb_config);
    env->DeleteLocalRef(config_class);
    env->DeleteLocalRef(bitmap_class);
    return result;
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_domity_tankfactory_prismtank_PrismTankCoder_encodeNative(
        JNIEnv* env, jobject, jobject inner_bitmap, jobject cover_bitmap, jobject out_bitmap,
        jint inner_threshold, jint cover_threshold, jint inner_contrast, jint cover_contrast,
        jboolean inner_gray, jboolean cover_gray, jint slope, jint gap, jboolean is_row, jboolean is_reverse) {
    LockedBitmap inner;
    LockedBitmap cover;
    LockedBitmap out;

    bool ok = false;
    do {
        if (!inner.lock(env, inner_bitmap)) break;
        if (!cover.lock(env, cover_bitmap)) break;
        if (!out.lock(env, out_bitmap)) break;

        if (inner.info.width != cover.info.width || inner.info.height != cover.info.height) break;
        if (inner.info.width != out.info.width || inner.info.height != out.info.height) break;

        uint8_t inner_lut[256];
        uint8_t cover_lut[256];
        if (inner_contrast == 0) {
            prism::build_identity_lut(inner_lut);
        } else {
            prism::build_contrast_lut((int)inner_contrast, inner_lut);
        }
        if (cover_contrast == 0) {
            prism::build_identity_lut(cover_lut);
        } else {
            prism::build_contrast_lut((int)cover_contrast, cover_lut);
        }

        prism::EncodeConfig cfg;
        cfg.inner_threshold = (int)inner_threshold;
        cfg.cover_threshold = (int)cover_threshold;
        cfg.slope = (int)slope;
        cfg.gap = (int)gap;
        cfg.is_row = is_row == JNI_TRUE;
        cfg.is_reverse = is_reverse == JNI_TRUE;

        prism::encode(inner.data(), cover.data(), out.data(), (int)inner.info.width, (int)inner.info.height,
                      cfg, inner_lut, cover_lut, inner_gray == JNI_TRUE, cover_gray == JNI_TRUE);
        ok = true;
    } while (false);

    out.unlock();
    cover.unlock();
    inner.unlock();

    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jobject JNICALL
Java_io_github_domity_tankfactory_prismtank_PrismTankCoder_decodeNative(
        JNIEnv* env, jobject, jobject src_bitmap, jint lower_threshold, jint higher_threshold, jint method,
        jint contrast, jint iterations) {
    LockedBitmap src;
    if (!src.lock(env, src_bitmap)) {
        src.unlock();
        return nullptr;
    }

    const int width = (int)src.info.width;
    const int height = (int)src.info.height;
    const int total = width * height;
    if (total <= 0) {
        src.unlock();
        return nullptr;
    }

    jobject result_bitmap = createArgbBitmap(env, width, height);
    if (result_bitmap == nullptr) {
        src.unlock();
        return nullptr;
    }

    LockedBitmap out;
    if (!out.lock(env, result_bitmap)) {
        out.unlock();
        src.unlock();
        env->DeleteLocalRef(result_bitmap);
        return nullptr;
    }

    uint8_t lut[256];
    if (contrast == 0) {
        prism::build_identity_lut(lut);
    } else {
        prism::build_contrast_lut((int)contrast, lut);
    }

    prism::DecodeConfig cfg;
    cfg.lower_threshold = (int)lower_threshold;
    cfg.higher_threshold = (int)higher_threshold;
    cfg.method = (int)method;
    cfg.iterations = (int)iterations;

    uint8_t* mask = (uint8_t*)malloc((size_t)total);
    uint32_t* tmp_color = (uint32_t*)malloc((size_t)total * sizeof(uint32_t));
    uint8_t* tmp_mask = (uint8_t*)malloc((size_t)total);

    prism::decode(src.data(), out.data(), width, height, cfg, lut, mask, tmp_color, tmp_mask);

    if (mask) free(mask);
    if (tmp_color) free(tmp_color);
    if (tmp_mask) free(tmp_mask);

    out.unlock();
    src.unlock();

    return result_bitmap;
}
