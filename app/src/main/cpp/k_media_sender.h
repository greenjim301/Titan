#ifndef __K_VIDEO_SENDER_H__
#define __K_VIDEO_SENDER_H__

#include <stdint.h>
#include "k_rtp_sender.h"

class k_media_sender
{
public:
	k_media_sender(k_socket* p_socket);

	void ff_rtp_send_h264(const uint8_t *buf1, int size, uint32_t ts);
    void ff_rtp_send_audio(const uint8_t *buf1, int size, uint32_t ts);

private:
	static const uint8_t *ff_avc_find_startcode(const uint8_t *p, const uint8_t *end);
	static const uint8_t *ff_avc_find_startcode_internal(const uint8_t *p, const uint8_t *end);

	void nal_send(const uint8_t *buf, int size, int last);

private:
	k_rtp_sender m_video_sender;
	k_rtp_sender m_audio_sender;
};

#endif // __K_VIDEO_SENDER_H__
