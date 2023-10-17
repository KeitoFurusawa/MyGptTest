package com.example.mygpttest;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.google.protobuf.ByteString;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
/**
 * Emits microphone audio using a background ScheduledExecutorService.
 */
public class AudioEmitter {
    private static final String TAG = "audio";

    private AudioRecord mAudioRecorder;
    private ScheduledExecutorService mAudioExecutor;
    private byte[] mBuffer;

    /** Start streaming  */
    public void start(int encoding, int channel, int sampleRate, AudioSubscriber subscriber) {
        mAudioExecutor = Executors.newSingleThreadScheduledExecutor();

        // create and configure recorder
        // Note: ensure settings are match the speech recognition config
        mAudioRecorder = new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channel)
                        .build())
                .build();
        mBuffer = new byte[2 * AudioRecord.getMinBufferSize(sampleRate, channel, encoding)];

        // start!
        Log.d(TAG, "Recording audio with buffer size of: " + mBuffer.length + " bytes");
        mAudioRecorder.startRecording();

        // stream bytes as they become available in chunks equal to the buffer size
        mAudioExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                // read audio data
                int read = mAudioRecorder.read(mBuffer, 0, mBuffer.length, AudioRecord.READ_BLOCKING);

                // send next chunk
                if (read > 0) {
                    subscriber.onDataReceived(ByteString.copyFrom(mBuffer, 0, read));
                }
            }
        }, 0, 10, TimeUnit.MILLISECONDS);
    }

    /** Stop Streaming  */
    public void stop() {
        // stop events
        if (mAudioExecutor != null) {
            mAudioExecutor.shutdown();
            mAudioExecutor = null;
        }

        // stop recording
        if (mAudioRecorder != null) {
            mAudioRecorder.stop();
            mAudioRecorder.release();
            mAudioRecorder = null;
        }
    }

    // Interface for subscriber
    public interface AudioSubscriber {
        void onDataReceived(ByteString data);
    }
}
