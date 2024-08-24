package com.dictaphone.twenty.four;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.os.PowerManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.dictaphone.twenty.four.Rec_list_adapter;
import com.dictaphone.twenty.four.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.io.File;
import java.io.IOException;


public class RecordListFragment extends Fragment implements Rec_list_adapter.onItemList_click {

    private ConstraintLayout audio_playersheet;
    private BottomSheetBehavior bottomSheetBehavior;
    private RecyclerView record_list;
    private File[] allFiles;
    private Rec_list_adapter rec_list_adapter;

    private MediaPlayer mediaPlayer = null;
    private boolean isPlaying = false;

    private File file_toPlay;
    private ImageButton play_btn;
    private ImageButton play_prev_btn;
    private ImageButton play_forw_btn;
    private TextView player_file_name;
    private TextView player_title;
    private SeekBar seekBar;
    private Handler seekbarHandler;
    private Runnable updateseekbar;

    private boolean isScreenReceiverRegistered = false;
    private boolean isSeekBarReceiverRegistered = false;
    private boolean isMediaPlayerPreparedReceiverRegistered = false;
    private boolean isMediaPlayerCompletedReceiverRegistered = false;

    private PowerManager.WakeLock wakeLock;
    public RecordListFragment() {
        // Required empty public constructor
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_record_list, container, false);
    }

    private BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                // Обновляем UI после разблокировки
                if (mediaPlayer != null && isPlaying) {
                    updateUI();
                }
            }
        }
    };
    private void updateUI() {
        seekBar.setMax(mediaPlayer.getDuration());
        seekBar.setProgress(mediaPlayer.getCurrentPosition());
        player_file_name.setText(file_toPlay.getName());
        player_title.setText("Playing");
        play_btn.setImageDrawable(getActivity().getResources().getDrawable(R.drawable.player_pause_btn, null));
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        PowerManager powerManager = (PowerManager) getActivity().getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MediaPlayerWakelock");
        audio_playersheet = view.findViewById(R.id.playersheet);
        bottomSheetBehavior = BottomSheetBehavior.from(audio_playersheet);
        record_list = view.findViewById(R.id.record_recycler_list);

        play_btn = view.findViewById(R.id.play_btn);
        play_prev_btn = view.findViewById(R.id.play_back_btn);
        play_forw_btn = view.findViewById(R.id.play_forward_btn);
        player_file_name = view.findViewById(R.id.player_file_name);
        player_title = view.findViewById(R.id.player_title);
        seekBar = view.findViewById(R.id.seekbar_player);

        IntentFilter filter = new IntentFilter("ACTION_UPDATE_SEEKBAR");
        getActivity().registerReceiver(seekBarReceiver, filter);
        isSeekBarReceiverRegistered = true;

        IntentFilter completedFilter = new IntentFilter("MEDIA_PLAYER_COMPLETED");
        getActivity().registerReceiver(mediaPlayerCompletedReceiver, completedFilter);
        isMediaPlayerCompletedReceiverRegistered = true;

        IntentFilter preparedFilter = new IntentFilter("MEDIA_PLAYER_PREPARED");
        getActivity().registerReceiver(mediaPlayerPreparedReceiver, preparedFilter);
        isMediaPlayerPreparedReceiverRegistered = true;

        IntentFilter screenFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        getActivity().registerReceiver(screenReceiver, screenFilter);
        isScreenReceiverRegistered = true;

        String rec_path_files = getActivity().getExternalFilesDir("/").getAbsolutePath();
        File dir = new File(rec_path_files);
        allFiles = dir.listFiles();
        // Инициализация seekbarHandler
        seekbarHandler = new Handler();
        rec_list_adapter = new Rec_list_adapter(allFiles,this);

        record_list.setHasFixedSize(true);
        record_list.setLayoutManager(new LinearLayoutManager(getContext()));
        record_list.setAdapter(rec_list_adapter);


        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if(newState == BottomSheetBehavior.STATE_HIDDEN)
                {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {

            }
        });

        play_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(isPlaying)
                {
                    pauseAudio();
                }
                else {
                    if(file_toPlay!= null)
                    {
                        resumeAudio();
                    }
                }
            }
        });

        play_prev_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent serviceIntent = new Intent(getContext(), AudioPlaybackService.class);
                serviceIntent.setAction("REWIND_AUDIO");
                serviceIntent.putExtra("REWIND_AMOUNT", 1000); // Rewind by 1 second
                getContext().startService(serviceIntent);
            }
        });

        play_forw_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent serviceIntent = new Intent(getContext(), AudioPlaybackService.class);
                serviceIntent.setAction("FORWARD_AUDIO");
                serviceIntent.putExtra("FORWARD_AMOUNT", 1000); // Forward by 1 second
                getContext().startService(serviceIntent);
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    Intent serviceIntent = new Intent(getContext(), AudioPlaybackService.class);
                    serviceIntent.setAction("SEEK_TO");
                    serviceIntent.putExtra("SEEK_POSITION", progress);
                    getContext().startService(serviceIntent);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Останавливаем обновление SeekBar при начале перемотки
                seekbarHandler.removeCallbacks(updateseekbar);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Логика уже включена в onProgressChanged, так что тут ничего не нужно добавлять
            }
        });

    }
    private BroadcastReceiver seekBarReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("ACTION_UPDATE_SEEKBAR".equals(intent.getAction())) {
                int currentPosition = intent.getIntExtra("CURRENT_POSITION", 0);
                seekBar.setProgress(currentPosition); // Обновляем SeekBar
            }
        }
    };




    @Override
    public void onClick_Listener(File file, int position)
    {
        file_toPlay = file;
        if(isPlaying)
        {
            stopAudio();
            playAudio(file_toPlay);
        }
        else
        {
            playAudio(file_toPlay);
        }
    }

    private void stopAudio() {
        Intent serviceIntent = new Intent(getContext(), AudioPlaybackService.class);
        getContext().stopService(serviceIntent);

        play_btn.setImageDrawable(getActivity().getResources().getDrawable(R.drawable.player_play_btn, null));
        player_title.setText("Stopped");
        isPlaying = false;
    }

    // Update playAudio to use the Service correctly
    private void playAudio(File file_toPlay) {
        Intent serviceIntent = new Intent(getContext(), AudioPlaybackService.class);
        serviceIntent.putExtra("FILE_PATH", file_toPlay.getAbsolutePath());
        ContextCompat.startForegroundService(getContext(), serviceIntent);

        // Update the UI to show that playback has started
        play_btn.setImageDrawable(getActivity().getResources().getDrawable(R.drawable.player_pause_btn, null));
        player_file_name.setText(file_toPlay.getName());
        player_title.setText("Playing");
        isPlaying = true;

        // Register a receiver to update the UI when the MediaPlayer is prepared
        IntentFilter filter = new IntentFilter("MEDIA_PLAYER_PREPARED");
        getActivity().registerReceiver(mediaPlayerPreparedReceiver, filter);
    }

    private BroadcastReceiver mediaPlayerPreparedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("MEDIA_PLAYER_PREPARED".equals(intent.getAction())) {
                int duration = intent.getIntExtra("DURATION", 0);
                seekBar.setMax(duration);

                // Start SeekBar update
                updateRunnable();
                seekbarHandler.postDelayed(updateseekbar, 0);
            }
        }
    };

    // Добавьте новый BroadcastReceiver для обработки завершения воспроизведения
    private BroadcastReceiver mediaPlayerCompletedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("MEDIA_PLAYER_COMPLETED".equals(intent.getAction())) {
                // Сбрасываем UI, когда аудио завершает воспроизведение
                play_btn.setImageDrawable(getActivity().getResources().getDrawable(R.drawable.player_play_btn, null));
                seekBar.setProgress(0);  // Сбрасываем SeekBar в начало
                player_title.setText("Completed");

                isPlaying = false;
            }
        }
    };

    private void updateRunnable() {
        updateseekbar = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && isPlaying) {
                    seekBar.setProgress(mediaPlayer.getCurrentPosition());
                    seekbarHandler.postDelayed(this, 500); // Обновляем каждые 500 мс
                }
            }
        };
    }

    // Modify pauseAudio to pause the service's MediaPlayer instead of stopping the service
    private void pauseAudio() {
        Intent serviceIntent = new Intent(getContext(), AudioPlaybackService.class);
        serviceIntent.setAction("PAUSE_AUDIO");
        getContext().startService(serviceIntent);

        play_btn.setImageDrawable(getActivity().getResources().getDrawable(R.drawable.player_play_btn, null));
        isPlaying = false;
        seekbarHandler.removeCallbacks(updateseekbar); // Остановка обновления SeekBar
    }


    private void resumeAudio() {
        Intent serviceIntent = new Intent(getContext(), AudioPlaybackService.class);
        serviceIntent.setAction("RESUME_AUDIO");
        getContext().startService(serviceIntent);

        play_btn.setImageDrawable(getActivity().getResources().getDrawable(R.drawable.player_pause_btn, null));
        isPlaying = true;

        // Перезапуск обновления SeekBar после возобновления воспроизведения
        updateRunnable();
        seekbarHandler.postDelayed(updateseekbar, 0);
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Остановка воспроизведения и освобождение ресурсов
        Intent stopServiceIntent = new Intent(getContext(), AudioPlaybackService.class);
        stopServiceIntent.setAction("STOP_AUDIO");
        getContext().startService(stopServiceIntent);

        if (isScreenReceiverRegistered) {
            getActivity().unregisterReceiver(screenReceiver);
            isScreenReceiverRegistered = false;
        }
        if (isSeekBarReceiverRegistered) {
            getActivity().unregisterReceiver(seekBarReceiver);
            isSeekBarReceiverRegistered = false;
        }
        if (isMediaPlayerPreparedReceiverRegistered) {
            getActivity().unregisterReceiver(mediaPlayerPreparedReceiver);
            isMediaPlayerPreparedReceiverRegistered = false;
        }
        if (isMediaPlayerCompletedReceiverRegistered) {
            getActivity().unregisterReceiver(mediaPlayerCompletedReceiver);
            isMediaPlayerCompletedReceiverRegistered = false;
        }
    }
}