#include <jni.h>
#include <string>

#include "stdafx.h"

extern "C"
JNIEXPORT jstring
JNICALL
Java_com_cnr_ffmpegx264_jniinterface_FFmpegBridge_stringFromFFmpeg(
        JNIEnv *env,
        jobject /* this */) {
    char info[10000] = { 0 };
    sprintf(info, "%s\n", avcodec_configuration());
    return env->NewStringUTF(info);
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_cnr_ffmpegx264_jniinterface_FFmpegBridge_encodeFrame2H264(JNIEnv *env, jclass type,
                                                                   jbyteArray data_) {
    jbyte *data = env->GetByteArrayElements(data_, NULL);

    env->ReleaseByteArrayElements(data_, data, 0);
}

