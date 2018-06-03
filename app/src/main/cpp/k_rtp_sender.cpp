#include <k_util/k_util.h>
#include "k_rtp_sender.h"

k_rtp_sender::k_rtp_sender(k_socket* p_socket, int payload, uint32_t msg_id)
	: m_socket(p_socket)
	, m_payload(payload)
	, m_msg_id(msg_id)
	, m_seq(0)
	, m_timestamp(0)
	, m_ssrc(0)
{

}

k_rtp_sender::~k_rtp_sender()
{
}

void k_rtp_sender::ff_rtp_send_data(uint8_t *buf1, int len, int m)
{
	uint8_t* p_rtp = buf1 - RTP_HEADER_LENGTH;
	uint32_t tot_len = len + RTP_HEADER_LENGTH;
	/* build the RTP header */
	k_util::avio_wb16(p_rtp, 0x1234);//magic
	k_util::avio_wb32(p_rtp, tot_len);
	k_util::avio_wb32(p_rtp, m_msg_id);

	k_util::avio_w8(p_rtp, RTP_VERSION << 6);
	k_util::avio_w8(p_rtp, (m_payload & 0x7f) | ((m & 0x01) << 7));
	k_util::avio_wb16(p_rtp, m_seq);
	k_util::avio_wb32(p_rtp, m_timestamp);
	k_util::avio_wb32(p_rtp, m_ssrc);

    m_seq = (m_seq + 1) & 0xffff;

	m_socket->k_send((char*)buf1 - RTP_HEADER_LENGTH, tot_len);
}

void k_rtp_sender::set_timestamp(uint32_t ts)
{
	m_timestamp = ts;
}

uint8_t* k_rtp_sender::get_payload_buf()
{
	return m_rtp_buf + RTP_HEADER_LENGTH;
}
