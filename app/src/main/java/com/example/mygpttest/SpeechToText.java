package com.example.mygpttest;
import com.google.cloud.speech.v1p1beta1.*;
import com.google.protobuf.ByteString;

public class SpeechToText {
    public static String transcribe(byte[] audioData) throws Exception {
        try (SpeechClient speechClient = SpeechClient.create()) {
            ByteString audioBytes = ByteString.copyFrom(audioData);

            RecognitionConfig config =
                    RecognitionConfig.newBuilder()
                            .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                            .setSampleRateHertz(16000)
                            .setLanguageCode("en-US")
                            .build();

            RecognitionAudio audio = RecognitionAudio.newBuilder().setContent(audioBytes).build();

            RecognizeResponse response = speechClient.recognize(config, audio);
            for (SpeechRecognitionResult result : response.getResultsList()) {
                // 変換されたテキストを取得
                return result.getAlternatives(0).getTranscript();
            }
        }
        return null;
    }
}
