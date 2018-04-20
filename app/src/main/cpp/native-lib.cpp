#include <jni.h>
#include <string>

#include "stdafx.h"

extern "C"
JNIEXPORT jstring
JNICALL
Java_com_cnr_ffmpegx264_MainActivity_stringFromFFmpeg(
        JNIEnv *env,
        jobject /* this */) {
    char info[10000] = { 0 };
    sprintf(info, "%s\n", avcodec_configuration());
    return env->NewStringUTF(info);
}


