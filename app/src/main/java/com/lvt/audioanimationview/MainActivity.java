package com.lvt.audioanimationview;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    /**
     * 如果想显示的波形更层次更明显请修改pcm传的数据量大小  或者添加些随机数
     *
     */
    private TextView tvText;
    private boolean isRecoding;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvText = findViewById(R.id.tv_text);
        final AudioAnimationView audioView = findViewById(R.id.audio_View);
        tvText.setOnClickListener(view -> {
            if(!isRecoding) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(MainActivity.this.getApplicationContext(),"请先开启录音权限哈",1).show();
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10);
                    return;
                }
                isRecoding = true;
                WavRecorderUtils.getInstance().record("/sdcard/aa.wav");
                tvText.setText("点击停止");
            }else{
                isRecoding = false;
                WavRecorderUtils.getInstance().stop();
                tvText.setText("开始录音");
            }
        });

        WavRecorderUtils.getInstance().setRecordListener(data -> {
            if (audioView != null) {
                audioView.setmFFTBytes(data);
            }
        });


        Paint paint = new Paint();
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dip2px(8));
        paint.setColor(Color.parseColor("#00A5FF"));
        int maxCount = 20; //最大条数
        int interval = dip2px(6);
        VoiceGraphRenderer barGraphRenderer = new VoiceGraphRenderer(paint, maxCount, interval, 1,
                isHaveVoice -> {
                    if (isHaveVoice) {
                        //xxx.setText("xxxx");
                    } else {
                       // xxx.setText("大声点，听不见哦～");
                    }
                });

        RandomVoiceGraphRenderer randomRenderer = new RandomVoiceGraphRenderer(paint, maxCount, interval, 1,
                isHaveVoice -> {
                    if (isHaveVoice) {
                        //xxx.setText("xxxx");
                    } else {
                        // xxx.setText("大声点，听不见哦～");
                    }
                });
        if (audioView != null) {
            audioView.addRenderer(barGraphRenderer);
        }
//        if(randomRenderer != null){  //更好看些的波形
//            audioView.addRenderer(randomRenderer);
//        }
    }
    int dip2px(int dps) {
        return Math.round(getResources().getDisplayMetrics().density * dps);
    }
}
