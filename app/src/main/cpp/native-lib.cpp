#include <jni.h>
#include <string>
#include <map>
#include <stdlib.h>
#include <string.h>


#include "k_util/k_string.h"
#include "k_util/k_sockaddr.h"
#include "k_util/k_socket.h"
#include "k_media_sender.h"
#include "k_server_conn.h"
#include "k_util/k_util.h"
#include "libyuv/rotate.h"

static k_server_conn* m_server_conn = NULL;

extern "C"
JNIEXPORT void JNICALL
Java_com_lisi_titan_TitanNativeLib_nativeSendMeidaData(JNIEnv *env, jobject type,
                                               jbyteArray buf_, jint len, jlong timestamp,
                                               jint msgid) {
    jbyte *buf = env->GetByteArrayElements(buf_, NULL);

    if (m_server_conn)
    {
        k_media_msg* media = new k_media_msg;
        media->m_size = len;
        media->m_ts = timestamp;
        media->m_data = (uint8_t*)malloc(len);
        memcpy(media->m_data, buf, len);

        k_msg msg = {msgid, media};

        if(m_server_conn->enque_msg(msg))
        {
            LOGE("enque media failed");
            free(media->m_data);
            delete media;
        }
    } else
    {
        LOGE("server conn null");
    }

    env->ReleaseByteArrayElements(buf_, buf, 0);
}


extern "C"
JNIEXPORT void JNICALL
Java_com_lisi_titan_TitanNativeLib_nativeYUVRoate(JNIEnv *env, jobject type,
                                                      jbyteArray yuv_, jint src_width, jint src_height,
                                                      jint rotate) {
    jbyte *yuv = env->GetByteArrayElements(yuv_, NULL);

    int src_i420_y_size = src_width * src_height;
    int src_i420_uv_size = ((src_width + 1) / 2) * ((src_height + 1) / 2);

    int dst_width = src_width;
    int dst_height = src_height;

    if (rotate == 90 || rotate == 270){
        dst_width = src_height;
        dst_height = src_width;
    }

    int dst_i420_y_size = dst_width * dst_height;
    int dst_i420_uv_size = ((dst_width + 1) / 2) * ((dst_height + 1) / 2);
    int dst_i420_size = dst_i420_y_size + dst_i420_uv_size * 2;

    uint8_t * src_i420 = (uint8_t*)yuv;
    uint8_t * dst_i420_c = (uint8_t*) malloc(dst_i420_size);

    enum libyuv::RotationMode mode = (enum libyuv::RotationMode)rotate;

    libyuv::I420Rotate(src_i420, src_width,
               src_i420 + src_i420_y_size, (src_width + 1) / 2,
               src_i420 + src_i420_y_size + src_i420_uv_size, (src_width + 1) / 2,
               dst_i420_c, dst_width,
               dst_i420_c + dst_i420_y_size, (dst_width + 1) / 2,
               dst_i420_c + dst_i420_y_size + dst_i420_uv_size,
               (dst_width + 1) / 2,
               src_width, src_height, mode);

    memcpy(src_i420, dst_i420_c, dst_i420_size);
    free(dst_i420_c);

    env->ReleaseByteArrayElements(yuv_, yuv, 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_lisi_titan_TitanNativeLib_nativeInitServerConn(JNIEnv *env, jobject type, jstring serverIP_,
                                                       jint serverPort, jstring userID_,
                                                       jstring password_, jobject callback) {
    const char *serverIP = env->GetStringUTFChars(serverIP_, 0);
    const char *userID = env->GetStringUTFChars(userID_, 0);
    const char *password = env->GetStringUTFChars(password_, 0);

    if(!m_server_conn)
    {
        LOGE("new server conn");
        m_server_conn = new k_server_conn;
        m_server_conn->init();
    }

    LOGE("init server conn");

    k_init_conn_msg* init = new k_init_conn_msg;
    init->m_server_port = serverPort;
    init->m_server_ip = serverIP;
    init->m_user_id = userID;
    init->m_password = password;
    init->m_callback = env->NewGlobalRef(callback);

    k_msg msg = {600, init};

    if(m_server_conn->enque_msg(msg))
    {
        env->DeleteGlobalRef(init->m_callback);
        LOGE("enque init failed");
        delete init;
    }

    env->ReleaseStringUTFChars(serverIP_, serverIP);
    env->ReleaseStringUTFChars(userID_, userID);
    env->ReleaseStringUTFChars(password_, password);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_lisi_titan_TitanNativeLib_nativeShutServerConn(JNIEnv *env, jobject type) {
    if(m_server_conn)
    {
        LOGE("shut server conn");
        k_msg msg = {603, NULL};
        m_server_conn->enque_msg(msg);
    }
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved)
{
    JNIEnv* env = NULL;
    jint result = -1;

    //获取JNI版本
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK)
    {
        LOGE("GetEnv failed!");
        return result;
    }

    k_server_conn::g_jvm = vm;
    LOGE("GetEnv success!");

    return JNI_VERSION_1_6;
}
