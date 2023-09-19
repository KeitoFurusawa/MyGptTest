package com.example.mygpttest;

import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;

public class GPT4Chat extends AppCompatActivity {
    private static final String API_KEY = "8UCHXpBd7Q2Lw86gWWAFCY6XGBO8JiGUVplGo6AJwsIHK3lIQDqS7HlDMCIs9q4fI0Mf4hvWqiYmEopDxTyVBBw";
    private static final String API_BASE = "https://api.openai.iniad.org/api/v1";

    protected static JSONArray chatHistory = new JSONArray();

    public GPT4Chat() {
        setup();
    }

    public static void main(String[] args) {
        System.out.println("GPT: こんにちは！どのようにお手伝いできますか？");
        setup();
    }

    private static void setup() {
        chatHistory = new JSONArray();
        JSONObject systemMessage = new JSONObject();
        try {
            systemMessage.put("role", "system");
            systemMessage.put("content", "You are a helpful assistant.");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        chatHistory.put(systemMessage);
    }

    protected String getUserInput() {
        // EditTextからユーザーの入力を取得
        return "0";
    }

    protected static String send_message(String message) {
        try {
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", message);
            chatHistory.put(userMessage);

            JSONObject requestBody = new JSONObject();
            requestBody.put("model", "gpt-4");
            requestBody.put("messages", chatHistory);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 1000);

            OkHttpClient client = new OkHttpClient();
            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(JSON, requestBody.toString());

            Request request = new Request.Builder()
                    .url(API_BASE)
                    .post(body)
                    .addHeader("Authorization", "Bearer " + API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();

            Response response = client.newCall(request).execute();

            if (response.isSuccessful()) {
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
                return "APIリクエストが失敗しました: " + response.code() + " " + response.message();
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return "エラー: " + e.getMessage();
        } catch (IOException e) {
            e.printStackTrace();
            return "エラー: " + e.getMessage();
        }
    }
}

