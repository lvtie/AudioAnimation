package com.lvt.audioanimationview;

import android.Manifest;
import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.core.content.PermissionChecker;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.Permission;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 *
 */
public final class WavRecorderUtils {
  private static final int MAX_ZERO_COUNT = 20;

  private static volatile WavRecorderUtils sInstance;

  @NonNull
  private RecordHandler mHandler;
  @Nullable
  private AudioRecord mAudioRecord;
  @Nullable
  private Callback mCallback;
  @Nullable
  private Executor mExecutor;
  private volatile boolean mIsRecording;
  private int mBufferSize;
  @Nullable
  private String mWavFilePath;
  @Nullable
  private Parameters mParameters;
  private WavRecorderUtils() {
    mHandler = new RecordHandler(this);
  }

  @NonNull
  public static WavRecorderUtils getInstance() {
    if (sInstance == null) {
      synchronized (WavRecorderUtils.class) {
        if (sInstance == null) {
          sInstance = new WavRecorderUtils();
        }
      }
    }
    return sInstance;
  }

  /**
   * 参数
   */
  public static final class Parameters {
    private int mAudioSource;
    private int mSampleRateInHz;
    private int mChannelConfig;
    private int mAudioFormat;

    private Parameters(@NonNull Builder builder) {
      mAudioSource = builder.mAudioSource;
      mSampleRateInHz = builder.mSampleRateInHz;
      mChannelConfig = builder.mChannelConfig;
      mAudioFormat = builder.mAudioFormat;
    }

    public static final class Builder {
      private int mAudioSource;
      private int mSampleRateInHz;
      private int mChannelConfig;
      private int mAudioFormat;

      public Builder() {
        mAudioSource = MediaRecorder.AudioSource.MIC;
        mSampleRateInHz = 16000;
        mChannelConfig = AudioFormat.CHANNEL_IN_DEFAULT;
        mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
      }

      @NonNull
      public Builder setAudioSource(int audioSource) {
        mAudioSource = audioSource;
        return this;
      }

      @NonNull
      public Builder setSampleRateInHz(int sampleRateInHz) {
        mSampleRateInHz = sampleRateInHz;
        return this;
      }

      @NonNull
      public Builder setChannelConfig(int channelConfig) {
        mChannelConfig = channelConfig;
        return this;
      }

      @NonNull
      public Builder setAudioFormat(int audioFormat) {
        mAudioFormat = audioFormat;
        return this;
      }

      @NonNull
      public Parameters build() {
        // 判断 channel config 参数是否合法
        if (getChannelCount(mChannelConfig) == -1) {
          throw new IllegalArgumentException("bad channel config");
        }
        return new Parameters(this);
      }
    }
  }

  /**
   * 录音 Handler
   */
  private static final class RecordHandler extends Handler {
    //<editor-fold desc="常量">
    private static final int WHAT_START = 0;
    private static final int WHAT_RECORDING = 1;
    private static final int WHAT_FINISH = 2;
    private static final int WHAT_ERROR = 3;

    @NonNull
    private WeakReference<WavRecorderUtils> mWeakHelper;

    private RecordHandler(@NonNull WavRecorderUtils helper) {
      super(Looper.getMainLooper());
      mWeakHelper = new WeakReference<>(helper);
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
      super.handleMessage(msg);
      WavRecorderUtils helper = mWeakHelper.get();
      if (helper != null && helper.mCallback != null) {
        final Object object = msg.obj;
        switch (msg.what) {
          case WHAT_START:
            helper.mCallback.onRecordStart();
            break;
          case WHAT_RECORDING:
            if (object instanceof RecordingResult) {
              helper.mCallback.onRecording((RecordingResult) object);
            }
            break;
          case WHAT_FINISH:
            if (object instanceof FinishResult) {
              helper.mCallback.onRecordFinished((FinishResult) object);
            }
            break;
          case WHAT_ERROR:
            if (object instanceof Throwable) {
              helper.mCallback.onRecordError((Throwable) object);
              helper.release();
            }
            break;
          default:
            break;
        }
      }
    }

    /**
     * 发送开始消息
     */
    private void sendStartMessage() {
      sendMessage(WHAT_START, null);
    }

    /**
     * 发送录音消息
     *
     * @param result 录音结果
     */
    private void sendRecordingMessage(@NonNull RecordingResult result) {
      sendMessage(WHAT_RECORDING, result);
    }

    /**
     * 发送结束消息
     */
    private void sendFinishMessage(@NonNull FinishResult result) {
      sendMessage(WHAT_FINISH, result);
    }

    /**
     * 发送错误消息
     */
    private void sendErrorMessage(@NonNull Throwable e) {
      sendMessage(WHAT_ERROR, e);
    }

    private void sendMessage(int what, @Nullable Object object) {
      Message message = Message.obtain();
      message.what = what;
      message.obj = object;
      sendMessage(message);
    }
  }

  /**
   * 录音结果
   */
  public static final class RecordingResult {
    private float mVolumeProcess;
    private long mDuration;

    public float getVolumeProcess() {
      return mVolumeProcess;
    }

    public long getDuration() {
      return mDuration;
    }
  }

  /**
   * 录音结果
   */
  public static final class FinishResult {
    private long mDuration;

    public long getDuration() {
      return mDuration;
    }
  }

  /**
   * 回调
   */
  public interface Callback {
    /**
     * 开始录音回调
     */
    void onRecordStart();

    /**
     * 录音中回调
     *
     * @param result 录音结果
     */
    void onRecording(@NonNull RecordingResult result);

    /**
     * 录音结束回调
     *
     * @param result 结束结果
     */
    void onRecordFinished(FinishResult result);

    /**
     * 录音失败
     *
     * @param e 异常
     */
    void onRecordError(@NonNull Throwable e);
  }
  //</editor-fold>

  //<editor-fold desc="公开方法">

  /**
   * 设置回调接口
   *
   * @param callback 回调接口
   */
  public void setCallback(@Nullable Callback callback) {
    mCallback = callback;
  }

  /**
   * 录音
   *
   * @param wavFilePath WAV 文件路径
   */
  @RequiresPermission(Manifest.permission.RECORD_AUDIO)
  public void record(@NonNull String wavFilePath) {
    record(wavFilePath, new Parameters.Builder().build());
  }

  /**
   * 录音
   *
   * @param wavFilePath WAV 文件路径
   * @param parameters 参数
   */
  public void record(@NonNull String wavFilePath, @NonNull Parameters parameters) {
    if (mAudioRecord != null) {
      mAudioRecord.release();
    }
    try {
      mBufferSize = AudioRecord.getMinBufferSize(
          parameters.mSampleRateInHz, parameters.mChannelConfig, parameters.mAudioFormat
      )*2;  //数值越大 绘制的图形越明显
      mAudioRecord = new AudioRecord(
          parameters.mAudioSource,
          parameters.mSampleRateInHz,
          parameters.mChannelConfig,
          parameters.mAudioFormat,
          mBufferSize
      );
      // 录音
      mAudioRecord.startRecording();
      // 检测状态
      if (mAudioRecord.getState() == AudioRecord.STATE_UNINITIALIZED) {
        mHandler.sendErrorMessage(new Throwable("audio record is uninitialized"));
        return;
      }
      // 设置正在录音
      mIsRecording = true;
      // 设置 wav 文件路径
      mWavFilePath = wavFilePath;
      // 设置参数
      mParameters = parameters;
      // 写入录音数据
      writeRecordData();
    } catch (IllegalArgumentException e) {
      mHandler.sendErrorMessage(e);
    } catch (IllegalStateException e) {
      mHandler.sendErrorMessage(e);
    }
  }

  /**
   * 停止录音
   */
  public void stop() {
    mIsRecording = false;
    if (mAudioRecord != null) {
      try {
        mAudioRecord.stop();
      } catch (IllegalStateException e) {
        // do nothing
      }
    }
  }

  /**
   * 释放
   */
  public void release() {
    mCallback = null;
    stop();
    if (mAudioRecord != null) {
      mAudioRecord.release();
      mAudioRecord = null;
    }
  }
  //</editor-fold>

  //<editor-fold desc="私有方法">

  /**
   * 写入录音数据
   */
  private void writeRecordData() {
    // 开启子线程处理录音数据
    if (mExecutor == null) {
      mExecutor = Executors.newSingleThreadExecutor();
    }
    final byte[] pcmBuffer = new byte[mBufferSize];
    mExecutor.execute(new Runnable() {
      @SuppressWarnings("TryFinallyCanBeTryWithResources")
      @Override
      public void run() {
        if (mWavFilePath != null && mParameters != null) {
          long currentTime = System.currentTimeMillis();
          ByteArrayOutputStream bos = null;
          final boolean is8Bit = mParameters.mAudioFormat == AudioFormat.ENCODING_PCM_8BIT;
          final float maxAmplitude = is8Bit ? Byte.MAX_VALUE : Short.MAX_VALUE;
          final int minPeek = (int) (maxAmplitude * 0.1f);
          boolean hasError = false;
          mHandler.sendStartMessage();
          try {
            bos = new ByteArrayOutputStream();
            int zeroCount = 0;
            // 读取 pcm 数据 并 保存进字节流中
            while (mIsRecording) {
              if (mAudioRecord == null) {
                break;
              }
              int read = mAudioRecord.read(pcmBuffer, 0, mBufferSize);
              if(recordListener != null){
                recordListener.onRecord(pcmBuffer);
              }
              // 定义录音结果
              RecordingResult recordingResult = new RecordingResult();
              if (read > 0) {
                zeroCount = 0;
                // 写入数据
                bos.write(pcmBuffer, 0, read);
                // 获取当前音量
                double sum = 0;
                int handleCount = 0;
                if (is8Bit) {
                  for (int i = 0; i < read; i++) {
                    if (Math.abs(pcmBuffer[i]) > minPeek) {
                      sum += pcmBuffer[i] * pcmBuffer[i];
                      handleCount++;
                    }
                  }
                } else {
                  for (int i = 0; i < read; i += 2) {
                    if (i + 1 != read) {
                      short peek = (short) (((pcmBuffer[i] & 0xFF) << 8) | (pcmBuffer[i] & 0xFF));
                      if (peek > minPeek) {
                        sum += peek * peek;
                        handleCount++;
                      }
                    }
                  }
                }
                if (handleCount != 0) {
                  final double amplitude = Math.sqrt(sum / handleCount);
                  // 通知正在录音
                  recordingResult.mVolumeProcess = (float) (amplitude / maxAmplitude);
                  if (recordingResult.mVolumeProcess < 0) {
                    recordingResult.mVolumeProcess = 0;
                  } else if (recordingResult.mVolumeProcess > 1) {
                    recordingResult.mVolumeProcess = 1;
                  }
                }
              } else if (read == 0) {
                // 判断异常
                zeroCount++;
                if (zeroCount > MAX_ZERO_COUNT) {
                  hasError = true;
                  mHandler.sendErrorMessage(new Throwable("can not record audio"));
                  break;
                }
              }
              recordingResult.mDuration = System.currentTimeMillis() - currentTime;
              mHandler.sendRecordingMessage(recordingResult);
            }
            if (!hasError) {
              // 获取时长
              long totalDuration = System.currentTimeMillis() - currentTime;
              // 将 pcm 数据转换成 wav 数据
              writePcmDataToWavFile(bos.toByteArray(), new File(mWavFilePath), mParameters);
              // 通知结束
              FinishResult finishResult = new FinishResult();
              finishResult.mDuration = totalDuration;
              mHandler.sendFinishMessage(finishResult);
            }
          } catch (IOException e) {
            // 通知失败
            mHandler.sendErrorMessage(e);
          } finally {
            if (bos != null) {
              try {
                bos.close();
              } catch (IOException e) {
                // do nothing
              }
            }
          }
        }
      }
    });
  }

  /**
   * 将 pcm 数据写入 wav 文件
   *
   * @param pcmData pcm 数据
   * @param wavFile wav 文件
   */
  private void writePcmDataToWavFile(
      @NonNull byte[] pcmData,
      @NonNull File wavFile,
      @NonNull Parameters parameters)
      throws IOException {
    long totalAudioLength = pcmData.length;
    long totalDataLength = pcmData.length + 36;
    long longSampleRate = parameters.mSampleRateInHz;
    int channelCount = getChannelCount(parameters.mChannelConfig);
    long byteRate = 16 * parameters.mSampleRateInHz * channelCount / 8;
    FileOutputStream fos = new FileOutputStream(wavFile);
    /// 添加 wav 头部
    byte[] header = new byte[44];
    // RIFF/WAVE header
    header[0] = 'R';
    header[1] = 'I';
    header[2] = 'F';
    header[3] = 'F';
    header[4] = (byte) (totalDataLength & 0xff);
    header[5] = (byte) ((totalDataLength >> 8) & 0xff);
    header[6] = (byte) ((totalDataLength >> 16) & 0xff);
    header[7] = (byte) ((totalDataLength >> 24) & 0xff);
    //WAVE
    header[8] = 'W';
    header[9] = 'A';
    header[10] = 'V';
    header[11] = 'E';
    // 'fmt ' chunk
    header[12] = 'f';
    header[13] = 'm';
    header[14] = 't';
    header[15] = ' ';
    // 4 bytes: size of 'fmt ' chunk
    header[16] = 16;
    header[17] = 0;
    header[18] = 0;
    header[19] = 0;
    // format = 1 (PCM)
    header[20] = 1;
    header[21] = 0;
    header[22] = (byte) channelCount;
    header[23] = 0;
    header[24] = (byte) (longSampleRate & 0xff);
    header[25] = (byte) ((longSampleRate >> 8) & 0xff);
    header[26] = (byte) ((longSampleRate >> 16) & 0xff);
    header[27] = (byte) ((longSampleRate >> 24) & 0xff);
    header[28] = (byte) (byteRate & 0xff);
    header[29] = (byte) ((byteRate >> 8) & 0xff);
    header[30] = (byte) ((byteRate >> 16) & 0xff);
    header[31] = (byte) ((byteRate >> 24) & 0xff);
    // block align
    header[32] = (byte) (channelCount * 16 / 8);
    header[33] = 0;
    // bits per sample
    header[34] = 16;
    header[35] = 0;
    // data
    header[36] = 'd';
    header[37] = 'a';
    header[38] = 't';
    header[39] = 'a';
    header[40] = (byte) (totalAudioLength & 0xff);
    header[41] = (byte) ((totalAudioLength >> 8) & 0xff);
    header[42] = (byte) ((totalAudioLength >> 16) & 0xff);
    header[43] = (byte) ((totalAudioLength >> 24) & 0xff);
    // 写入头
    fos.write(header, 0, header.length);
    // 写入 pcm 数据
    fos.write(pcmData, 0, pcmData.length);
    fos.close();
  }

  /**
   * 获取声道数
   *
   * @param channelConfig 声道配置
   * @return 声道数
   */
  private static int getChannelCount(int channelConfig) {
    switch (channelConfig) {
      case AudioFormat.CHANNEL_IN_DEFAULT:
      case AudioFormat.CHANNEL_IN_MONO:
      case AudioFormat.CHANNEL_CONFIGURATION_MONO:
        return 1;
      case AudioFormat.CHANNEL_IN_STEREO:
      case AudioFormat.CHANNEL_CONFIGURATION_STEREO:
      case (AudioFormat.CHANNEL_IN_FRONT | AudioFormat.CHANNEL_IN_BACK):
        return 2;
      case AudioFormat.CHANNEL_INVALID:
      default:
        return -1;
    }
  }
  RecordListener recordListener;
  interface RecordListener{
    void onRecord(byte[] data);
  }
  public void setRecordListener(RecordListener l){
    recordListener = l;
  }
}
