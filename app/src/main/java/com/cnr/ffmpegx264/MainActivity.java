/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cnr.ffmpegx264;

import android.Manifest;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.cnr.ffmpegx264.encode.IMediaRecorder;
import com.cnr.ffmpegx264.jniinterface.FFmpegBridge;
import com.google.android.cameraview.AspectRatio;
import com.google.android.cameraview.CameraView;
import com.google.android.cameraview.Size;

import java.io.File;
import java.util.Set;


/**
 * This demo app saves the taken picture to a constant file.
 * $ adb pull /sdcard/Android/data/com.google.android.cameraview.demo/files/Pictures/picture.jpg
 */
public class MainActivity extends AppCompatActivity implements
        ActivityCompat.OnRequestPermissionsResultCallback,
        AspectRatioFragment.Listener,IMediaRecorder{

    /**
     * 采样率设置不支持
     */
    public static final int AUDIO_RECORD_ERROR_SAMPLERATE_NOT_SUPPORT = 1;
    /**
     * 最小缓存获取失败
     */
    public static final int AUDIO_RECORD_ERROR_GET_MIN_BUFFER_SIZE_NOT_SUPPORT = 2;
    /**
     * 创建AudioRecord失败
     */
    public static final int AUDIO_RECORD_ERROR_CREATE_FAILED = 3;
    private boolean mRecording = false;
    public static final int AUDIO_RECORD_ERROR_UNKNOWN = 0;
    // 视频编码任务
    private VideoStreamTask mVideoStreamTask;

    // 音频编码任务
    private AudioStreamTask mAudioStreamTask;

    // 音频录制
    private AudioRecorder mAudioRecorder;

    private static final String TAG = "TAG";

    private static final int REQUEST_CAMERA_PERMISSION = 1;

    private static final String FRAGMENT_DIALOG = "dialog";

    private static final int[] FLASH_OPTIONS = {
            CameraView.FLASH_AUTO,
            CameraView.FLASH_OFF,
            CameraView.FLASH_ON,
    };

    private static final int[] FLASH_ICONS = {
            R.drawable.ic_flash_auto,
            R.drawable.ic_flash_off,
            R.drawable.ic_flash_on,
    };

    private static final int[] FLASH_TITLES = {
            R.string.flash_auto,
            R.string.flash_off,
            R.string.flash_on,
    };

    private int mCurrentFlash;

    private CameraView mCameraView;

    private Handler mBackgroundHandler;

    private View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.take_picture:
                    if (mCameraView != null) {
                        mCameraView.takePicture();
                    }
                    break;
            }
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCameraView = (CameraView) findViewById(R.id.camera);
        if (mCameraView != null) {
            mCameraView.addCallback(mCallback);
        }
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.take_picture);
        if (fab != null) {
            fab.setOnClickListener(mOnClickListener);
        }
        Button start = (Button) findViewById(R.id.start);
        if (start != null) {
            start.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED) {
                        mCameraView.start();
                        startAudioRecord();
                    } else if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                            Manifest.permission.CAMERA)) {
                        ConfirmationDialogFragment
                                .newInstance(R.string.camera_permission_confirmation,
                                        new String[]{Manifest.permission.CAMERA},
                                        REQUEST_CAMERA_PERMISSION,
                                        R.string.camera_permission_not_granted)
                                .show(getSupportFragmentManager(), FRAGMENT_DIALOG);
                    } else {
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA},
                                REQUEST_CAMERA_PERMISSION);
                    }
                }
            });
        }
        Button stop = (Button) findViewById(R.id.stop);
        if (stop != null) {
            stop.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    mCameraView.stop();
                    stopAudioRecord();
                }
            });
        }
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
        }
    }
    @Override
    public void onAudioError(int what, String message) {
        Log.d("onAudioError", "what = " + what + ", message = " + message);
    }

    @Override
    public void receiveAudioData(final byte[] sampleBuffer, final int len) {
        // 音频编码
        if (mRecording) {
            getBackgroundHandler().post(new Runnable() {
                @Override
                public void run() {
                    if (null != mAudioStreamTask) {
                        switch(mAudioStreamTask.getStatus()) {
                            case RUNNING:
                                return;

                            case PENDING:
                                mAudioStreamTask.cancel(false);
                                break;
                        }
                    }
                    mAudioStreamTask = new AudioStreamTask(sampleBuffer, len);
                    mAudioStreamTask.execute((Void)null);
                }
            });

        }
    }
    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    protected void onPause() {
        mCameraView.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBackgroundHandler != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mBackgroundHandler.getLooper().quitSafely();
            } else {
                mBackgroundHandler.getLooper().quit();
            }
            mBackgroundHandler = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CAMERA_PERMISSION:
                if (permissions.length != 1 || grantResults.length != 1) {
                    throw new RuntimeException("Error on requesting camera permission.");
                }
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.camera_permission_not_granted,
                            Toast.LENGTH_SHORT).show();
                }
                // No need to start camera here; it is handled by onResume
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.aspect_ratio:
                FragmentManager fragmentManager = getSupportFragmentManager();
                if (mCameraView != null
                        && fragmentManager.findFragmentByTag(FRAGMENT_DIALOG) == null) {
                    final Set<AspectRatio> ratios = mCameraView.getSupportedAspectRatios();
                    final AspectRatio currentRatio = mCameraView.getAspectRatio();
                    AspectRatioFragment.newInstance(ratios, currentRatio)
                            .show(fragmentManager, FRAGMENT_DIALOG);
                }
                return true;
            case R.id.switch_flash:
                if (mCameraView != null) {
                    mCurrentFlash = (mCurrentFlash + 1) % FLASH_OPTIONS.length;
                    item.setTitle(FLASH_TITLES[mCurrentFlash]);
                    item.setIcon(FLASH_ICONS[mCurrentFlash]);
                    mCameraView.setFlash(FLASH_OPTIONS[mCurrentFlash]);
                }
                return true;
            case R.id.switch_camera:
                if (mCameraView != null) {
                    int facing = mCameraView.getFacing();
                    mCameraView.setFacing(facing == CameraView.FACING_FRONT ?
                            CameraView.FACING_BACK : CameraView.FACING_FRONT);
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onAspectRatioSelected(@NonNull AspectRatio ratio) {
        if (mCameraView != null) {
            Toast.makeText(this, ratio.toString(), Toast.LENGTH_SHORT).show();
            mCameraView.setAspectRatio(ratio);
        }
    }

    private Handler getBackgroundHandler() {
        if (mBackgroundHandler == null) {
            HandlerThread thread = new HandlerThread("background");
            thread.start();
            mBackgroundHandler = new Handler(thread.getLooper());
        }
        return mBackgroundHandler;
    }

    private CameraView.Callback mCallback
            = new CameraView.Callback() {
        @Override
        public void onCameraOpened(CameraView cameraView) {
//            String filename = "/DCIM/Camera/" + System.currentTimeMillis() + ".mp4";
//            String path = Environment.getExternalStorageDirectory().getPath() + filename;
            File file = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "picture.mp4");

            FFmpegBridge.initMediaRecorder(file.getAbsolutePath(), 1280, 1280, 1280, 1280,
                    25, 5760000, true, 40000, 44100);
            FFmpegBridge.startRecord();
            mRecording = true;
        }

        @Override
        public void onCameraClosed(CameraView cameraView) {
            Log.d(TAG, "onCameraClosed");
        }

        @Override
        public void onPictureTaken(final CameraView cameraView, final byte[] data) {

            Toast.makeText(cameraView.getContext(), R.string.picture_taken, Toast.LENGTH_SHORT)
                    .show();
            getBackgroundHandler().post(new Runnable() {
                @Override
                public void run() {


//                    ///mnt/sdcard/Android/data/com.cnr.voicetv/files/Pictures \\私有文件
//                    Log.d(TAG, "onPreviewFrame: ---->"+data);
//                    File file = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES),
//                            "picture.yvu");
//                    OutputStream os = null;
//                    try {
//                        os = new FileOutputStream(file);
//                        os.write(data);
//                        os.close();
//                    } catch (IOException e) {
//                        Log.w(TAG, "Cannot write to " + file, e);
//                    } finally {
//                        if (os != null) {
//                            try {
//                                os.close();
//                            } catch (IOException e) {
//                                // Ignore
//                            }
//                        }
//                    }
//                    FFmpegBridge.encodeFrame2H264(data);
//                    Log.i("TAG","-->"+FFmpegBridge.stringFromFFmpeg());
//                    try {
//                        H264TrackImpl h264Track = new H264TrackImpl(new FileDataSourceImpl(file.getAbsoluteFile()));
//                        Movie movie = new Movie();
//                        movie.addTrack(h264Track);
//                        Container mp4file = new DefaultMp4Builder().build(movie);
//                        FileChannel fc = new FileOutputStream(new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES),"output.mp4")).getChannel();
//                        mp4file.writeContainer(fc);
//                        fc.close();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
                    if (null != mVideoStreamTask) {
                        switch(mVideoStreamTask.getStatus()){
                            case RUNNING:
                                return;

                            case PENDING:
                                mVideoStreamTask.cancel(false);
                                break;
                        }
                    }
                    mVideoStreamTask = new VideoStreamTask(data);
                    mVideoStreamTask.execute((Void)null);
                }
            });
        }

    };

    public static class ConfirmationDialogFragment extends DialogFragment {

        private static final String ARG_MESSAGE = "message";
        private static final String ARG_PERMISSIONS = "permissions";
        private static final String ARG_REQUEST_CODE = "request_code";
        private static final String ARG_NOT_GRANTED_MESSAGE = "not_granted_message";

        public static ConfirmationDialogFragment newInstance(@StringRes int message,
                                                             String[] permissions, int requestCode, @StringRes int notGrantedMessage) {
            ConfirmationDialogFragment fragment = new ConfirmationDialogFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_MESSAGE, message);
            args.putStringArray(ARG_PERMISSIONS, permissions);
            args.putInt(ARG_REQUEST_CODE, requestCode);
            args.putInt(ARG_NOT_GRANTED_MESSAGE, notGrantedMessage);
            fragment.setArguments(args);
            return fragment;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Bundle args = getArguments();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(args.getInt(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    String[] permissions = args.getStringArray(ARG_PERMISSIONS);
                                    if (permissions == null) {
                                        throw new IllegalArgumentException();
                                    }
                                    ActivityCompat.requestPermissions(getActivity(),
                                            permissions, args.getInt(ARG_REQUEST_CODE));
                                }
                            })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Toast.makeText(getActivity(),
                                            args.getInt(ARG_NOT_GRANTED_MESSAGE),
                                            Toast.LENGTH_SHORT).show();
                                }
                            })
                    .create();
        }

    }

    // ------------------------------------- 视频编码线程 -------------------------------------------
    private class VideoStreamTask extends AsyncTask<Void, Void, Void> {

        private byte[] mData;

        //构造函数
        VideoStreamTask(byte[] data){
            this.mData = data;
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (mData != null) {
                FFmpegBridge.encodeFrame2H264(mData);
            }
            return null;
        }
    }

    // ----------------------------------- 音频编码线程 ---------------------------------------------
    private class AudioStreamTask extends AsyncTask<Void, Void, Void> {

        private byte[] mData; // 音频数据
        private int mSize;

        //构造函数
        AudioStreamTask(byte[] data, int size) {
            mData = data;
            mSize = size;
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (mData != null) {
                FFmpegBridge.encodePCMFrame(mData, mSize);
            }
            return null;
        }
    }

    // ------------------------------------------ 音频录音线程 --------------------------------------
    public class AudioRecorder extends Thread {
        // 是否停止线程
        private boolean mStop = false;

        private AudioRecord mAudioRecord = null;
        /** 采样率 */
        private int mSampleRate = 44100;
        private IMediaRecorder mMediaRecorder;

        public AudioRecorder(IMediaRecorder mediaRecorder) {
            this.mMediaRecorder = mediaRecorder;
        }

        /** 设置采样率 */
        public void setSampleRate(int sampleRate) {
            this.mSampleRate = sampleRate;
        }

        @Override
        public void run() {
            if (mSampleRate != 8000 && mSampleRate != 16000 && mSampleRate != 22050
                    && mSampleRate != 44100) {
                mMediaRecorder.onAudioError(AUDIO_RECORD_ERROR_SAMPLERATE_NOT_SUPPORT,
                        "sampleRate not support.");
                return;
            }

            final int mMinBufferSize = AudioRecord.getMinBufferSize(mSampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

            if (AudioRecord.ERROR_BAD_VALUE == mMinBufferSize) {
                mMediaRecorder.onAudioError(AUDIO_RECORD_ERROR_GET_MIN_BUFFER_SIZE_NOT_SUPPORT,
                        "parameters are not supported by the hardware.");
                return;
            }

            mAudioRecord = new AudioRecord(android.media.MediaRecorder.AudioSource.MIC, mSampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, mMinBufferSize);
            if (null == mAudioRecord) {
                mMediaRecorder.onAudioError(AUDIO_RECORD_ERROR_CREATE_FAILED, "new AudioRecord failed.");
                return;
            }
            try {
                mAudioRecord.startRecording();
            } catch (IllegalStateException e) {
                mMediaRecorder.onAudioError(AUDIO_RECORD_ERROR_UNKNOWN, "startRecording failed.");
                return;
            }

            byte[] sampleBuffer = new byte[2048];

            try {
                while (!mStop) {
                    int result = mAudioRecord.read(sampleBuffer, 0, 2048);
                    if (result > 0) {
                        mMediaRecorder.receiveAudioData(sampleBuffer, result);
                    }
                }
            } catch (Exception e) {
                String message = "";
                if (e != null)
                    message = e.getMessage();
                mMediaRecorder.onAudioError(AUDIO_RECORD_ERROR_UNKNOWN, message);
            }

            mAudioRecord.release();
            mAudioRecord = null;
        }

        /**
         * 停止音频录制
         */
        public void stopRecord() {
            mStop = true;
        }
    }
    /**
     * 开始音频录制
     */
    private void startAudioRecord() {
        mAudioRecorder = new AudioRecorder(this);
        mAudioRecorder.start();
    }
    /**
     * 停止音频录制
     */
    private void stopAudioRecord() {
        if (mAudioRecorder != null){
            mAudioRecorder.stopRecord();
        }
    }
}
