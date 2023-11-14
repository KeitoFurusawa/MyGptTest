package com.example.mygpttest;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.content.Intent;

public class MainActivity extends AppCompatActivity {

    private ListView buttonListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonListView = findViewById(R.id.buttonListView);

        // リストビューに表示するボタンのリスト
        String[] buttonNames = {"ChatGPT", "VoiceStreamingTest", "VoiceGPT"};

        // リストビューにボタンのリストを設定するアダプター
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, buttonNames);
        buttonListView.setAdapter(adapter);

        // リストビューのアイテムがクリックされたときの処理
        buttonListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // クリックされたアイテムに応じて遷移処理を行う
                switch (position) {
                    case 0:
                        // 画面1に遷移する処理
                        startActivity(new Intent(MainActivity.this, ChatGptActivity.class));
                        break;
                    case 1:
                        // 画面2に遷移する処理
                        startActivity(new Intent(MainActivity.this, VoiceTestActivity2.class));
                        break;
                    case 2:
                        // 画面3に遷移する処理
                        startActivity(new Intent(MainActivity.this, VoiceGptActivity.class));
                        break;
                }
            }
        });
    }
}
