#ifndef __K_RTP_CONTEXT_H__
#define __K_RTP_CONTEXT_H__

#include <stdint.h>
#include "k_util/k_socket.h"

class k_rtp_sender
{
public:
	enum
	{
		MAX_PAYLOAD_SIZE = 1000
	};

	k_rtp_sender(k_socket* p_socket, int payload, uint32_t msg_id);
	~k_rtp_sender();

	void ff_rtp_send_data(uint8_t *buf1, int len, int m);
	void set_timestamp(uint32_t ts);
	uint8_t* get_payload_buf();

private:
	enum
	{
		RTP_VERSION = 2,
		RTP_HEADER_LENGTH = 22
	};

	k_socket* m_socket;
	uint32_t m_msg_id;
	int m_seq;
	int m_payload;
	uint32_t m_timestamp;
	uint32_t m_ssrc;
	uint8_t m_rtp_buf[RTP_HEADER_LENGTH + MAX_PAYLOAD_SIZE];
};

#endif