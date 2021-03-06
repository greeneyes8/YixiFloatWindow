package com.yixi.window.view;

import java.util.List;

import com.yixi.window.R;
import com.yixi.window.data.IMediaData;
import com.yixi.window.utils.MediaUtils;
import com.yixi.window.view.FloatMusicView.MusicObserver;
import com.yixi.window.view.FloatWindowBigView2.ActionCallBack;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class FloatMovieView extends RelativeLayout implements
        OnSeekBarChangeListener, OnClickListener, ActionCallBack,
        OnCompletionListener, OnPreparedListener {
    private static final int REFRESH_PROGRESS_EVENT = 0x0010;
    private boolean mIsHaveData = false;
    private View mLayoutView;

    public ImageButton mBtnPlay;
    public ImageButton mBtnPause;
    public ImageButton mBtnPlayNext;
    public ImageButton mBtnPlayPre;
    public TextView mPlaySongTextView;
    public VideoView mVideoView;
    public SeekBar mPlayProgress;
    public TextView mcurtimeTextView;
    public TextView mtotaltimeTextView;
    private int mCurrentPos;
    private Context mContext;
    private List<IMediaData> mVideoList;
    private Handler mHandler;
    private boolean isPaused = true;
    private boolean isOnline = false;
    private SharedPreferences mSharedpreferences;
    private static final String PREFERENCE_NAME = "save_info";
    private static final String PREFERENCE_POSITION = "video_pos";
    private static final String PREFERENCE_PROGRESS = "video_progress";
    private int mProgress = 0;
    private AudioManager mAudioManager;

    private BroadcastReceiver mPhoneReceiver = new BroadcastReceiver () {

        @Override
        public void onReceive(Context context, Intent action) {
            TelephonyManager tm = (TelephonyManager) context 
                    .getSystemService(Service.TELEPHONY_SERVICE);
            if (tm.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
                isPaused = false;
            } else {
                isPaused = true;
            }
            showPlay(isPaused);
        }
        
    };

    private  OnAudioFocusChangeListener mAudioFocusChangeListener = new OnAudioFocusChangeListener() {
        
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
                isPaused = true;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (mVideoView.isPlaying()) {
                    isPaused = true;
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                isPaused = false;
                break;
            default:
                break;
            }
            showPlay(isPaused);
        }
    };
    public FloatMovieView(Context context) {
        super(context);
    }

    public FloatMovieView(Context context, AttributeSet ats) {
        super(context, ats);
        mContext = context;
        mCurrentPos = 0;
        LayoutInflater inflater = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mLayoutView = inflater.inflate(R.layout.movie_player, this, true);
        initView();
        init();
    }

    private void init() {
        mAudioManager = (AudioManager) mContext.getSystemService(mContext.AUDIO_SERVICE);
        mAudioManager.requestAudioFocus(mAudioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        intentFilter.setPriority(Integer.MAX_VALUE);
        mContext.registerReceiver(mPhoneReceiver, intentFilter);
        mVideoList = MediaUtils.getVideoFileList(mContext);
        mIsHaveData = (mVideoList.size() > 0);
        if (mIsHaveData) {
            mSharedpreferences = mContext.getSharedPreferences(PREFERENCE_NAME, mContext.MODE_PRIVATE);
            mCurrentPos = mSharedpreferences.getInt(PREFERENCE_POSITION, 0);
            int progress = mSharedpreferences.getInt(PREFERENCE_PROGRESS, 0);
            if (mCurrentPos > mVideoList.size()) {
                mCurrentPos = 0;
            }
            mVideoView.setVideoPath(mVideoList.get(mCurrentPos).mMediaPath);
            if (progress == 0) {
                mVideoView.setBackground(MediaUtils.getVideoThumbnail(mVideoList.get(mCurrentPos).mMediaId, mContext));
            } else {
                mVideoView.setBackground(null);
                mVideoView.seekTo(progress);
            }
            showData();
        } else {
            showNoData();
        }
      
        showPlay(isPaused);
        mHandler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                // TODO Auto-generated method stub

                switch (msg.what) {
                case REFRESH_PROGRESS_EVENT:
                    setPlayInfo();
                    break;
                default:

                    break;
                }
            }

        };
        registerVideoContentObserver();
    }

    private void initView() {
        mBtnPlay = (ImageButton) mLayoutView.findViewById(R.id.buttonPlay);
        mBtnPause = (ImageButton) mLayoutView.findViewById(R.id.buttonPause);
        mBtnPlayPre = (ImageButton) mLayoutView
                .findViewById(R.id.buttonPlayPre);
        mBtnPlayNext = (ImageButton) mLayoutView
                .findViewById(R.id.buttonPlayNext);
        mBtnPlay.setOnClickListener(this);
        mBtnPause.setOnClickListener(this);
        mBtnPlayPre.setOnClickListener(this);
        mBtnPlayNext.setOnClickListener(this);

        mPlaySongTextView = (TextView) mLayoutView
                .findViewById(R.id.movie_title);
        mcurtimeTextView = (TextView) mLayoutView
                .findViewById(R.id.textViewCurTime);
        mtotaltimeTextView = (TextView) mLayoutView
                .findViewById(R.id.textViewTotalTime);

        mPlayProgress = (SeekBar) mLayoutView.findViewById(R.id.seekBar);
        mPlayProgress.setOnSeekBarChangeListener(this);
        mVideoView = (VideoView) findViewById(R.id.movie_layout);
        mVideoView.setOnCompletionListener(this);
        mVideoView.setOnPreparedListener(this);
    }

    @Override
    public void onProgressChanged(SeekBar seekbar, int progress,
            boolean fromUser) {
        mProgress = progress;
        
        if (fromUser) {
            if (!isOnline) {
                mVideoView.seekTo(progress);
            }

        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onStopTrackingTouch(SeekBar arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onClick(View view) {
        // TODO Auto-generated method stub
        switch (view.getId()) {
        case R.id.buttonPlay:
        case R.id.buttonPause:
            rePlay();
            break;
        case R.id.buttonPlayPre:
            playPre();
            break;
        case R.id.buttonPlayNext:
            playNext();
            break;
        default:
            break;
        }
    }

    public void setPlayInfo() {
        int i = mVideoView.getCurrentPosition();
        mPlayProgress.setProgress(i);

        if (isOnline) {
            int j = mVideoView.getBufferPercentage();
            mPlayProgress
                    .setSecondaryProgress(j * mPlayProgress.getMax() / 100);
        } else {
            mPlayProgress.setSecondaryProgress(0);
        }

        i /= 1000;
        int minute = i / 60;
        int hour = minute / 60;
        int second = i % 60;
        minute %= 60;
        mcurtimeTextView.setText(String.format("%02d:%02d:%02d", hour, minute,
                second));
        mHandler.sendEmptyMessageDelayed(REFRESH_PROGRESS_EVENT, 100);

    }

    public void showNoData() {
        mVideoView.setVisibility(View.GONE);
        ((TextView) mLayoutView.findViewById(R.id.no_video)).setVisibility(View.VISIBLE);
    }


    public void showData() {
        mVideoView.setVisibility(View.VISIBLE);
        ((TextView) mLayoutView.findViewById(R.id.no_video)).setVisibility(View.GONE);
    }

    public void rePlay() {
        isPaused = !isPaused;
        showPlay(isPaused);
    }

    public void playPre() {
        isOnline = false;
        if (!mIsHaveData) {
            showNoData();
        } else {
            if (--mCurrentPos < 0) {
                mCurrentPos = mVideoList.size() - 1;
            }
            mVideoView.setVideoPath(mVideoList.get(mCurrentPos).mMediaPath);
            showData();
            showPlay(false);
        }

    }

    public void playNext() {
        isOnline = false;
        if (!mIsHaveData) {
            showNoData();
        } else {
            if (++mCurrentPos >= mVideoList.size()) {
                mCurrentPos = 0;
            }
            mVideoView.setVideoPath(mVideoList.get(mCurrentPos).mMediaPath);
            showData();
            showPlay(false);
        }
    } 

    @Override
    public void doAction() {
        mVideoView.stopPlayback();
        mContext.unregisterReceiver(mPhoneReceiver);
        mHandler.removeMessages(REFRESH_PROGRESS_EVENT);
        mSharedpreferences = mContext.getSharedPreferences(PREFERENCE_NAME, mContext.MODE_PRIVATE);
        SharedPreferences.Editor editor = mSharedpreferences.edit();
        editor.putInt(PREFERENCE_POSITION, mCurrentPos);
        editor.putInt(PREFERENCE_PROGRESS, mProgress);
        editor.commit();
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        playNext();
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {

        int duration = mVideoView.getDuration();
        mPlayProgress.setMax(duration);
        duration /= 1000;
        int minute = duration / 60;
        int hour = minute / 60;
        int second = duration % 60;
        minute %= 60;
        mtotaltimeTextView.setText(String.format("%02d:%02d:%02d", hour,
                minute, second));
        mPlaySongTextView.setText(mVideoList.get(mCurrentPos).mMediaName);
        mHandler.sendEmptyMessage(REFRESH_PROGRESS_EVENT);
    }


    public void showPlay(boolean flag) {
        if (mIsHaveData) {
            if (flag) {
                mBtnPlay.setVisibility(View.VISIBLE);
                mBtnPause.setVisibility(View.GONE);
                mVideoView.pause();
                isPaused = true;
            } else {
                mBtnPlay.setVisibility(View.GONE);
                mBtnPause.setVisibility(View.VISIBLE);
                mVideoView.setBackground(null);
                mAudioManager.requestAudioFocus(mAudioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                mVideoView.start();
                isPaused = false;
            }
        }

    }

    public void registerVideoContentObserver() {
        VideoObserver videoContent = new VideoObserver(mContext, new Handler());
        mContext.getContentResolver()
                .registerContentObserver(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true,
                        videoContent);
    }

    class VideoObserver extends ContentObserver {

        private Context mContext;
        public VideoObserver(Context context, Handler handler) {
            super(handler);
            mContext = context;
        }
        @Override
        public void onChange(boolean selfChange) {
            // TODO Auto-generated method stub
            super.onChange(selfChange);
            Log.d("apple", ">>>>>>>>>>>>>>>>>>onChange>>> ");
            boolean isFlag = mIsHaveData;
            mVideoList = MediaUtils.getVideoFileList(mContext);
            mIsHaveData = mVideoList.size() > 0;
            if (isFlag != mIsHaveData) {
                if (mIsHaveData) {
                    showData();
                    mVideoView.setVideoPath(mVideoList.get(0).mMediaPath);
                    mVideoView.setBackground(MediaUtils.getVideoThumbnail(mVideoList.get(0).mMediaId, mContext));
                    isPaused = true;
                    showPlay(isPaused);
                } else {
                    showNoData();
                }
            }
        }
    }
}
