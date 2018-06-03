package com.lisi.titan;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Process;
import android.support.annotation.Nullable;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static junit.framework.Assert.assertTrue;

public class TitanAudioRecord {
    private static final String TAG = "TitanAudioRecord";


    // Default audio data format is PCM 16 bit per sample.
    // Guaranteed to be supported by all devices.
    private static final int AUDIO_SAMPLE_RATE = 44100;

    private static final int BITS_PER_SAMPLE = 16;

    // Requested size of each recorded buffer provided to the client.
    private static final int CALLBACK_BUFFER_SIZE_MS = 10;

    // Average number of callbacks per second.
    private static final int BUFFERS_PER_SECOND = 1000 / CALLBACK_BUFFER_SIZE_MS;

    // We ask for a native buffer size of BUFFER_SIZE_FACTOR * (minimum required
    // buffer size). The extra space is allocated to guard against glitches under
    // high load.
    private static final int BUFFER_SIZE_FACTOR = 2;

    // The AudioRecordJavaThread is allowed to wait for successful call to join()
    // but the wait times out afther this amount of time.
    private static final long AUDIO_RECORD_THREAD_JOIN_TIMEOUT_MS = 2000;

    public static final int DEFAULT_AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_COMMUNICATION;

    private final TitanAudioEffects effects = new TitanAudioEffects();

    private final Context context;
    private final int audioSource;
    private final boolean isAcousticEchoCancelerSupported;
    private final boolean isNoiseSuppressorSupported;

    private @Nullable ByteBuffer byteBuffer;
    private volatile boolean microphoneMute = false;
    private byte[] emptyBytes;

    private @Nullable AudioRecord audioRecord = null;
    private @Nullable AudioRecordThread audioThread = null;
    private @Nullable TitanAudioEncoder audioEncoder = null;

    private final ByteOrder mByteOrder =
            ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN
                    ? ByteOrder.BIG_ENDIAN  : ByteOrder.LITTLE_ENDIAN;

    /**
     * Audio thread which keeps calling ByteBuffer.read() waiting for audio
     * to be recorded. Feeds recorded data to the native counterpart as a
     * periodic sequence of callbacks using DataIsRecorded().
     * This thread uses a Process.THREAD_PRIORITY_URGENT_AUDIO priority.
     */
    private class AudioRecordThread extends Thread {
        private volatile boolean keepAlive = true;

        public AudioRecordThread(String name) {
            super(name);
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            Log.e(TAG, "AudioRecordThread" );
            assertTrue(audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING);

            long lastTime = System.nanoTime();
            while (keepAlive) {
                int bytesRead = audioRecord.read(byteBuffer, byteBuffer.capacity());
                if (bytesRead == byteBuffer.capacity()) {
                    if (microphoneMute) {
                        byteBuffer.clear();
                        byteBuffer.put(emptyBytes);
                    }

                    byte[] data = Arrays.copyOf(byteBuffer.array(), byteBuffer.capacity());
//                    short [] shortData = new short[data.length / 2];
//
//                    ByteBuffer.wrap(data).order(mByteOrder).asShortBuffer().get(shortData);
//
//                    int i,j;
//
//                    for (i = 0; i < shortData.length; ++i){
//                        j = shortData[i];
//                        shortData[i] = (short)(j>>2);
//                    }
//
//                    ByteBuffer.wrap(data).order(mByteOrder).asShortBuffer().put(shortData);

                    audioEncoder.encode(data);

                } else {
                    String errorMessage = "AudioRecord.read failed: " + bytesRead;
                    Log.e(TAG, errorMessage);
                    if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                        keepAlive = false;
//                        reportWebRtcAudioRecordError(errorMessage);
                    }
                }
            }

            try {
                if (audioRecord != null) {
                    audioRecord.stop();
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "AudioRecord.stop failed: " + e.getMessage());
            }
        }

        // Stops the inner thread loop and also calls AudioRecord.stop().
        // Does not block the calling thread.
        public void stopThread() {
            Log.e(TAG, "stopThread");
            keepAlive = false;
        }
    }

    public TitanAudioRecord(Context context) {
        this.context = context;
        this.audioSource = DEFAULT_AUDIO_SOURCE;
        this.isAcousticEchoCancelerSupported = TitanAudioEffects.isAcousticEchoCancelerSupported();
        this.isNoiseSuppressorSupported = TitanAudioEffects.isAcousticEchoCancelerSupported();
    }

    boolean isAcousticEchoCancelerSupported() {
        return isAcousticEchoCancelerSupported;
    }

    boolean isNoiseSuppressorSupported() {
        return isNoiseSuppressorSupported;
    }

    public boolean enableBuiltInAEC(boolean enable) {
        Log.e(TAG, "enableBuiltInAEC(" + enable + ')');
        return effects.setAEC(enable);
    }

    public boolean enableBuiltInNS(boolean enable) {
        Log.e(TAG, "enableBuiltInNS(" + enable + ')');
        return effects.setNS(enable);
    }

    public int initRecording() {
        Log.e(TAG, "initRecording");
        if (audioRecord != null) {
            Log.e(TAG,"InitRecording called twice without StopRecording.");
            return -1;
        }

        if (isNoiseSuppressorSupported())
        {
            enableBuiltInNS(true);
        }

        if (isAcousticEchoCancelerSupported())
        {
            enableBuiltInAEC(true);
        }

        final int bytesPerFrame = BITS_PER_SAMPLE / 8;
        final int framesPerBuffer = AUDIO_SAMPLE_RATE / BUFFERS_PER_SECOND;
        byteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * framesPerBuffer);
        Log.e(TAG, "byteBuffer.capacity: " + byteBuffer.capacity());
        emptyBytes = new byte[byteBuffer.capacity()];
        // Rather than passing the ByteBuffer with every callback (requiring
        // the potentially expensive GetDirectBufferAddress) we simply have the
        // the native class cache the address to the memory once.
//        nativeCacheDirectBufferAddress(nativeAudioRecord, byteBuffer);

        // Get the minimum buffer size required for the successful creation of
        // an AudioRecord object, in byte units.
        // Note that this size doesn't guarantee a smooth recording under load.
        final int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int minBufferSize =
                AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE, channelConfig, AudioFormat.ENCODING_PCM_16BIT);
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "AudioRecord.getMinBufferSize failed: " + minBufferSize );
            return -1;
        }
        Log.e(TAG, "AudioRecord.getMinBufferSize: " + minBufferSize);

        // Use a larger buffer size than the minimum required when creating the
        // AudioRecord instance to ensure smooth recording under load. It has been
        // verified that it does not increase the actual recording latency.
        int bufferSizeInBytes = Math.max(BUFFER_SIZE_FACTOR * minBufferSize, byteBuffer.capacity());
        Log.e(TAG, "bufferSizeInBytes: " + bufferSizeInBytes);
        try {
            audioRecord = new AudioRecord(audioSource, AUDIO_SAMPLE_RATE, channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT, bufferSizeInBytes);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "AudioRecord ctor error: " + e.getMessage());
            releaseAudioResources();
            return -1;
        }
        if ( audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "Failed to create a new AudioRecord instance" );
            releaseAudioResources();
            return -1;
        }
        effects.enable(audioRecord.getAudioSessionId());
        logMainParameters();
        return framesPerBuffer;
    }

    public boolean startRecording() {
        Log.e(TAG, "startRecording");
        assertTrue(audioRecord != null);
        assertTrue(audioThread == null);
        assertTrue(audioEncoder == null);

        audioEncoder = new TitanAudioEncoder();
        if(-1 == audioEncoder.initEncode())
        {
            Log.e(TAG,"audioEncoder.initEncode failed: ");
            return false;
        }

        try {
            audioRecord.startRecording();
        } catch (IllegalStateException e) {
            Log.e(TAG,"AudioRecord.startRecording failed: " + e.getMessage());
            return false;
        }
        if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            Log.e(TAG,"AudioRecord.startRecording failed - incorrect state :"
                            + audioRecord.getRecordingState());
            return false;
        }
        audioThread = new AudioRecordThread("AudioRecordJavaThread");
        audioThread.start();
        return true;
    }

    public boolean stopRecording() {
        Log.e(TAG, "stopRecording");

        if (audioThread != null)
        {
            audioThread.stopThread();
            try {
                audioThread.join(AUDIO_RECORD_THREAD_JOIN_TIMEOUT_MS);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }

        if (audioEncoder != null)
        {
            audioEncoder.release();
        }

        audioEncoder = null;
        audioThread = null;
        effects.release();
        releaseAudioResources();
        return true;
    }

    // Sets all recorded samples to zero if |mute| is true, i.e., ensures that
    // the microphone is muted.
    public void setMicrophoneMute(boolean mute) {
        Log.w(TAG, "setMicrophoneMute(" + mute + ")");
        microphoneMute = mute;
    }

    // Releases the native AudioRecord resources.
    private void releaseAudioResources() {
        Log.e(TAG, "releaseAudioResources");
        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }
    }

    private void logMainParameters() {
        Log.e(TAG,
                "AudioRecord: "
                        + "session ID: " + audioRecord.getAudioSessionId() + ", "
                        + "channels: " + audioRecord.getChannelCount() + ", "
                        + "sample rate: " + audioRecord.getSampleRate());
    }

}
