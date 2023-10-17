package com.example.mygpttest;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.grpc.InstantiatingGrpcChannelProvider;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.speech.v1p1beta1.*;
import com.google.protobuf.ByteString;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import android.Manifest;
import com.google.auth.oauth2.GoogleCredentials;

import io.grpc.StatusRuntimeException;

public class SpeechToText extends AppCompatActivity {
    private static final String TAG = "SpeechToTextActivity";
    private String project_id = "corded-academy-278508";
    private SpeechClient speechClient;
    private boolean isRecording = false;
    private static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 1;
    private TextView textViewStatus;
    private TextView resultTextView;
    private Button startButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speech_test);
        Log.i(TAG, "This is Log");
        textViewStatus = findViewById(R.id.textViewStatus);
        resultTextView = findViewById(R.id.resultStoT_TextView);
        startButton = findViewById(R.id.StartButton);
        // リソースIDからInputStreamを取得


        // SpeechClientを初期化
        try {
            int resourceJSONId = R.raw.iniadbessho_credentials; // JSONファイルのリソースIDを取得
            InputStream inputStream = getResources().openRawResource(resourceJSONId); // リソースIDからInputStreamを取得
            GoogleCredentials credentials = GoogleCredentials.fromStream(inputStream); // GoogleCredentialsを初期化
            speechClient = SpeechClient.create(SpeechSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                    .setTransportChannelProvider(InstantiatingGrpcChannelProvider.newBuilder().build())
                    .build());
        } catch (IOException e) {
            Log.i(TAG, "Catch IOException: " + e.getMessage());
            e.printStackTrace();
        } catch (IllegalStateException e) {
            Log.i(TAG, "Catch IllegalStateException: " + e.getMessage());
            e.printStackTrace();
        } catch (ExceptionInInitializerError e) {
            Log.i(TAG, "Catch ExceptionInInitializerError: " + e.getMessage());
            e.printStackTrace();
        }

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isRecording) {
                    // マイクからの音声認識を開始
                    if (speechClient != null) {
                        textViewStatus.setText("音声認識アクティブ");
                        new SpeechRecognitionTask().execute();
                    } else {
                        Log.i(TAG, "SpeechClient is null");
                    }
                } else {
                    // 音声認識を停止
                    isRecording = false;
                    textViewStatus.setText("音声認識パッシブ");
                }
            }
        });
    }

    private class SpeechRecognitionTask extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... voids) {
            return startSpeechRecognition();
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                resultTextView.setText("認識結果: " + result);
            } else {
                resultTextView.setText("音声認識に失敗しました。");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // ユーザーがマイクのパーミッションを許可した場合、音声認識を開始する
                startSpeechRecognition();
            } else {
                // ユーザーがマイクのパーミッションを拒否した場合、適切なエラーメッセージを表示するなどの処理を行う
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // アクティビティが破棄される際にSpeechClientをクローズ
        if (speechClient != null && !speechClient.isShutdown()) {
            speechClient.shutdown();
        }
    }

    public String startSpeechRecognition() {
        // マイクのパーミッションが許可されているか確認
        if (ContextCompat.checkSelfPermission(SpeechToText.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // マイクのパーミッションが許可されていない場合、ユーザーに許可を求めるダイアログを表示
            ActivityCompat.requestPermissions(SpeechToText.this, new String[]{Manifest.permission.RECORD_AUDIO}, MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
            return null;
        } else {
            int bufferSize = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            // マイクのパーミッションがすでに許可されている場合、音声認識を開始する
            AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            try {
                isRecording = true;
                audioRecord.startRecording();
                while (isRecording) {
                    Log.i(TAG, "This is inner of while loop");
                    byte[] audioBuffer = new byte[bufferSize];
                    int result = audioRecord.read(audioBuffer, 0, bufferSize);
                    if (result > 0) {
                        // オーディオデータをByteStringに変換
                        ByteString audioData = ByteString.copyFrom(audioBuffer, 0, result);
                        Log.i(TAG, "AudioData size: " + audioData.size());
                        StringBuilder transcriptBuilder = new StringBuilder();
                        try {
                            // Speech-to-Text APIにリクエストを送信
                            Log.i(TAG, "inner try" + audioData.size());
                            RecognitionConfig config = RecognitionConfig.newBuilder()
                                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                                    .setSampleRateHertz(16000)
                                    .setLanguageCode("en-US")
                                    .build();
                            Log.i("before rec audio = " ,TAG);
                            RecognitionAudio audio = RecognitionAudio.newBuilder()
                                    .setContent(audioData)
                                    .build();
                            Log.i("before res = " ,TAG);
                            RecognizeResponse response = speechClient.recognize(config, audio);
                            Log.i("res = " ,TAG);

                            // 認識結果を取得
                            List<SpeechRecognitionResult> results = response.getResultsList();

                            for (SpeechRecognitionResult result_s : results) {
                                Log.i("inner for = " ,TAG);
                                transcriptBuilder.append(result_s.getAlternatives(0).getTranscript());
                            }
                        } catch (StatusRuntimeException e) {
                            Log.i(TAG, "Catch StatusRuntimeException: " + e.getMessage());
                        } catch (ApiException e) {
                            Log.i(TAG, "Catch ApiException: " + e.getMessage());
                        } catch (Exception e) {
                            Log.i(TAG, "Catch some Exception: " + e.getMessage());
                        } finally {
                          return transcriptBuilder.toString();
                        }
                    }
                }
            } finally {
                /*
                audioRecord.stop();
                audioRecord.release();

                 */
            }
        }
        return null;
    }
}
