package com.dictaphone.twenty.four;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.widget.Chronometer;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;

public class AudioRecordService extends Service {
    private MediaRecorder mediaRecorder;
    private String recordFilePath;
    private PowerManager.WakeLock wakeLock;
    private Chronometer chronometer;

    @Override
    public void onCreate() {
        super.onCreate();
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::AudioRecordWakelock");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        recordFilePath = intent.getStringExtra("FILE_PATH");

        startForeground(1, createNotification());

        wakeLock.acquire();

        startRecording();

        return START_REDELIVER_INTENT; // Перезапуск сервиса с тем же Intent при необходимости
    }

    private void startRecording() {
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setOutputFile(recordFilePath);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }

        mediaRecorder.start();

        // Start chronometer
        chronometer = new Chronometer(this);
        chronometer.setBase(SystemClock.elapsedRealtime());
        chronometer.start();
    }

    private Notification createNotification() {
        NotificationChannel channel = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channel = new NotificationChannel("RecordServiceChannel", "Record Service Channel",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setSound(null, null);
        }
        NotificationManager manager = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager = getSystemService(NotificationManager.class);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(channel);
        }

        return new NotificationCompat.Builder(this, "RecordServiceChannel")
                .setContentTitle("Recording Audio")
                .setContentText("Recording in progress...")
                .setSmallIcon(R.drawable.player_header_icon)
                .setSound(null)  // Устанавливаем пустой звук
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRecording();
        wakeLock.release();
    }

    private void stopRecording() {
        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
        }

        if (chronometer != null) {
            chronometer.stop();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

