package com.example.mygpttest;
/**
 * 音声認識テスト用アクティビティ_01
 * バッファサイズ分の音声を適宜認識。ストリーミングではないので、抜け落ちやラグがひどい。
 *
 */

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.core.app.ActivityCompat;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class VoiceTestActivity extends AppCompatActivity {

    private TextView resultTextView;
    private Button startButton;
    private Button stopButton;
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private BlockingQueue<byte[]> audioData = new LinkedBlockingQueue<>();
    private static final String TAG = "speech";

    private class TranscribeTask extends AsyncTask<Void, String, String> {

        @Override
        protected String doInBackground(Void... params) {
            // Google Cloud Speech-to-Text APIの初期化と認証
            try (InputStream credentialsStream = getResources().openRawResource(R.raw.iniadbessho_credentials)) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);
                SpeechSettings settings = SpeechSettings.newBuilder().setCredentialsProvider(() -> credentials).build();
                try (SpeechClient speechClient = SpeechClient.create(settings)) {
                    // リクエストの生成
                    RecognitionConfig config =
                            RecognitionConfig.newBuilder()
                                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                                    .setSampleRateHertz(16000)
                                    .setLanguageCode("ja-JP")
                                    .setModel("default")
                                    .build();
                    Log.i(TAG, "リクエストの生成完了");

                    while (isRecording) {
                        Log.d(TAG, "バックグラウンドプロセス");
                        byte[] audioBuffer = audioData.poll();
                        if (audioBuffer != null) {
                            Log.i(TAG, ByteString.copyFrom(audioBuffer).toString());
                            RecognitionAudio recognitionAudio = RecognitionAudio.newBuilder().setContent(ByteString.copyFrom(audioBuffer)).build();
                            RecognizeRequest request = RecognizeRequest.newBuilder().setConfig(config).setAudio(recognitionAudio).build();

                            Log.d(TAG, "認識完了");

                            // API呼び出しと結果の取得
                            RecognizeResponse response = speechClient.recognize(request);
                            Log.i(TAG, response.getResultsList().toString());
                            StringBuilder transcript = new StringBuilder();
                            for (SpeechRecognitionResult result : response.getResultsList()) {
                                transcript.append(result.getAlternatives(0).getTranscript());
                                Log.i(TAG, "inner for" + transcript.toString());
                            }
                            publishProgress(transcript.toString()); // 進捗を更新して結果をUIに表示
                            Log.i(TAG, "進捗更新完了");
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            // UIを更新する処理（認識結果を表示するなど）
            Log.i(TAG, "l: " + values.length);
            String result = values[0];
            if (result.equals("")) {
                Log.i(TAG, "RESULT:" + result);
                resultTextView.setText("Result is empty.");
            } else {
                Log.i(TAG, "RESULT:" + result);
                resultTextView.setText(result);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_test);

        resultTextView = findViewById(R.id.resultTextView_M2);
        startButton = findViewById(R.id.startButton_M2);
        stopButton = findViewById(R.id.stopButton_M2);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isRecording) {
                    Log.i(TAG, "すでに録音中です");
                } else {
                    Log.i(TAG, "START RECORDING");
                    // マイクのパーミッションを確認
                    if (ActivityCompat.checkSelfPermission(VoiceTestActivity.this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(VoiceTestActivity.this, new String[]{android.Manifest.permission.RECORD_AUDIO}, 1);
                        return;
                    }

                    Log.i(TAG, "マイクのパーミッション確認通過");

                    // マイクからの音声データを取得してキューに追加する処理
                    int bufferSize = 98000;//AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                    audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
                    audioRecord.startRecording();
                    isRecording = true;

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "Thread run");
                            while (isRecording) {
                                byte[] audioBuffer = new byte[bufferSize];
                                int bytesRead = audioRecord.read(audioBuffer, 0, bufferSize);
                                if (bytesRead > 0) {
                                    Log.d(TAG, "size of byte Read" + bytesRead);
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
                // マイクの録音を停止
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
