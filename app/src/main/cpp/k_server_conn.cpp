//
// Created by Si_Li on 2018/4/22.
//

#include "k_util/k_util.h"
#include <malloc.h>
#include <jsoncpp/json/json.h>
#include <cstring>
#include "k_server_conn.h"
#include "k_server_conn_handler.h"

JavaVM* k_server_conn::g_jvm = NULL;

k_server_conn::k_server_conn()
: m_server_socket(NULL)
, m_media_sender(NULL)
, m_jni_env(NULL)
, m_callback(NULL)
{

}

int k_server_conn::process_msg(k_msg &msg) {
    switch (msg.m_msg_id)
    {
        case 3:
        {
            if(m_callback != NULL)
            {
                m_jni_env->DeleteGlobalRef(m_callback);
                m_callback = NULL;
            }

            if (m_jni_env)
            {
                g_jvm->DetachCurrentThread();
                m_jni_env = NULL;
            }
            return k_thread_task::process_msg(msg);
        }
            break;
        case 600:
        {
            k_init_conn_msg* pmsg = static_cast<k_init_conn_msg*> (msg.m_data);
            this->init_conn(pmsg);
            delete pmsg;

            return 0;
        }
            break;

        case 601:
        case 602:
        {
            k_media_msg* pmsg = static_cast<k_media_msg*> (msg.m_data);

            if (m_media_sender == NULL)
            {
                LOGE("media sender null");
            } else
            {
                if(msg.m_msg_id == 601)
                {
                    m_media_sender->ff_rtp_send_h264(pmsg->m_data, pmsg->m_size, pmsg->m_ts);
                } else
                {
                    m_media_sender->ff_rtp_send_audio(pmsg->m_data, pmsg->m_size, pmsg->m_ts);
                }
            }

            free(pmsg->m_data);
            delete pmsg;

            return 0;
        }
            break;

        case 603:
        {
            if(m_server_socket)
                this->del_event(m_server_socket);

            return 0;
        }
            break;

        default:
            return k_thread_task::process_msg(msg);
    }

}

#define MSG_HEAD_LENGTH 10

int k_server_conn::init_conn(k_init_conn_msg *pmsg) {
    if(m_jni_env == NULL)
    {
        if(g_jvm->AttachCurrentThread(&m_jni_env, NULL) != JNI_OK)
        {
            LOGE("%s: AttachCurrentThread() failed", __FUNCTION__);
            return -1;
        }
    }

    if (m_server_socket)
    {
        LOGE("sock change");
        this->del_event(m_server_socket);

        k_init_conn_msg* msg = new k_init_conn_msg;
        msg->m_server_ip = pmsg->m_server_ip;
        msg->m_server_port = pmsg->m_server_port;
        msg->m_user_id = pmsg->m_user_id;
        msg->m_password = pmsg->m_password;
        msg->m_callback = pmsg->m_callback;

        k_msg new_msg = {600, msg};

        if(this->enque_msg(new_msg))
        {
            m_jni_env->DeleteGlobalRef(pmsg->m_callback);
            delete msg;
        }

        return 0;
    }

    if(m_callback != NULL)
    {
        m_jni_env->DeleteGlobalRef(m_callback);
    }

    m_callback = pmsg->m_callback;

    LOGE("new sock");
    m_server_socket = new k_socket;
    m_server_socket->init(AF_INET, SOCK_STREAM);
    k_sockaddr addr;

    if(addr.init(AF_INET, pmsg->m_server_ip, pmsg->m_server_port))
    {
        LOGE("addr init failed");
        login_callback(-1);
        delete m_server_socket;
        m_server_socket = NULL;
        return -1;
    }

    if (m_server_socket->k_connect(addr))
    {
        LOGE("connect failed");
        login_callback(-2);
        delete m_server_socket;
        m_server_socket = NULL;
        return -1;
    }

    Json::Value jsonRoot; //定义根节点
    jsonRoot["user_id"] = pmsg->m_user_id.c_str(); //添加数据
    jsonRoot["password"] = pmsg->m_password.c_str();

    Json::StreamWriterBuilder writer;
    std::string document = Json::writeString(writer, jsonRoot);

    char buf[4096];
    uint8_t * p_buf  = (uint8_t*)buf;
    int tot_len = document.size() + MSG_HEAD_LENGTH;
    int msg_id = 1002;

    k_util::avio_wb16(p_buf, 0x1234);//magic
    k_util::avio_wb32(p_buf, tot_len);
    k_util::avio_wb32(p_buf, msg_id);

    memcpy(p_buf, document.c_str(), document.size());

    int ret = m_server_socket->k_send(buf, tot_len);
    if (ret != tot_len)
    {
        LOGE("send failed");
        login_callback(-3);
        delete m_server_socket;
        m_server_socket = NULL;
        return -1;
    }

    k_server_conn_hander* handler = new k_server_conn_hander;
    this->add_event(m_server_socket, handler, k_event::READ_MASK);
    m_media_sender = new k_media_sender(m_server_socket);

    return 0;
}

void k_server_conn::clear_server_socket() {
    LOGE("clear sock");
    m_server_socket = NULL;
    delete m_media_sender;
    m_media_sender = NULL;
}

void k_server_conn::login_callback(int ret) {
    jclass  cls = m_jni_env->GetObjectClass(m_callback);
    if(cls == NULL)
    {
        LOGE("FindClass() Error.....");
        return ;
    }

    jmethodID mid = m_jni_env->GetMethodID(cls, "onLoginCallback", "(I)V");
    if (mid == NULL)
    {
        LOGE("GetMethodID() Error.....");
        return  ;
    }

    m_jni_env->CallVoidMethod(m_callback, mid, ret);
}

void k_server_conn::disconnect_callback(int ret) {
    jclass  cls = m_jni_env->GetObjectClass(m_callback);
    if(cls == NULL)
    {
        LOGE("FindClass() Error.....");
        return ;
    }

    jmethodID mid = m_jni_env->GetMethodID(cls, "onDisconnectCallback", "(I)V");
    if (mid == NULL)
    {
        LOGE("GetMethodID() Error.....");
        return  ;
    }

    m_jni_env->CallVoidMethod(m_callback, mid, ret);
}