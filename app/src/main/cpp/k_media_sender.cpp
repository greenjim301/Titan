#include "k_media_sender.h"
#include <string.h>

k_media_sender::k_media_sender(k_socket* p_socket)
	: m_video_sender(p_socket, 96, 1000)
    , m_audio_sender(p_socket, 97, 1001)
{
}

const uint8_t * k_media_sender::ff_avc_find_startcode_internal(
	const uint8_t *p, const uint8_t *end)
{
	const uint8_t *a = p + 4 - ((intptr_t)p & 3);

	for (end -= 3; p < a && p < end; p++) {
		if (p[0] == 0 && p[1] == 0 && p[2] == 1)
			return p;
	}

	for (end -= 3; p < end; p += 4) {
		uint32_t x = *(const uint32_t*)p;
		//      if ((x - 0x01000100) & (~x) & 0x80008000) // little endian
		//      if ((x - 0x00010001) & (~x) & 0x00800080) // big endian
		if ((x - 0x01010101) & (~x) & 0x80808080) { // generic
			if (p[1] == 0) {
				if (p[0] == 0 && p[2] == 1)
					return p;
				if (p[2] == 0 && p[3] == 1)
					return p + 1;
			}
			if (p[3] == 0) {
				if (p[2] == 0 && p[4] == 1)
					return p + 2;
				if (p[4] == 0 && p[5] == 1)
					return p + 3;
			}
		}
	}

	for (end += 3; p < end; p++) {
		if (p[0] == 0 && p[1] == 0 && p[2] == 1)
			return p;
	}

	return end + 3;
}

const uint8_t * k_media_sender::ff_avc_find_startcode(const uint8_t *p, const uint8_t *end)
{
	const uint8_t *out = ff_avc_find_startcode_internal(p, end);
	if (p < out && out < end && !out[-1]) out--;
	return out;
}

void k_media_sender::ff_rtp_send_h264(const uint8_t *buf1, int size, uint32_t ts)
{
	const uint8_t *r, *end = buf1 + size;
	m_video_sender.set_timestamp(ts);
	
	r = ff_avc_find_startcode(buf1, end);

	while (r < end) {
		const uint8_t *r1;

		while (!*(r++));
		r1 = ff_avc_find_startcode(r, end);

		nal_send(r, r1 - r, r1 == end);
		r = r1;
	}
}

void k_media_sender::nal_send(const uint8_t *buf, int size, int last)
{
	uint8_t* p_buf = m_video_sender.get_payload_buf();

	if (size <= k_rtp_sender::MAX_PAYLOAD_SIZE)
	{	
		memcpy(p_buf, buf, size);
		m_video_sender.ff_rtp_send_data(p_buf, size, last);
	}
	else {
		int flag_byte, header_size;

		uint8_t type = buf[0] & 0x1F;
		uint8_t nri = buf[0] & 0x60;

		p_buf[0] = 28;        /* FU Indicator; Type = 28 ---> FU-A */
		p_buf[0] |= nri;
		p_buf[1] = type;
		p_buf[1] |= 1 << 7;
		buf += 1;
		size -= 1;

		flag_byte = 1;
		header_size = 2;

		while (size + header_size > k_rtp_sender::MAX_PAYLOAD_SIZE) {
			memcpy(&p_buf[header_size], buf, k_rtp_sender::MAX_PAYLOAD_SIZE - header_size);
			m_video_sender.ff_rtp_send_data(p_buf, k_rtp_sender::MAX_PAYLOAD_SIZE, 0);
			buf += k_rtp_sender::MAX_PAYLOAD_SIZE - header_size;
			size -= k_rtp_sender::MAX_PAYLOAD_SIZE - header_size;
			p_buf[flag_byte] &= ~(1 << 7);
		}
		p_buf[flag_byte] |= 1 << 6;
		memcpy(&p_buf[header_size], buf, size);
		m_video_sender.ff_rtp_send_data(p_buf, size + header_size, last);
	}
}

void k_media_sender::ff_rtp_send_audio(const uint8_t *buf1, int size, uint32_t ts) {
    m_audio_sender.set_timestamp(ts);

    uint8_t * buf = m_audio_sender.get_payload_buf();
    memcpy(buf, buf1, size);

    m_audio_sender.ff_rtp_send_data(buf, size, 1);
}

