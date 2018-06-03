#ifndef __K_UTIL_H__
#define __K_UTIL_H__

#include <android/log.h>

#define TAG "native-lib" // 这个是自定义的LOG的标识
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,TAG ,__VA_ARGS__) // 定义LOGE类型

#include <stdint.h>

class k_util
{
public:
	static int init();
	static void cleanup();

	static void avio_w8(uint8_t*& s, int b);
	static void avio_wb16(uint8_t*& s, unsigned int val);
	static void avio_wb32(uint8_t*& s, unsigned int val);
};

#endif