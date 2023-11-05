package com.example.mygpttest;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.util.Log;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.grpc.InstantiatingGrpcChannelProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeoutException;


public class SpeechRecognitionTask extends AsyncTask<Void, Void, String> {
    private static final String TAG = "speech";
    private static final AtomicInteger taskIdCounter = new AtomicInteger(0);; //taskIdの初期値0

    private static final int SAMPLE_RATE = 16000;
    private AudioRecord audioRecord;
    private Context context;
    private int taskId;

    public SpeechRecognitionTask(Context context) {
        this.context = context;
        taskId = taskIdCounter.getAndIncrement();
    }

    @Override
    protected String doInBackground(Void... voids) {
        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize);
        byte[] audioData = new byte[minBufferSize];

        StringBuilder resultBuilder = new StringBuilder();
        try {
            int resourceJSONId = R.raw.iniadbessho_credentials;
            InputStream inputStream = context.getResources().openRawResource(resourceJSONId);
            GoogleCredentials credentials = GoogleCredentials.fromStream(inputStream);

            try (SpeechClient speechClient = SpeechClient.create(SpeechSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                    .setTransportChannelProvider(InstantiatingGrpcChannelProvider.newBuilder().build())
                    .build())) {
                audioRecord.startRecording();

                while (!isCancelled()) {
                    Log.d(TAG, "inner while");
                    int numBytesRead = audioRecord.read(audioData, 0, audioData.length);
                    if (numBytesRead > 0) {
                        Log.d(TAG, "numBytesRead > 0");
                        RecognitionConfig config = RecognitionConfig.newBuilder()
                                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                                .setSampleRateHertz(SAMPLE_RATE)
                                .setLanguageCode("en-us")
                                .build();
                        Log.d(TAG, "after config");
                        RecognitionAudio audio = RecognitionAudio.newBuilder()
                                .setContent(ByteString.copyFrom(audioData, 0, numBytesRead))
                                .build();
                        Log.d(TAG, "after audio");
                        try {
                            RecognizeResponse response = speechClient.recognize(config, audio);
                            Log.d(TAG, "response: " + response.toString()); //ここまでは出力確認
                            List<SpeechRecognitionResult> results = response.getResultsList(); //ここから処理がバグってる可能性がある
                            Log.d(TAG, "result: " + results.toString());
                            for (SpeechRecognitionResult recognitionResult : results) {
                                Log.d(TAG, "inner of for recognitionResult");
                                List<SpeechRecognitionAlternative> alternatives = recognitionResult.getAlternativesList();
                                for (SpeechRecognitionAlternative alternative : alternatives) {
                                    Log.d(TAG, "inner of for alternative");
                                    resultBuilder.append(alternative.getTranscript()).append("\n");
                                    //コールバックを作って返す　ー＞　ハンドラーに渡して出力
                                }
                            }
                            Log.d(TAG, resultBuilder.toString());
                            // APIの応答を処理するコード
                        } catch (Exception e) {
                            // その他の例外の場合の処理
                            Log.e(TAG, e.getMessage());
                        }
                    } else {
                        Log.d(TAG, "NO DATA TO READ.");
                    }
                }

                return resultBuilder.toString();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        if (audioRecord != null) {
            Log.d(TAG, "stop and release");
            audioRecord.stop();
            audioRecord.release();
        }
    }

    private SpeechClient createSpeechClient() throws IOException {
        // JSONファイルのリソースIDを取得
        int resourceJSONId = R.raw.iniadbessho_credentials;

        // リソースIDからInputStreamを取得
        InputStream inputStream = context.getResources().openRawResource(resourceJSONId);

        // GoogleCredentialsを初期化
        GoogleCredentials credentials = GoogleCredentials.fromStream(inputStream);

        // SpeechClientを初期化して返す
        return SpeechClient.create(SpeechSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .setTransportChannelProvider(InstantiatingGrpcChannelProvider.newBuilder().build())
                .build());
    }

    public int getTaskId() { //taskIdのgetter
        return this.taskId;
    }
}


