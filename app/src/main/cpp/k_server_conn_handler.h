//
// Created by Si_Li on 2018/4/23.
//

#ifndef TITAN_K_SERVER_CONN_HANDLER_H
#define TITAN_K_SERVER_CONN_HANDLER_H

#include "k_util/k_handler.h"

class k_server_conn_hander : public k_handler
{
public:
    k_server_conn_hander();
    ~k_server_conn_hander();

    enum
    {
        MSG_HEAD_LEN = sizeof(uint16_t)  + sizeof(uint32_t)
    };

    int handle_read(k_thread_task* task, k_event* ev, k_socket* sock);
    void handle_del(k_thread_task* task, k_event* ev, k_socket* sock);

private:
    char m_buf[4096];
    char head_buf[MSG_HEAD_LEN];
    char* m_rebuf;
    uint32_t m_rebuf_size;

};

#endif //TITAN_K_SERVER_CONN_HANDLER_H
