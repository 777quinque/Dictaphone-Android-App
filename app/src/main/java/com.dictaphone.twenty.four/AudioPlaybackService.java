package com.dictaphone.twenty.four;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;

public class AudioPlaybackService extends Service {
    private MediaPlayer mediaPlayer;
    private PowerManager.WakeLock wakeLock;
    private Handler seekbarUpdateHandler;
    private Runnable updateSeekbarPosition;

    private String currentFilePath = null;

    @Override
    public void onCreate() {
        super.onCreate();
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::AudioPlaybackWakelock");
        seekbarUpdateHandler = new Handler(); // Initialize here
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();

        if ("PAUSE_AUDIO".equals(action)) {
            pauseAudio();
        } else if ("RESUME_AUDIO".equals(action)) {
            resumeAudio();
        } else if ("SEEK_TO".equals(action)) {
            int seekPosition = intent.getIntExtra("SEEK_POSITION", 0);
            if (mediaPlayer != null) {
                mediaPlayer.seekTo(seekPosition);
                Intent updateIntent = new Intent("ACTION_UPDATE_SEEKBAR");
                updateIntent.putExtra("CURRENT_POSITION", seekPosition);
                sendBroadcast(updateIntent);
            }
        } else if ("FORWARD_AUDIO".equals(action)) {
            int forwardAmount = intent.getIntExtra("FORWARD_AMOUNT", 0);
            forwardAudio(forwardAmount);
        } else if ("REWIND_AUDIO".equals(action)) {
            int rewindAmount = intent.getIntExtra("REWIND_AMOUNT", 0);
            rewindAudio(rewindAmount);
        } else if ("STOP_AUDIO".equals(action)) {
            stopAudio();
        } else {
            String filePath = intent.getStringExtra("FILE_PATH");
            startForeground(2, createNotification(filePath));
            wakeLock.acquire();
            playAudio(filePath);
        }
        return START_STICKY;
    }

    private void stopAudio() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        stopForeground(true);
        stopSelf();
    }

    private void playAudio(String filePath) {
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
        } else {
            mediaPlayer.reset(); // Сбрасываем, если уже был и подготовим его заново
        }

        currentFilePath = filePath;

        try {
            mediaPlayer.setDataSource(filePath);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        mediaPlayer.setOnPreparedListener(mp -> {
            mp.start();
            Intent intent = new Intent("MEDIA_PLAYER_PREPARED");
            intent.putExtra("DURATION", mp.getDuration());
            sendBroadcast(intent); // Отправляем сообщение о подготовке
        });

        mediaPlayer.setOnCompletionListener(mp -> {
            Intent intent = new Intent("MEDIA_PLAYER_COMPLETED");
            sendBroadcast(intent);
            // Не останавливаем сервис, просто освобождаем ресурсы
        });

        updateSeekbarPosition();
    }

    private void pauseAudio() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            seekbarUpdateHandler.removeCallbacks(updateSeekbarPosition); // Останавливаем обновление SeekBar
        }
    }

    private void resumeAudio() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                return; // Уже воспроизводится
            }

            if (mediaPlayer.getCurrentPosition() >= mediaPlayer.getDuration()) {
                // Если воспроизведение завершилось, сбрасываем позицию
                mediaPlayer.seekTo(0);
            }
            mediaPlayer.start();
            updateSeekbarPosition(); // Перезапускаем обновление SeekBar
        } else if (currentFilePath != null) {
            // Если MediaPlayer не существует, создаем новый и запускаем воспроизведение
            playAudio(currentFilePath);
        }
    }

    private void rewindAudio(int rewindAmount) {
        if (mediaPlayer != null) {
            int newPosition = mediaPlayer.getCurrentPosition() - rewindAmount;
            if (newPosition < 0) {
                newPosition = 0;
            }
            mediaPlayer.seekTo(newPosition);
            updateSeekbarPosition();
        }
    }

    private void forwardAudio(int forwardAmount) {
        if (mediaPlayer != null) {
            int newPosition = mediaPlayer.getCurrentPosition() + forwardAmount;
            if (newPosition > mediaPlayer.getDuration()) {
                newPosition = mediaPlayer.getDuration();
            }
            mediaPlayer.seekTo(newPosition);
            updateSeekbarPosition();
        }
    }

    private Notification createNotification(String fileName) {
        NotificationChannel channel = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channel = new NotificationChannel("PlaybackServiceChannel", "Playback Service Channel",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setSound(null, null);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }

        return new NotificationCompat.Builder(this, "PlaybackServiceChannel")
                .setContentTitle("Playing Audio")
                .setContentText("Playing " + fileName)
                .setSmallIcon(R.drawable.player_header_icon)
                .setSound(null)  // Устанавливаем пустой звук
                .build();
    }

    private void updateSeekbarPosition() {
        updateSeekbarPosition = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null) {
                    try {
                        if (mediaPlayer.isPlaying()) {
                            int currentPosition = mediaPlayer.getCurrentPosition();
                            Intent updateIntent = new Intent("ACTION_UPDATE_SEEKBAR");
                            updateIntent.putExtra("CURRENT_POSITION", currentPosition);
                            sendBroadcast(updateIntent);
                            seekbarUpdateHandler.postDelayed(this, 500); // Continue updating
                        }
                    } catch (IllegalStateException e) {
                        // Handle the exception gracefully, e.g., log it
                        e.printStackTrace();
                    }
                }
            }
        };
        seekbarUpdateHandler.post(updateSeekbarPosition); // Start updating
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (seekbarUpdateHandler != null && updateSeekbarPosition != null) {
            seekbarUpdateHandler.removeCallbacks(updateSeekbarPosition);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

