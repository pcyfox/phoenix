package com.guoxiaoxing.phoenix.picker.ui.camera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.ViewCompat;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.guoxiaoxing.phoenix.R;
import com.guoxiaoxing.phoenix.core.common.PhoenixConstant;
import com.guoxiaoxing.phoenix.core.model.MediaEntity;
import com.guoxiaoxing.phoenix.core.model.MimeType;
import com.guoxiaoxing.phoenix.picker.rx.bus.ImagesObservable;
import com.guoxiaoxing.phoenix.picker.ui.BaseActivity;
import com.guoxiaoxing.phoenix.picker.ui.camera.config.CameraConfig;
import com.guoxiaoxing.phoenix.picker.ui.camera.config.model.MediaAction;
import com.guoxiaoxing.phoenix.picker.ui.camera.listener.CameraControlAdapter;
import com.guoxiaoxing.phoenix.picker.ui.camera.listener.CameraStateAdapter;
import com.guoxiaoxing.phoenix.picker.ui.camera.listener.CameraVideoRecordTextAdapter;
import com.guoxiaoxing.phoenix.picker.ui.camera.listener.ICameraFragment;
import com.guoxiaoxing.phoenix.picker.ui.camera.listener.OnCameraResultAdapter;
import com.guoxiaoxing.phoenix.picker.ui.camera.widget.CameraSettingsView;
import com.guoxiaoxing.phoenix.picker.ui.camera.widget.CameraSwitchView;
import com.guoxiaoxing.phoenix.picker.ui.camera.widget.FlashSwitchView;
import com.guoxiaoxing.phoenix.picker.ui.camera.widget.RecordButton;
import com.guoxiaoxing.phoenix.picker.ui.picker.PreviewActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CameraActivity extends BaseActivity implements View.OnClickListener {
    private static final String TAG_CAMERA_FRAGMENT = "CameraFragment";
    private static final String DIRECTORY_NAME = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath()
            + "/Camera";

    View mCameraLayout;
    RecordButton mRecordButton;
    TextView mRecordDurationText;
    TextView mRecordSizeText;
    private TextView tvTip;

    CameraSettingsView mSettingsView;
    FlashSwitchView mFlashSwitchView;
    CameraSwitchView mCameraSwitchView;
    private long recordingTimeSeconds;
    private boolean booleanCanRecordVideo = true;
    private boolean locked = false;//解决拍照按钮快速重复点击问题

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getWindow() != null) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        setContentView(R.layout.activity_camera);
        setupView();
        setupCameraFragment();
        booleanCanRecordVideo = option.isBooleanCanRecordVideo();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (booleanCanRecordVideo) {
            tvTip.setVisibility(View.VISIBLE);
        }else {
            tvTip.setVisibility(View.GONE);
        }
    }

    @Override
    public void onClick(View v) {
        final ICameraFragment cameraFragment = getCameraFragment();
        if (cameraFragment == null) {
            return;
        }
        if (v.getId() == R.id.flash_switch_view) {
            cameraFragment.toggleFlashMode();
        } else if (v.getId() == R.id.front_back_camera_switcher) {
            cameraFragment.switchCameraTypeFrontBack();
        } else if (v.getId() == R.id.settings_view) {
            cameraFragment.openSettingDialog();
        }
    }

    private void setupView() {
        mSettingsView = findViewById(R.id.settings_view);
        mFlashSwitchView = findViewById(R.id.flash_switch_view);
        mCameraSwitchView = findViewById(R.id.front_back_camera_switcher);
        mRecordButton = findViewById(R.id.record_button);
        mRecordDurationText = findViewById(R.id.record_duration_text);
        mRecordSizeText = findViewById(R.id.record_size_mb_text);
        mCameraLayout = findViewById(R.id.rl_camera_control);
        tvTip = findViewById(R.id.tv_tip);

        mSettingsView.setOnClickListener(this);
        mFlashSwitchView.setOnClickListener(this);
        mCameraSwitchView.setOnClickListener(this);
        mCameraLayout.setOnClickListener(this);
        mRecordButton.setTimeLimit(option.getRecordVideoTime() * 1000);
        mRecordButton.setRecordable(booleanCanRecordVideo);


        mRecordButton.setOnRecordButtonListener(new RecordButton.OnRecordButtonListener() {
            @Override
            public void onClick() {
                if(locked)return;
                tvTip.setVisibility(View.GONE);
                recordingTimeSeconds = 0;
                final ICameraFragment cameraFragment = getCameraFragment();
                cameraFragment.switchCaptureAction(MediaAction.ACTION_PHOTO);
                locked=true;
                cameraFragment.takePicture(DIRECTORY_NAME, "IMG_" + System.currentTimeMillis(),
                        new OnCameraResultAdapter() {
                            @Override
                            public void onPhotoTaken(byte[] bytes, String filePath) {
                                locked=false;
                                try {
                                    MediaScannerConnection.scanFile(CameraActivity.this, new String[]{filePath}, null,
                                            new MediaScannerConnection.OnScanCompletedListener() {
                                                @Override
                                                public void onScanCompleted(String path, Uri uri) {

                                                }
                                            });
                                } catch (Exception ignore) {
                                }

                                ArrayList<MediaEntity> mediaList = new ArrayList<>();
                                MediaEntity mediaEntity = MediaEntity.newBuilder()
                                        .localPath(filePath)
                                        .fileType(MimeType.ofImage())
                                        .mimeType(MimeType.createImageType(filePath))
                                        .build();
                                mediaList.add(mediaEntity);

                                ImagesObservable.Companion.getInstance().savePreviewMediaList(mediaList);
                                Intent intent = new Intent(CameraActivity.this, PreviewActivity.class);
                                intent.putParcelableArrayListExtra(PhoenixConstant.KEY_PICK_LIST, mediaList);
                                intent.putExtra(PhoenixConstant.KEY_PREVIEW_TYPE, PhoenixConstant.TYPE_PREIVEW_FROM_CAMERA);
                                startActivityForResult(intent, PhoenixConstant.REQUEST_CODE_PREVIEW);

                            }
                        }
                );
            }

            @Override
            public void onLongClickStart() {
                recordingTimeSeconds = 0;
                tvTip.setVisibility(View.GONE);
                final ICameraFragment cameraFragment = getCameraFragment();
                cameraFragment.switchCaptureAction(MediaAction.ACTION_VIDEO);
                cameraFragment.startRecordingVideo(DIRECTORY_NAME, "VID_" + System.currentTimeMillis());
            }

            @Override
            public void onLongClickEnd() {
                tvTip.setVisibility(View.VISIBLE);
                final ICameraFragment cameraFragment = getCameraFragment();
                cameraFragment.stopRecordingVideo(new OnCameraResultAdapter() {
                    @Override
                    public void onVideoRecorded(final String filePath) {
                        Log.d("onLongClickEnd", "filePath: " + filePath);
                        Log.d("onLongClickEnd", "recordingTimeSeconds: " + recordingTimeSeconds);
                        if (recordingTimeSeconds < 1) {
                            showToast("录制时间过短");
                            // deleteFile(new File(filePath));
                            Log.d("onLongClickEnd", "deleteFile: " + filePath);
                            return;
                        }
                        try {
                            MediaScannerConnection.scanFile(CameraActivity.this, new String[]{filePath}, null,
                                    new MediaScannerConnection.OnScanCompletedListener() {
                                        @Override
                                        public void onScanCompleted(String path, Uri uri) {
                                            ArrayList<MediaEntity> mediaList = new ArrayList<>();

                                            MediaEntity mediaEntity = MediaEntity.newBuilder()
                                                    .localPath(filePath)
                                                    .fileType(MimeType.ofVideo())
                                                    .mimeType(MimeType.createVideoType(filePath))
                                                    .build();
                                            mediaList.add(mediaEntity);

                                            ImagesObservable.Companion.getInstance().savePreviewMediaList(mediaList);
                                            Intent intent = new Intent(CameraActivity.this, PreviewActivity.class);
                                            intent.putParcelableArrayListExtra(PhoenixConstant.KEY_PICK_LIST, mediaList);
                                            intent.putExtra(PhoenixConstant.KEY_PREVIEW_TYPE, PhoenixConstant.TYPE_PREIVEW_FROM_CAMERA);
                                            startActivityForResult(intent, PhoenixConstant.REQUEST_CODE_PREVIEW);
                                            recordingTimeSeconds = 0;
                                        }
                                    });
                        } catch (Exception ignore) {
                        }

                    }
                });
                cameraFragment.switchCaptureAction(MediaAction.ACTION_PHOTO);
            }
        });
    }

    private static void deleteFile(File var0) {
        if (var0 != null) {
            if (var0.exists()) {
                if (!var0.isDirectory()) {
                    var0.delete();
                }
            }
        }
    }

    private void setupCameraFragment() {
        if (Build.VERSION.SDK_INT > 15) {
            final String[] permissions = {
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE};

            final List<String> permissionsToRequest = new ArrayList<>();
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission);
                }
            }
            if (!permissionsToRequest.isEmpty()) {
                ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(
                        new String[permissionsToRequest.size()]), PhoenixConstant.REQUEST_CODE_CAMERA_PERMISSIONS);
            } else addCameraFragment();
        } else {
            addCameraFragment();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length != 0) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            addCameraFragment();
        }
    }

    public void addCameraFragment() {
        mCameraLayout.setVisibility(View.VISIBLE);
        final CameraFragment cameraFragment = CameraFragment.newInstance(new CameraConfig.Builder()
                .setCamera(CameraConfig.CAMERA_FACE_REAR).build());
        Bundle bundle = new Bundle();
        bundle.putParcelable(PhoenixConstant.PHOENIX_OPTION, option);

        cameraFragment.setArguments(bundle);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, cameraFragment, TAG_CAMERA_FRAGMENT)
                .commitAllowingStateLoss();

        if (cameraFragment != null) {
            //cameraFragmentApi.setResultListener(new OnCameraResultListener() {
            //    @Override
            //    public void onVideoRecorded(String filePath) {
            //        Intent intent = CameraPreviewActivity.newIntentVideo(CameraFragmentMainActivity.this, filePath);
            //        startActivityForResult(intent, REQUEST_CODE_PREVIEW);
            //    }
//
            //    @Override
            //    public void onPictureTaken(byte[] bytes, String filePath) {
            //        Intent intent = CameraPreviewActivity.newIntentPhoto(CameraFragmentMainActivity.this, filePath);
            //        startActivityForResult(intent, REQUEST_CODE_PREVIEW);
            //    }
            //});

            cameraFragment.setStateListener(new CameraStateAdapter() {

                @Override
                public void onCurrentCameraBack() {
                    mCameraSwitchView.displayBackCamera();
                }

                @Override
                public void onCurrentCameraFront() {
                    mCameraSwitchView.displayFrontCamera();
                }

                @Override
                public void onFlashAuto() {
                    mFlashSwitchView.displayFlashAuto();
                }

                @Override
                public void onFlashOn() {
                    mFlashSwitchView.displayFlashOn();
                }

                @Override
                public void onFlashOff() {
                    mFlashSwitchView.displayFlashOff();
                }

                @Override
                public void onCameraSetupForPhoto() {
//                    mFlashSwitchView.setVisibility(View.VISIBLE);
                }

                @Override
                public void onCameraSetupForVideo() {
//                    mFlashSwitchView.setVisibility(View.GONE);
                }

                @Override
                public void shouldRotateControls(int degrees) {
                    ViewCompat.setRotation(mCameraSwitchView, degrees);
                    ViewCompat.setRotation(mFlashSwitchView, degrees);
                    ViewCompat.setRotation(mRecordDurationText, degrees);
                    ViewCompat.setRotation(mRecordSizeText, degrees);
                }

                @Override
                public void onRecordStateVideoReadyForRecord() {
                }

                @Override
                public void onRecordStateVideoInProgress() {
                }

                @Override
                public void onRecordStatePhoto() {
                }

                @Override
                public void onStopVideoRecord() {
                    //    mRecordSizeText.setVisibility(View.GONE);
                    mSettingsView.setVisibility(View.VISIBLE);
                    tvTip.setVisibility(View.VISIBLE);
                }

                @Override
                public void onStartVideoRecord(File outputFile) {
                }
            });

            cameraFragment.setControlsListener(new CameraControlAdapter() {
                @Override
                public void lockControls() {
                    mCameraSwitchView.setEnabled(false);
                    mRecordButton.setEnabled(false);
                    mSettingsView.setEnabled(false);
                    mFlashSwitchView.setEnabled(false);
                }

                @Override
                public void unLockControls() {
                    mCameraSwitchView.setEnabled(true);
                    mRecordButton.setEnabled(true);
                    mSettingsView.setEnabled(true);
                    mFlashSwitchView.setEnabled(true);
                }

                @Override
                public void allowCameraSwitching(boolean allow) {
                    mCameraSwitchView.setVisibility(allow ? View.VISIBLE : View.GONE);
                }

                @Override
                public void allowRecord(boolean allow) {
                    mRecordButton.setEnabled(allow);
                }

                @Override
                public void setMediaActionSwitchVisible(boolean visible) {
                }
            });


            cameraFragment.setTextListener(new CameraVideoRecordTextAdapter() {
                @Override
                public void setRecordSizeText(long size, String text) {
                    mRecordSizeText.setText(text);
                }

                @Override
                public void setRecordSizeTextVisible(boolean visible) {
                    mRecordSizeText.setVisibility(visible ? View.VISIBLE : View.GONE);
                }

                @Override
                public void setRecordDurationText(String text, long recordingTimeSeconds) {
                    CameraActivity.this.recordingTimeSeconds = recordingTimeSeconds;
                    mRecordDurationText.setText(text);
                }

                @Override
                public void setRecordDurationTextVisible(boolean visible) {
                    mRecordDurationText.setVisibility(visible ? View.VISIBLE : View.GONE);
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (RESULT_OK != resultCode) return;
        tvTip.setVisibility(View.VISIBLE);
        switch (requestCode) {
            case PhoenixConstant.REQUEST_CODE_PREVIEW:
                onResult((List<MediaEntity>) data.getSerializableExtra(PhoenixConstant.KEY_PICK_LIST));
                break;
            default:
                break;
        }
    }

    private ICameraFragment getCameraFragment() {
        return (ICameraFragment) getSupportFragmentManager().findFragmentByTag(TAG_CAMERA_FRAGMENT);
    }
}
