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

public class ChatGptActivity extends AppCompatActivity {
    private static final String TAG = "main";
    private static final String API_KEY = "8UCHXpBd7Q2Lw86gWWAFCY6XGBO8JiGUVplGo6AJwsIHK3lIQDqS7HlDMCIs9q4fI0Mf4hvWqiYmEopDxTyVBBw";
    private static final String API_BASE = "https://api.openai.iniad.org/api/v1/chat/completions";
    protected static JSONArray chatHistory = new JSONArray();
    protected EditText editText;
    protected Button buttonSend;
    protected TextView textViewRes;
    protected TextView textViewReq;
    private Button buttonGoToSpeech;
    private static String prompt;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_gpt);

        // UI要素を取得
        editText = findViewById(R.id.editText);
        buttonSend = findViewById(R.id.buttonSend);
        buttonGoToSpeech = findViewById(R.id.buttonGoToSpeech);
        textViewRes = findViewById(R.id.textViewRes);
        textViewReq = findViewById(R.id.textViewReq);

        // 文字列リソースで静的な変数を初期化
        prompt = getString(R.string.prompt_osaka);

        // UI要素の初期設定
        textViewRes.setMovementMethod(new ScrollingMovementMethod());
        textViewReq.setMovementMethod(new ScrollingMovementMethod());

        // 送信ボタンのクリックリスナーを設定
        buttonSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String user_input = getUserInput();
                if (user_input.equalsIgnoreCase("--end")) {
                    textViewRes.setText("End Chat");
                } else if (user_input.equalsIgnoreCase("--hist")) {
                    textViewRes.setText(chatHistory.toString());
                } else {
                    //メッセージを送信(非同期で実行)
                    new SendMessageTask().execute(user_input);
                }
            }
        });
        // 画面遷移ボタンのクリックリスナーを設定
        buttonGoToSpeech.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 新しいアクティビティに遷移するためのIntentを作成
                Intent intent = new Intent(ChatGptActivity.this, MainActivity.class);
                startActivity(intent); // 新しいアクティビティを起動
            }
        });
        //初期設定を行う
        setup();
    }

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
    }

    protected String getUserInput() {
        // EditTextからユーザーの入力を取得
        EditText editText = findViewById(R.id.editText);
        String userInput = editText.getText().toString();
        return userInput;
    }

    private class SendMessageTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            //バックグラウンドで実行される処理
            String message = params[0];
            return send_message(message);
        }

        @Override
        protected void onPostExecute(String response) {
            //タスクが完了した後に実行される処理
            // レスポンスをTextViewに表示
            textViewRes.setText("GPT: " + response);
            Log.d(TAG, "onPostExecute: " + response);
        }
    }

    protected String send_message(String message) {
        try {
            textViewReq.setText(editText.getText());
            editText.setText("");
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