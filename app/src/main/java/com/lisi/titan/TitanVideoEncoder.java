package com.lisi.titan;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

public class TitanVideoEncoder {
    private static final String TAG = "TitanVideoEncoder";

    // Bitrate modes - should be in sync with OMX_VIDEO_CONTROLRATETYPE defined
    // in OMX_Video.h
    private static final int VIDEO_ControlRateConstant = 2;
    // Key associated with the bitrate control mode value (above). Not present as a MediaFormat
    // constant until API level 21.
    private static final int VIDEO_AVC_PROFILE_HIGH = 8;
    private static final int VIDEO_AVC_LEVEL_3 = 0x100;

    // See MAX_ENCODER_Q_SIZE in androidmediaencoder.cc.
    private static final int MAX_ENCODER_Q_SIZE = 2;
    private static final int DEQUEUE_OUTPUT_BUFFER_TIMEOUT_US = 100000;
    private static final int KEY_I_FRAME_INTERVAL = 2;

    private int mWidth;
    private int mHeight;
    private int mFramerate;
    private int mBitrate;

    @Nullable private MediaCodec codec;
    @Nullable private Thread outputThread;
    private volatile boolean running = false;
    private final BlockingDeque<byte[]> outputBuilders = new LinkedBlockingDeque<>();
    @Nullable private ByteBuffer configBuffer = null;

    public int initEncode(int width, int height, int framerate, int bps) {
        this.mWidth = width;
        this.mHeight = height;
        this.mFramerate = framerate;
        this.mBitrate = bps;

        Log.e(TAG,
                "initEncode: " + width + " x " + height + ". @ " + bps
                        + "bps. Fps: " + framerate);

        return initEncodeInternal();
    }

    private int initEncodeInternal() {
        try {
            codec = MediaCodec.createEncoderByType("video/avc");
        } catch (IOException | IllegalArgumentException e) {
            Log.e(TAG, "Cannot create media encoder ");
            return -1;
        }

        try {
            Log.e(TAG, "init codec " + mWidth + "x" + mHeight);

            MediaFormat format = MediaFormat.createVideoFormat("video/avc", mWidth, mHeight);
            format.setInteger(MediaFormat.KEY_BIT_RATE, mBitrate);
            format.setInteger(MediaFormat.KEY_BITRATE_MODE, VIDEO_ControlRateConstant);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, mFramerate);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, KEY_I_FRAME_INTERVAL);

            format.setInteger(MediaFormat.KEY_PROFILE, VIDEO_AVC_PROFILE_HIGH);
            format.setInteger(MediaFormat.KEY_LEVEL, VIDEO_AVC_LEVEL_3);

            Log.e(TAG, "Format: " + format);
            codec.configure(
                    format, null /* surface */, null /* crypto */, MediaCodec.CONFIGURE_FLAG_ENCODE);

            codec.start();
        } catch (IllegalStateException e) {
            Log.e(TAG, "initEncodeInternal failed", e);
            release();
            return -1;
        }

        running = true;
        outputThread = createOutputThread();
        outputThread.start();

        return 0;
    }

    public int release() {
        final int returnValue;
        if (outputThread == null) {
            Log.e(TAG, "output thread null");
            returnValue = 0;
        } else {
            // The outputThread actually stops and releases the codec once running is false.
            running = false;
            try {
                outputThread.join();
            }
            catch (InterruptedException e){
                Log.e(TAG, "Media encoder release exception" + e);
            }

            Log.e(TAG, "output thread release");
            returnValue = 0;
        }

        outputBuilders.clear();
        codec = null;
        outputThread = null;

        return returnValue;
    }

    private Thread createOutputThread() {
        return new Thread() {
            @Override
            public void run() {
                while (running) {
                    deliverEncodedImage();
                }
                releaseCodecOnOutputThread();
            }
        };
    }

    private void deliverEncodedImage() {
        try {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int index = codec.dequeueOutputBuffer(info, DEQUEUE_OUTPUT_BUFFER_TIMEOUT_US);
            if (index < 0) {
                return;
            }

            ByteBuffer codecOutputBuffer = codec.getOutputBuffer(index);
            codecOutputBuffer.position(info.offset);
            codecOutputBuffer.limit(info.offset + info.size);

            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                //Log.d(TAG, "Config frame generated. Offset: " + info.offset + ". Size: " + info.size);
                configBuffer = ByteBuffer.allocateDirect(info.size);
                configBuffer.put(codecOutputBuffer);
            } else {

                final boolean isKeyFrame = (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
                if (isKeyFrame) {
                    //Log.d(TAG, "Sync frame generated");
                }

                final ByteBuffer frameBuffer;
                if (isKeyFrame) {
//                    Log.d(TAG,
//                            "Prepending config frame of size " + configBuffer.capacity()
//                                    + " to output buffer with offset " + info.offset + ", size " + info.size);
                    // For H.264 key frame prepend SPS and PPS NALs at the start.
                    frameBuffer = ByteBuffer.allocateDirect(info.size + configBuffer.capacity());
                    configBuffer.rewind();
                    frameBuffer.put(configBuffer);
                    frameBuffer.put(codecOutputBuffer);
                    frameBuffer.rewind();
                } else {
                    frameBuffer = codecOutputBuffer.slice();
                }

                byte[] outData = new byte[frameBuffer.remaining()];
                frameBuffer.get(outData);

                long timestamp = Math.round( System.currentTimeMillis() * 90.d);
                TitanNativeLib.nativeSendMeidaData(outData, outData.length, timestamp, 601);
                outputBuilders.poll();
            }
            codec.releaseOutputBuffer(index, false);
        } catch (IllegalStateException e) {
            Log.e(TAG, "deliverOutput failed", e);
        }
    }

    private void releaseCodecOnOutputThread() {
        Log.e(TAG, "Releasing MediaCodec on output thread");
        try {
            codec.stop();
        } catch (Exception e) {
            Log.e(TAG, "Media encoder stop failed", e);
        }
        try {
            codec.release();
        } catch (Exception e) {
            Log.e(TAG, "Media encoder release failed", e);
            // Propagate exceptions caught during release back to the main thread.
        }
        configBuffer = null;
        Log.e(TAG, "Release on output thread done");
    }

    public int encode(byte[] videoFrame, int width, int height) {
        if (codec == null || outputThread == null) {
            return -1;
        }

        // If input resolution changed, restart the codec with the new resolution.
        final int frameWidth = width;
        final int frameHeight = height;
        if (frameWidth != mWidth || frameHeight != mHeight ) {
            Log.e(TAG, "reset codec " + frameWidth + "x" + frameHeight);
            int status = resetCodec(frameWidth, frameHeight);
            if (status != 0) {
                return status;
            }
        }

        if (outputBuilders.size() > MAX_ENCODER_Q_SIZE) {
            // Too many frames in the encoder.  Drop this frame.
            Log.e(TAG, "Dropped frame, encoder queue full");
            return -1; // See webrtc bug 2887.
        }

        outputBuilders.offer(new byte[1]);

        final int returnValue = encodeByteBuffer(videoFrame);

        // Check if the queue was successful.
        if (returnValue != 0) {
            // Keep the output builders in sync with buffers in the codec.
            outputBuilders.pollLast();
        }

        return returnValue;
    }

    private int resetCodec(int newWidth, int newHeight) {
        int status = release();
        if (status != 0) {
            return status;
        }
        mWidth = newWidth;
        mHeight = newHeight;
        return initEncodeInternal();
    }

    private int encodeByteBuffer(
            byte[] videoFrame) {
        // Frame timestamp rounded to the nearest microsecond.
        long presentationTimestampUs = (System.nanoTime() + 500) / 1000;

        // No timeout.  Don't block for an input buffer, drop frames if the encoder falls behind.
        int index;
        try {
            index = codec.dequeueInputBuffer(0 /* timeout */);
        } catch (IllegalStateException e) {
            Log.e(TAG, "dequeueInputBuffer failed", e);
            return -1;
        }

        if (index == -1) {
            // Encoder is falling behind.  No input buffers available.  Drop the frame.
            Log.d(TAG, "Dropped frame, no input buffers available");
            return -1; // See webrtc bug 2887.
        }

        ByteBuffer buffer;
        try {
            buffer = codec.getInputBuffer(index);
        } catch (IllegalStateException e) {
            Log.e(TAG, "getInputBuffers failed", e);
            return -1;
        }

        buffer.clear();
        buffer.put(videoFrame);

        try {
            codec.queueInputBuffer(
                    index, 0 /* offset */, videoFrame.length, presentationTimestampUs, 0 /* flags */);
        } catch (IllegalStateException e) {
            Log.e(TAG, "queueInputBuffer failed", e);
            // IllegalStateException thrown when the codec is in the wrong state.
            return -1;
        }
        return 0;
    }

}
