package com.lvt.audioanimationview;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

public class RandomVoiceGraphRenderer extends Renderer {
    private Paint mPaint;
    private Paint mGrayPaint;
    private int lineNumber = 0;
    private int lineInterval = 0;
    private int amplitudePixel = 0;
    private VoiceChangeCallBack myCallBack;
    private boolean isHaveVoice = true;
    protected float[] mFFTPoints;

    protected Paint mBgPoints;

    public RandomVoiceGraphRenderer(Paint paint, int count, int interval, int pixel, VoiceChangeCallBack callBack) {
        super();
        mPaint = paint;
        lineNumber = count;
        lineInterval = interval;
        amplitudePixel = pixel;
        myCallBack = callBack;
        if (mFFTPoints == null || mFFTPoints.length < lineNumber * 4) {
            mFFTPoints = new float[lineNumber * 4];
        }
        mGrayPaint = new Paint();
        mGrayPaint.setStrokeCap(Paint.Cap.ROUND);
        mGrayPaint.setStrokeWidth(mPaint.getStrokeWidth());
        mGrayPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
        mGrayPaint.setColor(Color.parseColor("#e9e9e9"));
        mBgPoints = new Paint();
        mBgPoints.setColor(Color.parseColor("#e9e9e9"));
        mBgPoints.setStrokeCap(Paint.Cap.ROUND);
        mBgPoints.setStrokeWidth(mPaint.getStrokeWidth());
    }
    //如果录音的编码是16位pcm，而录音数据数据是byte，需要将两个byte转为一个short进行处理，建议用小端的方式。
    private double doublecalculateVolume(byte[] buffer, int offset, int interval) {
        double sumVolume = 0.0;
        double avgVolume = 0.0;
        for (int i = offset * interval; i < offset * interval + interval; i += 2) {
            int v1 = buffer[i] & 0xFF;
            int v2 = buffer[i + 1] & 0xFF;
            int temp = v1 + (v2 << 8);// 小端 我们采用小端
            if (temp >= 0x8000) {
                temp = 0xffff - temp;
            }
            sumVolume += Math.abs(temp);
        }
        avgVolume = sumVolume / buffer.length / 2;
        return avgVolume;
    }

    //录音的编码主要有两种：8位pcm和16位pcm。8位pcm用一个字节表示语音的一个点，16位pcm用两个字节，也就是一个short来表示语音的一个点。需要特别注意的是，如果你用的16位pcm编码，而取录音数据用的是byte的话，需要自己将两个bye转换成一个short。将两个byte转换成一个short，有小端和大端两种，一般默认情况都是小端，但是有的开源库，比如lamemp3需要的就是大端，这个要根据不同的情况进行不同的处理。
    private double calculateVolume(short[] buffer){
        double sumVolume = 0.0;
        double avgVolume = 0.0;
        double volume = 0.0;
        for(short b : buffer){
            sumVolume += Math.abs(b)* Math.abs(b);
        }
        // 平均音量大小
        avgVolume = sumVolume / buffer.length;
        // 音量转分贝的公式
        volume = Math.log10(1 + avgVolume) * 10;
        return volume;
    }

    long timeInterval = 0L;

    @Override
    public void onRender(Canvas canvas, byte[] data, int width, int height) {
        if (data == null || data.length == 0) {
            myCallBack.isHaveVoice(false);
            return;
        }
        int interval = (data.length / lineNumber) / 2 * 2; // 每组绘制直线的间隔
        float centerDis = (width - (lineNumber * mPaint.getStrokeWidth() + (lineNumber - 1) * lineInterval)) / 2;
        for (int i = 0; i < lineNumber; ++i) {
            double dbValue = doublecalculateVolume(data, i, interval);
            float x = i * (lineInterval + mPaint.getStrokeWidth()) + mPaint.getStrokeWidth() / 2 + centerDis;
            mFFTPoints[i * 4] = x;
            mFFTPoints[i * 4 + 2] = x;

            if (dbValue < 4) {
                if (timeInterval == 0) {
                    timeInterval = System.currentTimeMillis();
                }
                mFFTPoints[i * 4 + 1] = height / 2 + 1;
                mFFTPoints[i * 4 + 3] = height / 2 - 1;

                if (System.currentTimeMillis() - timeInterval >= 3000) {  //持续3秒低于预定值提示用户大声点
                    timeInterval = System.currentTimeMillis();
                    isHaveVoice = false;
                    myCallBack.isHaveVoice(isHaveVoice);
                }
            } else {
                dbValue = dbValue * Math.random() * 2;
                timeInterval = System.currentTimeMillis();
                if (dbValue * amplitudePixel + mPaint.getStrokeWidth() > height / 2) {
                    mFFTPoints[i * 4 + 1] = height - mPaint.getStrokeWidth();
                    mFFTPoints[i * 4 + 3] = 0 + mPaint.getStrokeWidth();
                } else {
                    mFFTPoints[i * 4 + 1] = height / 2 + (float) dbValue * amplitudePixel;
                    mFFTPoints[i * 4 + 3] = height / 2 - (float) dbValue * amplitudePixel;
                }
                if (!isHaveVoice) {
                    isHaveVoice = true;
                    myCallBack.isHaveVoice(isHaveVoice);
                }
            }

        }
        canvas.drawLines(mFFTPoints, mPaint);
    }

    @Override
    public void drawBackground(Canvas mCanvas, int width, int height) {
        float centerDis = (width - (lineNumber * mPaint.getStrokeWidth() + (lineNumber - 1) * lineInterval)) / 2;
        for (int i = 0; i < lineNumber; ++i) {
            float x = i * (lineInterval + mPaint.getStrokeWidth()) + mPaint.getStrokeWidth() / 2 + centerDis;
            mCanvas.drawPoint(x, height / 2, mBgPoints);
        }
    }

    public interface VoiceChangeCallBack {
        void isHaveVoice(boolean isHaveVoice);
    }
}
