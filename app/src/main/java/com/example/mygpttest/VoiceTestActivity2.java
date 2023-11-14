/**
 * 音声認識テスト用アクティビティ_02
 * ストリーミングを実装中
 *
 */
package com.example.mygpttest;

import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class VoiceTestActivity2 extends AppCompatActivity {

    private TextView resultTextView;
    private Button startButton;
    private Button stopButton;
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private BlockingQueue<byte[]> audioData = new LinkedBlockingQueue<>();
    private static final String TAG = "speech";

    private class TranscribeTask extends AsyncTask<Void, String, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try (InputStream credentialsStream = getResources().openRawResource(R.raw.iniadbessho_credentials)) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);
                SpeechSettings settings = SpeechSettings.newBuilder().setCredentialsProvider(() -> credentials).build();

                try (SpeechClient speechClient = SpeechClient.create(settings)) {
                    RecognitionConfig config =
                            RecognitionConfig.newBuilder()
                                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                                    .setSampleRateHertz(16000)
                                    .setLanguageCode("en-US")
                                    .setModel("default")
                                    .setUseEnhanced(true) // ストリーミング認識用に追加
                                    .build();

                    // ストリーミング認識の開始
                    ClientStream<StreamingRecognizeRequest> clientStream =
                            speechClient.streamingRecognizeCallable().splitCall(new ResponseObserver<StreamingRecognizeResponse>() {
                                @Override
                                public void onStart(StreamController controller) {}

                                @Override
                                public void onResponse(StreamingRecognizeResponse response) {
                                    for (StreamingRecognitionResult result : response.getResultsList()) {
                                        Log.i(TAG, result.toString()); //[debug] レスポンスメッセージを適宜表示
                                        SpeechRecognitionAlternative alternative = result.getAlternatives(0);
                                        final String transcript = alternative.getTranscript();
                                        publishProgress(transcript); // 認識結果をUIに表示
                                    }
                                }

                                @Override
                                public void onError(Throwable t) {
                                    Log.e(TAG, "Error in streaming recognition: " + t.getMessage());
                                }

                                @Override
                                public void onComplete() {
                                    Log.i(TAG, "Streaming recognition completed.");
                                }
                            });

                    // 構成情報を送信する。
                    StreamingRecognitionConfig configRequest = StreamingRecognitionConfig.newBuilder()
                            .setConfig(config)
                            .build();
                    clientStream.send(StreamingRecognizeRequest.newBuilder()
                            .setStreamingConfig(configRequest)
                            .build());

                    // マイクからの音声データをストリーミング認識に送信
                    while (isRecording) {
                        byte[] audioBuffer = audioData.poll();
                        if (audioBuffer != null) {
                            StreamingRecognizeRequest request =
                                    StreamingRecognizeRequest.newBuilder()
                                            .setAudioContent(ByteString.copyFrom(audioBuffer))
                                            .build();
                            clientStream.send(request);
                        }
                    }

                    // 認識が終了したら、クリーンアップ
                    clientStream.closeSend();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error reading credentials: " + e.getMessage());
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            String result = values[0];
            if (result.equals("")) {
                resultTextView.setText("Result is empty.");
            } else {
                resultTextView.setText(result);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_test2);

        resultTextView = findViewById(R.id.resultTextView_VT2);
        startButton = findViewById(R.id.startButton_VT2);
        stopButton = findViewById(R.id.stopButton_VT2);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isRecording) {
                    Log.i(TAG, "すでに録音中です");
                } else {
                    Log.i(TAG, "START RECORDING");
                    if (ActivityCompat.checkSelfPermission(VoiceTestActivity2.this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(VoiceTestActivity2.this, new String[]{android.Manifest.permission.RECORD_AUDIO}, 1);
                        return;
                    }

                    int bufferSize = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                    audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
                    audioRecord.startRecording();
                    isRecording = true;

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            while (isRecording) {
                                byte[] audioBuffer = new byte[bufferSize];
                                int bytesRead = audioRecord.read(audioBuffer, 0, bufferSize);
                                if (bytesRead > 0) {
                                    audioData.offer(audioBuffer);
                                }
                            }
                        }
                    }).start();

                    new TranscribeTask().execute();
                }

            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i(TAG, "STOP RECORDING");
                isRecording = false;
                if (audioRecord != null) {
                    audioRecord.stop();
                    audioRecord.release();
                    audioRecord = null;
                }
            }
        });
    }
}
