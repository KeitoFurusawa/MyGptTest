/**
 * 音声対話GPT実装用Activity
 * ストリーミング音声を認識しながら，GPTに認識させる
 *
 */

package com.example.mygpttest;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
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
    private static final String API_KEY = "8UCHXpBd7Q2Lw86gWWAFCY6XGBO8JiGUVplGo6AJwsIHK3lIQDqS7HlDMCIs9q4fI0Mf4hvWqiYmEopDxTyVBBw";
    private static final String API_BASE = "https://api.openai.iniad.org/api/v1/chat/completions";
    protected static JSONArray chatHistory = new JSONArray();
    protected TextView textViewRes;
    protected TextView textViewReq;
    private Button buttonGoToHome;
    private static StringBuilder message = new StringBuilder("");
    private Button startButton;
    private Button stopButton;
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private BlockingQueue<byte[]> audioData = new LinkedBlockingQueue<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_gpt);

        // UI要素を取得
        buttonGoToHome = findViewById(R.id.buttonGoToHome_VG);
        startButton = findViewById(R.id.startButton_VG);
        stopButton = findViewById(R.id.stopButton_VG);
        textViewRes = findViewById(R.id.textViewRes_VG);
        textViewReq = findViewById(R.id.textViewReq_VG);

        // UI要素の初期設定
        textViewRes.setMovementMethod(new ScrollingMovementMethod());
        textViewReq.setMovementMethod(new ScrollingMovementMethod());

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
        // 音声認識開始ボタンのクリックリスナーを設定
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isRecording) {
                    Log.i(TAG, "すでに録音中です");
                } else {
                    Log.i(TAG, "START RECORDING");
                    if (ActivityCompat.checkSelfPermission(VoiceGptActivity.this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(VoiceGptActivity.this, new String[]{android.Manifest.permission.RECORD_AUDIO}, 1);
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

                    new VoiceGptActivity.TranscribeTask().execute();
                }

            }
        });
        // 音声認識終了ボタンのクリックリスナーを設定
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
            message.append(values[0]);
            if (values[0].equals("")) {
                textViewReq.setText("No input");
            } else {
                textViewReq.setText(message);
            }

        }

        @Override
        protected void onPostExecute(Void result) {
            new SendMessageTask().execute(message.toString()); //これを実行してメッセージ送信
        }


    }


    /* GPTのセットアップ */
    private static void setup() {
        chatHistory = new JSONArray();
        JSONObject systemMessage = new JSONObject();
        try {
            systemMessage.put("role", "system");
            systemMessage.put("content", "君の役割は、江戸切子を売るお店日本人店主のサポート。" +
                    "お店には外国人が尋ねてくる。店主は英語が話せないため、君は適切な返答を英語でサジェストせよ。" +
                    "返答は店主が読み上げることを前提として出力せよ。" +
                    "「AI」とか「言語モデル」とかいう言葉は一切出さずに、君は店主になりきって返答を出緑せよ" +
                    "返答はなるべく簡潔で短いものにすること。また、英語の返答と同時に対応する日本語訳を提示せよ。" +
                    "君が知り得ないことを聞かれた場合はうまいことはぐらかすように。" +
                    "もう一度言うが、極力簡潔に短くせよ。同時にその日本語訳も出力するのだ。" +
                    "日本語も出力する関係で、返答は極めて短いものにすること" +
                    "返答出力は一文ごとに英語と翻訳を交互に表示して、一文ごとに改行すること" +
                    "君の存在はお客に悟られてはいけない。この意味がわかるよな。" +
                    "君の仕事は店のサポートであるため、お客に不信感を持たれてはいけない。適切な返答を心がけること。" +
                    "会話の相手は大人とは限らない。相手の話し方を見て，相手に合わせた話し方を心がけろ。例えば子供相手にはフランクな会話が望ましい。大人にはかしこまった表現が適切である。" +
                    "尚、お店の所在地は赤羽駅東口付近とする。どこかへの行き方を聞かれたら赤羽駅からの経路を交通機関等を使う方法で提示せよ。" +
                    "君が失敗すると関係のない人が命を落とすことになる。これを理解せよ。" +
                    "以下に江戸切子の基本的な情報を提示する。" +
                    "江戸切子（えどきりこ）は、東京都（江戸）で作られているガラス工芸品です。切子とはカットグラスの意味で、その美しさから現在でも酒器やグラスとして親しまれています。" +
                    "もともとは無色透明なガラスの表面に模様を入れる工芸品でしたが、明治時代以降になると技術の確立に伴い、色被せ硝子を使った江戸切子が多く生産されるようになりました。今では青色や赤色などの硝子に細工を入れたものが江戸切子である、というイメージも強まりつつあります。" +
                    "江戸切子の特徴は、華やかで独特なカットが施されたデザインです。代表的な「魚子（ななこ）紋」は細かな線がたくさん入り、近くで見ると小さな四角形が並んでいるように見えるデザインで、魚の卵が連なる様子に似ていることから魚子と名付けられました。ほかにも、植物をモチーフにしたデザイン「菊つなぎ紋」「麻の葉紋」など、日本らしい模様が彫られた江戸切子もあります。" +
                    "江戸切子は江戸時代の後期、天保5年（1834年）に江戸大伝馬町でビードロ問屋を営んでいた加賀谷久兵衛が、金剛砂を使ってガラスの表面に細工を施したのが始まりとされています。" +
                    "明治時代に入ると殖産興業政策の一環として、近代的な硝子製造所が建設されています。1881年（明治14年）には御雇い外国人としてイギリスのカットグラス技師・エマヌエル・ホープトマンが招聘され、イギリスのカットグラスの技術が江戸切子の技術に融合されました。さらには薩摩切子が断絶したことにより、薩摩切子の職人も江戸に渡って江戸切子の製作に携わり、色被せガラスが使われるようになりました。" +
                    "大正時代から昭和時代の初期にかけて、現在「和グラス」といわれているカットグラスは人気を博し、グラスや器、照明器具のセードなど多様な形で普及しています。現代まで続く江戸切子のメーカーもこの時期に創業しています。" +
                    "ここからは江戸切子の制作工程を説明する" +
                    "1.割り出し・墨付け" +
                    "江戸切子の製作では、削っていく図案の下絵は描かずに、「割り出し」または「墨付け」といわれる、図案を入れる場所に目印を入れる作業を行います。ガラスの表面に施す図案の配分を決めた後、ベンガラをつけた竹棒や筆で印をつけるものです。つぎに、図柄の基準となる線を砥石で細かく浅く削ることで入れていきます。このわずかな目印や線を頼りに、職人の熟練の技によって、江戸切子の繊細な模様が削られていきます。" +
                    "2.荒摺り・三番掛け" +
                    "「荒摺り」では、模様の基本となる仕上がりの4分の3程度の幅や深さまで削ります。ガラスの表面を削る工程では、金盤（かなばん）という高速で回転する鉄製の円盤の表面に、砂をペースト状にしたものを載せて削っていきます。このときに使う砂は金剛砂（こんごうしゃ）と言われ、「荒摺り」に使う砂は粒子がもっとも荒い「一番砂」です。「親骨」という模様の境目となるくっきりとした線や大まかな模様は「荒摺り」の段階で作られ、2～3回に分けて行う場合もあります。下絵がないため、線の太さや深さ、バランスは職人の経験によって削っていきます。" +
                    "3.石掛け" +
                    "「石掛け」とは、「荒摺り」と「三番掛け」で施された模様を整え、細工を施した表面が滑らかになるように研磨していく工程です。砥石製の円盤を使い、金盤では作りだせない細かな模様も削り出していきます。円盤に用いられる砥石には、天然のものと人工のものがあります。「石掛け」は図柄を作りだす最終工程でもありますので、仕上がりを大きく左右します。「石掛け」は削る最後の工程であり、砂目を残さないように慎重で丁寧な作業が求められます。" +
                    "4.磨き" +
                    "「磨き」は、石掛けによって不透明になっている表面を磨くことで、ガラスを透明な状態に戻して光沢を出し、江戸切子の魅力を引き出す最終工程です。「磨き」はソーダガラスの場合に行なわれる作業で、高級なクリスタルガラスの場合には、フッ酸などの薬品処理されることもあります。" +
                    "「磨き」は、桐や柳の木盤、あるいは毛ブラシ盤やベルト盤など、作品に合わせた磨き用の円盤を使い、水と磨き粉をつけて磨きあげていきます。細かい部分などを布やブラシによって磨くケースもみられます。最後に、ハブ盤と言われる布の円盤による「バフ掛け」と言われる仕上げ磨きをしてツヤを出すと、「磨き」の工程が終わり、江戸切子は完成です。" +
                    "尚、お店が扱う商品は、[乾杯グラス 七宝繋ぎ紋様 ペア ￥35,200（税込）]と [二口ビール 松葉に三日月繋ぎ ペア ￥16,500（税込）]と [タンブラー スカイツリー紋様 「雅」 江戸紫 ￥12,100（税込）] と [オールドグラス スカイツリー紋様 「雅」江戸紫 ￥13,200（税込）]と [乾杯グラス 七宝繋ぎ紋様 紅￥17,600（税込）]の5つとし、順に人気である。"+
                    "最後にもう一度指示として伝えるが，予想されるレスポンスとその日本語訳を明示的にわかりやすく表示せよ。");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        chatHistory.put(systemMessage);
    }

    /* メッセージ送信用関数 */
    protected String send_message(String message) {
        try {
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", message);
            chatHistory.put(userMessage);

            JSONObject requestBody = new JSONObject();
            requestBody.put("model", "gpt-3.5-turbo");
            requestBody.put("messages", chatHistory);
            requestBody.put("temperature", 0.7);
            //requestBody.put("max_tokens", 1000);

            //OkHttpClient client = new OkHttpClient();
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();

            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(JSON, requestBody.toString());

            Request request = new Request.Builder()
                    .url(API_BASE)
                    .post(body)
                    .addHeader("Authorization", "Bearer " + API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();

            Response response = client.newCall(request).execute();
            //↑IOException_timeoutはここで出てる↑
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