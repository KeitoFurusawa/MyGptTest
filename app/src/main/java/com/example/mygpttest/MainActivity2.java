package com.example.mygpttest;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.TextSwitcher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.api.gax.rpc.ApiStreamObserver;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity2 extends AppCompatActivity {
    private static final String TAG = "main2";
    private static final String[] PERMISSIONS = {Manifest.permission.RECORD_AUDIO};
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    private boolean permissionToRecord = false;
    private AudioEmitter audioEmitter;
    private TextSwitcher textView;
    private SpeechClient speechClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        // パーミッションのリクエスト
        ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_RECORD_AUDIO_PERMISSION);

        // UI要素の取得
        textView = findViewById(R.id.last_recognize_result);
        textView.setFactory(() -> {
            TextView t = new TextView(this);
            t.setText("Please say something!");
            t.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            t.setTextAppearance(android.R.style.TextAppearance_Large);
            return t;
        });
        textView.setInAnimation(getApplicationContext(), android.R.anim.fade_in);
        textView.setOutAnimation(getApplicationContext(), android.R.anim.fade_out);

        // SpeechClientの初期化
        try {
            speechClient = SpeechClient.create(SpeechSettings.newBuilder()
                    .setCredentialsProvider(() -> {
                        try {
                            return GoogleCredentials.fromStream(getResources().openRawResource(R.raw.iniadbessho_credentials));
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to obtain credentials.", e);
                        }
                    })
                    .build());
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize SpeechClient.", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // レコーディングを開始（パーミッションが許可されている場合）
        if (permissionToRecord) {
            AtomicBoolean isFirstRequest = new AtomicBoolean(true);
            audioEmitter = new AudioEmitter();

            // サーバーにデータをストリーミングし、応答を収集する
            // 正しい型の ApiStreamObserver を作成
            ApiStreamObserver<StreamingRecognizeResponse> responseObserver = new ApiStreamObserver<StreamingRecognizeResponse>() {
                @Override
                public void onNext(StreamingRecognizeResponse value) {
                    runOnUiThread(() -> {
                        if (value.getResultsCount() > 0) {
                            textView.setText(value.getResults(0).getAlternatives(0).getTranscript());
                        } else {
                            textView.setText("Sorry, there was a problem!");
                        }
                    });
                }

                @Override
                public void onError(Throwable t) {
                    Log.e(TAG, "An error occurred", t);
                }

                @Override
                public void onCompleted() {
                    Log.d(TAG, "Stream closed");
                }
            };
            // 正しい型の responseObserver を使用してbidiStreamingCallを呼び出す
            ApiStreamObserver<StreamingRecognizeRequest> requestStream = speechClient
                    .streamingRecognizeCallable()
                    .bidiStreamingCall(responseObserver);



            // 入力ストリームを監視し、音声データが利用可能になるとリクエストを送信
            audioEmitter.start(bytes -> {
                StreamingRecognizeRequest.Builder builder = StreamingRecognizeRequest.newBuilder()
                        .setAudioContent(ByteString.copyFrom(bytes));

                // 最初のリクエストの場合、設定を含める
                if (isFirstRequest.getAndSet(false)) {
                    builder.setStreamingConfig(StreamingRecognitionConfig.newBuilder()
                            .setConfig(RecognitionConfig.newBuilder()
                                    .setLanguageCode("en-US")
                                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                                    .setSampleRateHertz(16000)
                                    .build())
                            .setInterimResults(false)
                            .setSingleUtterance(false)
                            .build());
                }

                // 次のリクエストを送信
                requestStream.onNext(builder.build());
            });
        } else {
            Log.e(TAG, "No permission to record! Please allow and then relaunch the app!");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // マイクのデータを停止
        if (audioEmitter != null) {
            audioEmitter.stop();
            audioEmitter = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // クリーンアップ
        if (speechClient != null) {
            try {
                speechClient.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing SpeechClient", e);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            permissionToRecord = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        }

        // 音声録音が利用できない場合はアプリを終了
        if (!permissionToRecord) {
            finish();
        }
    }
}
