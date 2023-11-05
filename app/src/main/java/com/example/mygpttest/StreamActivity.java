package com.example.mygpttest;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import android.widget.Toast;


public class StreamActivity extends AppCompatActivity {
    private SpeechRecognitionTask recognitionTask;
    private boolean isTaskRunning = false;
    private static final String TAG = "speech";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionToRecordAccepted = false;
    private Button startButton;
    private TextView resultTextView;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stream);
        status = findViewById(R.id.textViewStatus);
        startButton = findViewById(R.id.StartButton);
        resultTextView = findViewById(R.id.resultStoT_TextView);

        // マイクのパーミッションが許可されているか確認
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            // マイクのパーミッションが許可されていない場合、リクエストを送信
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
        } else {
            setup();
        }


    }

    // パーミッションリクエストの結果を受け取るメソッド
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // マイクのパーミッションが許可された場合、初期化処理を続行
                setup();
            } else {
                // マイクのパーミッションが許可されなかった場合、ユーザーに通知
                Toast.makeText(this, "マイクの使用が許可されていません。", Toast.LENGTH_SHORT).show();
                // あるいは、適切なエラーハンドリングを行う
            }
        }
    }

    private void setup() {
        Log.d(TAG, "run setup()");
        // マイクのパーミッションが許可されている場合、初期化処理を続行
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Task Status: " + ((recognitionTask == null) ? "null" : recognitionTask.getStatus()));
                if (!isTaskRunning) {
                    // 非同期タスクが実行中でない場合、新しいタスクを開始する
                    Log.d(TAG, "Starting the task");
                    isTaskRunning = true;
                    status.setText("listening");
                    recognitionTask = new SpeechRecognitionTask(StreamActivity.this) {
                        @Override
                        protected void onPostExecute(String result) {
                            resultTextView.setText(result);
                            isTaskRunning = false;
                            status.setText("stop");
                        }
                    };
                    recognitionTask.execute();
                    isTaskRunning = true;
                    status.setText("listening");
                } else {
                    // 非同期タスクが実行中の場合、タスクをキャンセルする
                    if (recognitionTask != null) {
                        Log.d(TAG, "Cancelling the task");
                        recognitionTask.cancel(true);
                    }
                    isTaskRunning = false;
                    status.setText("stop");
                }
            }
        });

    }
}
