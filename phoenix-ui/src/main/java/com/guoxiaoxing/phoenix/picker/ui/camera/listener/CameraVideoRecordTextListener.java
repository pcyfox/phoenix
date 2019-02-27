package com.guoxiaoxing.phoenix.picker.ui.camera.listener;

public interface CameraVideoRecordTextListener {
    void setRecordSizeText(long size, String text);

    void setRecordSizeTextVisible(boolean visible);

    void setRecordDurationText(String text, long recordingTimeSeconds);

    void setRecordDurationTextVisible(boolean visible);
}
