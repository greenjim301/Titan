//
// Created by Si_Li on 2018/4/23.
//

#include <cstring>
#include <cstdlib>
#include <jsoncpp/json/json.h>
#include <k_util/k_util.h>
#include "k_server_conn_handler.h"
#include "k_server_conn.h"

int k_server_conn_hander::handle_read(k_thread_task *task, k_event *ev, k_socket *sock) {
    int ret = sock->k_recv(m_buf, 4096);

    if (ret <= 0) {
        k_server_conn *conn = dynamic_cast<k_server_conn *>(task);
        conn->disconnect_callback(-4);

        printf("rev failed:%d\n", ret);
        task->del_event(sock);
        return 0;
    }

    char *p = m_buf;
    uint32_t size = ret;

    while (size > 0) {
        if (size < MSG_HEAD_LEN) {
            int need = MSG_HEAD_LEN - size;

            memcpy(head_buf, p, size);
            if (sock->k_recv_n(head_buf + size, need)) {
                printf("recv_n failed\n");
                task->del_event(sock);
                return -1;
            }

            p = head_buf;
            size = MSG_HEAD_LEN;
        }

        uint16_t magic = *(uint16_t *) p;
        magic = ntohs(magic);
        p += sizeof(uint16_t);
        size -= sizeof(uint16_t);

        if (magic != 0x1234) {
            printf("magic:%d wrong\n", magic);
            return -1;
        }

        uint32_t msg_len = *(uint32_t *) p;
        msg_len = ntohl(msg_len);
        msg_len -= MSG_HEAD_LEN;
        p += sizeof(uint32_t);
        size -= sizeof(uint32_t);

        if (msg_len > size) {
            uint32_t need = msg_len - size;

            if (msg_len > m_rebuf_size) {
                if (m_rebuf) {
                    free(m_rebuf);
                }

                m_rebuf = (char *) malloc(msg_len);
                m_rebuf_size = msg_len;
            }

            memcpy(m_rebuf, p, size);
            if (sock->k_recv_n(m_rebuf + size, need)) {
                printf("recv_n 2 failed\n");
                task->del_event(sock);
                return -1;
            }

            p = m_rebuf;
            size = msg_len;
        }

        uint32_t msg_id = *(uint32_t *) p;
        msg_id = ntohl(msg_id);
        char *mbuf = (char *) p + sizeof(uint32_t);
        int mlen = msg_len - sizeof(uint32_t);

        if (msg_id == 1003) {
            Json::Reader reader;
            Json::Value root;
            // reader将Json字符串解析到root，root将包含Json里所有子元素
            if (!reader.parse(mbuf, mbuf + mlen, root)) {
                printf("json parse failed\n");
                return -1;
            }

            int result = root["result"].asInt();
            k_string describe = root["describe"].asCString();

            LOGE("login rsp %d:%s", result, describe.c_str());

            k_server_conn *conn = dynamic_cast<k_server_conn *>(task);
            conn->login_callback(result);
        }


        p += msg_len;
        size -= msg_len;
    }

    return 0;
}

void k_server_conn_hander::handle_del(k_thread_task *task, k_event *ev, k_socket *sock) {
    k_server_conn *conn = dynamic_cast<k_server_conn *>(task);
    conn->clear_server_socket();

    k_handler::handle_del(task, ev, sock);
}

k_server_conn_hander::k_server_conn_hander()
        : m_rebuf(NULL), m_rebuf_size(0) {

}

k_server_conn_hander::~k_server_conn_hander() {
    if (m_rebuf)
        free(m_rebuf);

}
