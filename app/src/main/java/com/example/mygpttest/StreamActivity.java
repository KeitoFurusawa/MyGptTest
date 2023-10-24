package com.example.mygpttest;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.grpc.InstantiatingGrpcChannelProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class StreamActivity extends AppCompatActivity {

    private Button startButton;
    private TextView resultTextView;
    private boolean isRecognizing = false;
    private AudioRecord audioRecord;
    private ByteArrayOutputStream byteArrayOutputStream;
    private SpeechClient speechClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stream);

        startButton = findViewById(R.id.StartButton);
        resultTextView = findViewById(R.id.resultStoT_TextView);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRecognizing) {
                    startRecognition();
                } else {
                    stopRecognition();
                }
            }
        });
    }

    private void startRecognition() {
        isRecognizing = true;
        startButton.setText("Stop Recognition");

        int minBufferSize = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize);
        byteArrayOutputStream = new ByteArrayOutputStream();

        audioRecord.startRecording();

        try {
            int resourceJSONId = R.raw.iniadbessho_credentials; // JSONファイルのリソースIDを取得
            InputStream inputStream = getResources().openRawResource(resourceJSONId); // リソースIDからInputStreamを取得
            GoogleCredentials credentials = GoogleCredentials.fromStream(inputStream); // GoogleCredentialsを初期化
            speechClient = com.google.cloud.speech.v1.SpeechClient.create(com.google.cloud.speech.v1.SpeechSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                    .setTransportChannelProvider(InstantiatingGrpcChannelProvider.newBuilder().build())
                    .build());
        } catch (IOException e) {
            e.printStackTrace();
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] targetData = new byte[3200]; // 200 ms of audio data

                while (isRecognizing) {
                    int numBytesRead = audioRecord.read(targetData, 0, targetData.length);
                    if (numBytesRead > 0) {
                        StreamingRecognizeRequest request = StreamingRecognizeRequest.newBuilder()
                                .setAudioContent(ByteString.copyFrom(targetData, 0, numBytesRead))
                                .build();
                        byteArrayOutputStream.write(targetData, 0, numBytesRead);
                        new StreamingRecognizeTask().execute(request);
                    }
                }

                audioRecord.stop();
                audioRecord.release();
            }
        }).start();
    }

    private void stopRecognition() {
        isRecognizing = false;
        startButton.setText("Start Recognition");

        try {
            if (speechClient != null) {
                speechClient.close();
            }

            if (byteArrayOutputStream != null) {
                byteArrayOutputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class StreamingRecognizeTask extends AsyncTask<StreamingRecognizeRequest, Void, List<StreamingRecognitionResult>> {
        @Override
        protected List<StreamingRecognitionResult> doInBackground(StreamingRecognizeRequest... params) {
            List<StreamingRecognitionResult> results = new ArrayList<>();
            try {
                StreamingRecognizeResponse response = speechClient.streamingRecognize(params[0]);
                for (StreamingRecognitionResult result : response.getResultsList()) {
                    results.add(result);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return results;
        }

        @Override
        protected void onPostExecute(List<StreamingRecognitionResult> results) {
            for (StreamingRecognitionResult result : results) {
                SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
                resultTextView.setText(alternative.getTranscript());
            }
        }
    }
}
