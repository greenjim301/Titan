//
// Created by Si_Li on 2018/4/22.
//

#ifndef TITAN_K_SERVER_CONN_H
#define TITAN_K_SERVER_CONN_H


#include <jni.h>
#include "k_util/k_thread_task.h"
#include "k_util/k_string.h"
#include "k_media_sender.h"

struct k_init_conn_msg
{
    k_string m_user_id;
    k_string m_password;
    k_string m_server_ip;
    int m_server_port;
    jobject m_callback;
};

struct k_media_msg
{
    uint8_t * m_data;
    int m_size;
    uint32_t m_ts;
};

class k_server_conn : public  k_thread_task{
public:
    k_server_conn();

    virtual int process_msg(k_msg& msg);

    void clear_server_socket();
    void login_callback(int ret);
    void disconnect_callback(int ret);

    static JavaVM *g_jvm;

private:
    int init_conn(k_init_conn_msg* pmsg);

private:
    k_socket* m_server_socket;
    k_media_sender* m_media_sender;
    JNIEnv* m_jni_env;
    jobject m_callback;
};


#endif //TITAN_K_SERVER_CONN_H
