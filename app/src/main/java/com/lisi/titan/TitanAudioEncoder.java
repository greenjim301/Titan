package com.lisi.titan;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

public class TitanAudioEncoder {

    private static final String TAG = "TitanAudioEncoder";

    private final  String MIME_TYPE="audio/mp4a-latm";
    private final  int KEY_CHANNEL_COUNT=1;
    private final  int KEY_SAMPLE_RATE=44100;
    private final  int KEY_BIT_RATE=64000;
    private final  int KEY_AAC_PROFILE= MediaCodecInfo.CodecProfileLevel.AACObjectLC;
    private static final int DEQUEUE_OUTPUT_BUFFER_TIMEOUT_US = 100000;
    private static final int MAX_ENCODER_Q_SIZE = 10;

    private MediaCodec mMeidaCodec;
    @Nullable
    private Thread outputThread;
    private volatile boolean running = false;

    public int initEncode()
    {
        try {
            mMeidaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        } catch (IOException | IllegalArgumentException e) {
            Log.e(TAG, "Cannot create media encoder " + MIME_TYPE);
            return -1;
        }

        MediaFormat mediaFormat = MediaFormat.createAudioFormat(MIME_TYPE,
                KEY_SAMPLE_RATE, KEY_CHANNEL_COUNT);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, KEY_BIT_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,
                KEY_AAC_PROFILE);
        mMeidaCodec.configure(mediaFormat, null, null,
                MediaCodec.CONFIGURE_FLAG_ENCODE);
        mMeidaCodec.start();

        running = true;
        outputThread = createOutputThread();
        outputThread.start();

        return 0;
    }

    private Thread createOutputThread() {
        return new Thread() {
            @Override
            public void run() {
                while (running) {
                    deliverEncodedAudio();
                }
                releaseCodecOnOutputThread();
            }
        };
    }

    private void deliverEncodedAudio() {
        try {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int index = mMeidaCodec.dequeueOutputBuffer(info, DEQUEUE_OUTPUT_BUFFER_TIMEOUT_US);
            if (index < 0) {
                return;
            }

            ByteBuffer codecOutputBuffer = mMeidaCodec.getOutputBuffer(index);
            codecOutputBuffer.position(info.offset);
            codecOutputBuffer.limit(info.offset + info.size);

            int length = info.size + 4;
            byte[] outData = new byte[length];

            addAUHead(outData, info.size);
            codecOutputBuffer.get(outData,4,info.size);

            long timestamp = Math.round(System.currentTimeMillis() * 44.1d) ;

            TitanNativeLib.nativeSendMeidaData(outData, outData.length, timestamp, 602);

            mMeidaCodec.releaseOutputBuffer(index, false);
        } catch (IllegalStateException e) {
            Log.e(TAG, "deliverOutput failed", e);
        }
    }

    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2;  //AAC LC
        int freqIdx = 4;  //44.1KHz
        int chanCfg = 1;  //CPE
        packet[0] = (byte)0xFF;
        packet[1] = (byte)0xF9;
        packet[2] = (byte)(((profile-1)<<6) + (freqIdx<<2) +(chanCfg>>2));
        packet[3] = (byte)(((chanCfg&3)<<6) + (packetLen>>11));
        packet[4] = (byte)((packetLen&0x7FF) >> 3);
        packet[5] = (byte)(((packetLen&7)<<5) + 0x1F);
        packet[6] = (byte)0xFC;
    }

    private void addAUHead(byte[] packet, int packetLen) {
        packet[0] = (byte)0x0;
        packet[1] = (byte)0x10;
        packet[2] = (byte)((packetLen & 0x1FFF) >> 5);
        packet[3] = (byte)((packetLen & 0x1F) << 3);
    }

    private void releaseCodecOnOutputThread() {
        Log.e(TAG, "Releasing MediaCodec on output thread");
        try {
            mMeidaCodec.stop();
        } catch (Exception e) {
            Log.e(TAG, "Media encoder stop failed", e);
        }
        try {
            mMeidaCodec.release();
        } catch (Exception e) {
            Log.e(TAG, "Media encoder release failed", e);
            // Propagate exceptions caught during release back to the main thread.
        }
        Log.e(TAG, "Release on output thread done");
    }

    public int release() {

        final int returnValue;
        if (outputThread == null) {
            returnValue = 0;
        } else {
            // The outputThread actually stops and releases the codec once running is false.
            running = false;

            try {
                outputThread.join();
            }
            catch (InterruptedException e)
            {
                Log.e(TAG, "Media encoder release exception", e);
            }

            returnValue = 0;
        }

        mMeidaCodec = null;
        outputThread = null;

        return returnValue;
    }

    public int encode(byte[] data) {
        if (mMeidaCodec == null) {
            return -1;
        }

        final int returnValue = encodeByteBuffer(data);
        // Check if the queue was successful.

        return returnValue;
    }

    private int encodeByteBuffer(byte[] inBuffer)
    {
        int index;
        try {
            index = mMeidaCodec.dequeueInputBuffer(0 /* timeout */);
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
            buffer = mMeidaCodec.getInputBuffer(index);
        } catch (IllegalStateException e) {
            Log.e(TAG, "getInputBuffers failed", e);
            return -1;
        }

        long pts = (System.nanoTime() + 500) / 1000;
        buffer.clear();
        buffer.put(inBuffer);
        buffer.limit(inBuffer.length);

        try {
            mMeidaCodec.queueInputBuffer(
                    index, 0 /* offset */, inBuffer.length, pts, 0 /* flags */);
        } catch (IllegalStateException e) {
            Log.e(TAG, "queueInputBuffer failed", e);
            // IllegalStateException thrown when the codec is in the wrong state.
            return -1;
        }
        return 0;
    }
}
