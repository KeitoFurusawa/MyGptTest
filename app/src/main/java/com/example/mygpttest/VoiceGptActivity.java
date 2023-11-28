/**
 * 音声対話GPT実装用Activity
 * ストリーミング音声を認識しながら，GPTに認識させる
 *
 */

package com.example.mygpttest;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
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

public class VoiceGptActivity extends AppCompatActivity {
    private static final String TAG = "speech";
    private String MY_API_KEY;
    private String SPECIAL_API_KEY;
    private static final String API_BASE = "https://api.openai.iniad.org/api/v1/chat/completions";
    private static final String ORIGINAL_API_BASE = "https://api.openai.com/v1/chat/completions";
    protected static JSONArray chatHistory = new JSONArray();
    protected TextView textViewRes;
    protected TextView textViewReq;
    private Button buttonGoToHome;
    private static StringBuilder message = new StringBuilder("");
    private Button startButton;
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private boolean isSent = false;
    private boolean isTranscribing = false;
    private BlockingQueue<byte[]> audioData = new LinkedBlockingQueue<>();
    private static String prompt;
    private static int colorGreen = Color.parseColor("#FF009688");
    private static int colorRed = Color.parseColor("#FFF44336");


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_gpt);

        // UI要素を取得
        buttonGoToHome = findViewById(R.id.buttonGoToHome_VG);
        startButton = findViewById(R.id.startButton_VG);
        textViewRes = findViewById(R.id.textViewRes_VG);
        textViewReq = findViewById(R.id.textViewReq_VG);

        // 文字列リソースで静的な変数を初期化
        prompt = getString(R.string.prompt2);

        // UI要素の初期設定
        textViewRes.setMovementMethod(new ScrollingMovementMethod());
        textViewReq.setMovementMethod(new ScrollingMovementMethod());
        MY_API_KEY = getString(R.string.my_key);
        SPECIAL_API_KEY = getString(R.string.special_key);

        //初期設定を行う
        setup();
        
        // ホーム画面遷移ボタンのクリックリスナーを設定
        buttonGoToHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 新しいアクティビティに遷移するためのIntentを作成
                Intent intent = new Intent(VoiceGptActivity.this, MainActivity.class);
                startActivity(intent); // 新しいアクティビティを起動
            }
        });

        // 音声認識_長押しリスナー
        startButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isSent = false;
                        if (isRecording) {
                            Log.i(TAG, "すでに録音中です");
                        } else {
                            Log.i(TAG, "START RECORDING");
                            startButton.setBackgroundTintList(ColorStateList.valueOf(colorRed));
                            startButton.setText("RECORDING");
                            if (ActivityCompat.checkSelfPermission(VoiceGptActivity.this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                ActivityCompat.requestPermissions(VoiceGptActivity.this, new String[]{android.Manifest.permission.RECORD_AUDIO}, 1);
                                return true;
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

                            new VoiceGptActivity.TranscribeTask().execute();
                        }

                        return true;
                    case MotionEvent.ACTION_UP:
                        Log.i(TAG, "STOP RECORDING");
                        isRecording = false;
                        if (audioRecord != null) {
                            audioRecord.stop();
                            audioRecord.release();
                            audioRecord = null;
                        }
                        startButton.setBackgroundTintList(ColorStateList.valueOf(colorGreen));
                        startButton.setText("START");

                        if (!isTranscribing && !isSent) {
                            Log.i(TAG, "[on MotionEvent]-メッセージを送信:" + message);
                            new SendMessageTask().execute(message.toString()); //これを実行してメッセージ送信
                            message.setLength(0);
                            isSent = true;
                        }
                        return true;
                }
                return false;
            }
        });

        /* 音声認識開始ボタンのクリックリスナーを設定
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
            }
        });
        */
        /* 音声認識終了ボタンのクリックリスナーを設定
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

            }
        });
        */
    }

    /* GPTにメッセージを送信する非同期タスク */
    private class SendMessageTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            //バックグラウンドで実行される処理
            return send_message(params[0]);
        }

        @Override
        protected void onPostExecute(String response) {
            //タスクが完了した後に実行される処理
            // レスポンスをTextViewに表示
            textViewRes.setText("GPT: " + response);
            Log.d(TAG, "onPostExecute: " + response);
        }
    }

    /* ストリーム音声認識を行う非同期タスク */
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
                                public void onStart(StreamController controller) {isTranscribing = true;}

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
            message.append(values[0]);
            if (values[0].equals("")) {
                textViewReq.setText("No input");
            } else {
                Log.i(TAG, "SET TEXT: " + message);
                textViewReq.setText(message);
                isTranscribing = false;
            }
            if (!isRecording && !isSent) {
                Log.i(TAG, "[on Progress Update]-メッセージを送信:" + message);
                new SendMessageTask().execute(message.toString()); //これを実行してメッセージ送信
                message.setLength(0);
                isSent = true;
            }
        }

        @Override
        protected void onPostExecute(Void result) {

        }
    }


    /* GPTのセットアップ */
    private static void setup() {
        chatHistory = new JSONArray();
        JSONObject systemMessage = new JSONObject();
        try {
            systemMessage.put("role", "system");
            systemMessage.put("content", prompt);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        chatHistory.put(systemMessage);
        Log.i(TAG, chatHistory.toString());
    }

    /* メッセージ送信用関数 */
    protected String send_message(String message) {
        try {
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", message);
            Log.i(TAG, "This is message: " + message);
            chatHistory.put(userMessage);

            JSONObject requestBody = new JSONObject();
            requestBody.put("model", "gpt-3.5-turbo-1106");
            requestBody.put("messages", chatHistory);
            requestBody.put("temperature", 0.7);
            //requestBody.put("max_tokens", 1000);

            /* タイムアウトの設定 */
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();

            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(JSON, requestBody.toString());
            //GPTリクエストヘッダ
            Request request = new Request.Builder()
                    .url(ORIGINAL_API_BASE)
                    .post(body)
                    .addHeader("Authorization", "Bearer " + SPECIAL_API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();

            Response response = client.newCall(request).execute();
            Log.d(TAG, "SUCCESS: getting response");
            if (response.isSuccessful()) {
                Log.d(TAG, response.toString());
                String responseBody = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseBody);
                JSONArray choices = jsonResponse.getJSONArray("choices");
                String reply = choices.getJSONObject(0).getJSONObject("message").getString("content");

                JSONObject assistantMessage = new JSONObject();
                assistantMessage.put("role", "assistant");
                assistantMessage.put("content", reply);
                chatHistory.put(assistantMessage);

                return reply;
            } else {
                Log.d(TAG, "Failed on API Request.");
                return "APIリクエストが失敗しました: " + response.code() + " " + response.message();
            }
        } catch (JSONException e) {
            Log.d(TAG, "catch JsonException: " + e.getMessage());
            e.printStackTrace();
            return "エラー: " + e.getMessage();
        } catch (IOException e) {
            Log.d(TAG, "catch IOException: " + e.getMessage());
            e.printStackTrace();
            return "エラー: " + e.getMessage();
        }
    }

}