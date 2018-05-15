//#include <jni.h>
//#include <string>
//#include <thread>
//#include <memory>
//#include "stdafx.h"

#include "cain_recorder.h"
using namespace std;

AVFormatContext *inputContext = nullptr;


CainRecorder *recorder;
extern "C"
JNIEXPORT jint JNICALL
Java_com_cnr_ffmpegx264_jniinterface_FFmpegBridge_encodeFrame2H264(JNIEnv *env, jclass type,
                                                                   jbyteArray data_) {
    jbyte *elements = env->GetByteArrayElements(data_, 0);
    recorder->avcEncode(elements);
    env->ReleaseByteArrayElements(data_,elements,0);
    return 0;

}


extern "C"
JNIEXPORT jint JNICALL
Java_com_cnr_ffmpegx264_jniinterface_FFmpegBridge_stringFromFFmpeg(JNIEnv *env, jclass type,
                                                                   jstring url_) {
    const char *url = env->GetStringUTFChars(url_, 0);

    // TODO

    env->ReleaseStringUTFChars(url_, url);
}

/**
 * 初始化编码器
 * @param env
 * @param obj
 * @param videoPath_    视频路径
 * @param previewWidth  预览宽度
 * @param previewHeight 预览高度
 * @param videoWidth    录制视频宽度
 * @param videoHeight   录制视频高度
 * @param frameRate     帧率
 * @param bitRate       视频比特率
 * @param audioBitRate  音频比特率
 * @param audioSampleRate  音频采样频率
 * @return
 */
extern "C"
JNIEXPORT jint
JNICALL Java_com_cnr_ffmpegx264_jniinterface_FFmpegBridge_initMediaRecorder
        (JNIEnv *env, jclass obj, jstring videoPath_, jint previewWidth, jint previewHeight,
         jint videoWidth, jint videoHeight, jint frameRate, jint bitRate, jboolean enableAudio,
         jint audioBitRate, jint audioSampleRate) {

    // 配置参数
    const char * videoPath = env->GetStringUTFChars(videoPath_, 0);
    EncoderParams *params = (EncoderParams *)malloc(sizeof(EncoderParams));
    params->mediaPath = videoPath;
    params->previewWidth = previewWidth;
    params->previewHeight = previewHeight;
    params->videoWidth = videoWidth;
    params->videoHeight = videoHeight;
    params->frameRate = frameRate;
    params->bitRate = bitRate;
    // 是否允许音频编码
    if (enableAudio) {
        params->enableAudio = 1;
    } else {
        params->enableAudio = 0;
    }
    params->audioBitRate = audioBitRate;
    params->audioSampleRate = audioSampleRate;
    // 初始化录制器
    recorder = new CainRecorder(params);
    return recorder->initRecorder();
}
extern "C"
JNIEXPORT void JNICALL
Java_com_cnr_ffmpegx264_jniinterface_FFmpegBridge_startRecord(JNIEnv *env, jclass type) {

    if (!recorder) {
        return;
    }
    recorder->startRecord();



}


extern "C"
JNIEXPORT void JNICALL
Java_com_cnr_ffmpegx264_jniinterface_FFmpegBridge_openRecord(JNIEnv *env, jclass type) {

    // TODO
    av_register_all();

}
extern "C"
JNIEXPORT jint JNICALL
Java_com_cnr_ffmpegx264_jniinterface_FFmpegBridge_prepareJXFFmpegEncoder(JNIEnv *env, jclass type,
                                                                         jstring mediaBasePath_,
                                                                         jstring mediaName_,
                                                                         jint filter, jint in_width,
                                                                         jint in_height,
                                                                         jint out_width,
                                                                         jint out_height,
                                                                         jint frameRate,
                                                                         jlong bit_rate) {
    const char *mediaBasePath = env->GetStringUTFChars(mediaBasePath_, 0);
    const char *mediaName = env->GetStringUTFChars(mediaName_, 0);

    // TODO

    env->ReleaseStringUTFChars(mediaBasePath_, mediaBasePath);
    env->ReleaseStringUTFChars(mediaName_, mediaName);
}extern "C"
JNIEXPORT void JNICALL
Java_com_cnr_ffmpegx264_jniinterface_FFmpegBridge_stopRecord(JNIEnv *env, jclass type) {

    if (!recorder) {
        return;
    }
    recorder->recordEndian();

}extern "C"
JNIEXPORT jint JNICALL
Java_com_cnr_ffmpegx264_jniinterface_FFmpegBridge_encodePCMFrame(JNIEnv *env, jclass type,
                                                                 jbyteArray data_, jint len)
{
    if (!recorder) {
        return 0;
    }
    jbyte *data = env->GetByteArrayElements(data_, NULL);

    // 音频编码
    recorder->aacEncode(data);

    env->ReleaseByteArrayElements(data_, data, 0);
}