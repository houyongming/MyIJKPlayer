package com.linglongtech.ijkplayer.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.v4.view.MotionEventCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.linglongtech.ijkplayer.controller.IjkMediaController;
import com.linglongtech.ijkplayer.utils.ConstantsUrl;
import com.linglongtech.ijkplayer.utils.StringUtil;

import java.util.ArrayList;
import java.util.List;

import com.linglongtech.ijklib.entity.VideoInfo;
import com.linglongtech.ijklib.utils.Constants;
import com.linglongtech.ijklib.playView.IjkPlayView;
import com.linglongtech.ijkplayer.R;

import com.linglongtech.ijkplayer.utils.IjkLog;
import com.linglongtech.ijkplayer.utils.NetWorkUtils;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

import static com.linglongtech.ijklib.utils.PlayUtils.formatedSpeed;

//        IMediaPlayer.OnPreparedListener,
//        IMediaPlayer.OnSeekCompleteListener,
//        IMediaPlayer.OnCompletionListener,
//        IMediaPlayer.OnErrorListener,
//        IMediaPlayer.OnVideoSizeChangedListener,

public class PlayerActivity extends AppCompatActivity implements View.OnClickListener,
        IMediaPlayer.OnBufferingUpdateListener,
        IMediaPlayer.OnInfoListener,
        IjkPlayView.OnPlayListener{

    public static final String TAG = PlayerActivity.class.getSimpleName();

    // 目标进度
    private long mTargetPosition = Constants.INVALID_VALUE;
    // 当前音量
    private int mCurVolume = Constants.INVALID_VALUE;
    // 当前亮度
    private float mCurBrightness = Constants.INVALID_VALUE;
    // 音量控制
    private AudioManager mAudioManager;
    // 手势控制
    private GestureDetector mGestureDetector;
    // 最大音量
    private int mMaxVolume;

    private FrameLayout mFullLayout;
    // 触摸信息布局
    private FrameLayout mFlTouchLayout;
    // 音量
    private TextView mTvVolume;
    // 亮度
    private TextView mTvBrightness;
    // 快进
    private TextView mTvFastForward;
    // 快退
    private TextView mTvFastrewind;

    private TextView speedTv;


    private View mFlReload;
    // 重试
    private TextView mTvReload;

    //当前视频path的索引
    private int index = 0;
    //当前视频path
    private VideoInfo mVideo;

    private IjkPlayView mPlayView;
    private RelativeLayout mLoadingLay;
    private IjkMediaController mMediaController;
    private List<VideoInfo> mVideoInfos = new ArrayList<>();

    // 网络广播
    private NetBroadcastReceiver mNetReceiver;

    // 异常中断时的播放进度
    private int mInterruptPosition;
    private boolean mIsReady = false;

    private boolean mIsNetConnected;

    // 尝试重连消息
    private static final int MSG_TRY_RELOAD = 10001;
    // 更新进度消息
//    private static final int MSG_UPDATE_SEEK = 10002;
    private static final int MSG_UPDATE_SPEED = 10003;


    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
//            if (msg.what == MSG_UPDATE_SEEK) {
//                final int pos = _setProgress();
//                if (!mIsSeeking && mIsShowBar && mVideoView.isPlaying()) {
//                    // 这里会重复发送MSG，已达到实时更新 Seek 的效果
//                    msg = obtainMessage(MSG_UPDATE_SEEK);
//                    sendMessageDelayed(msg, 1000 - (pos % 1000));
//                }
//            } else
            if (msg.what == MSG_TRY_RELOAD) {
                if (mIsNetConnected) {
                    reload();
                }
                msg = obtainMessage(MSG_TRY_RELOAD);
                sendMessageDelayed(msg, 3000);
            } else if (msg.what == MSG_UPDATE_SPEED) {
                if (mPlayView != null) {
                    speedTv.setText(String.valueOf(formatedSpeed(mPlayView.getTcpSpeed(), 1000)));
                }

                mHandler.removeMessages(MSG_UPDATE_SPEED);
                mHandler.sendEmptyMessageDelayed(MSG_UPDATE_SPEED, 500);
            }
        }
    };

    /**
     * 重新开始
     */
    public void reload() {
        hideLoading();
        if (mIsReady) {
            // 确保网络正常时
            if (NetWorkUtils.isNetworkAvailable(this)) {
                mPlayView.reload();
                mPlayView.start();
                if (mInterruptPosition > 0) {
                    mPlayView.seekTo(mInterruptPosition);
                    mInterruptPosition = 0;
                }
            }
        } else {
            mPlayView.release(false);
            mPlayView.setRenderView();
            mPlayView.start();
        }
    }


    /**
     * 视频播放状态处理,包括IMediaPlayer状态  和  自定义 MediaPlayerParams状态
     *
     * @param status
     */
    private void switchStatus(final int status) {
        IjkLog.i("IjkPlayerView", "status " + status + "  mIsNeverPlay  " + mIsNeverPlay);
        switch (status) {
            case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                if (!mIsNeverPlay) {
                    showloding();
                }
                mHandler.removeMessages(MSG_TRY_RELOAD);
                break;
            case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
                hideLoading();
                break;
            case IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                break;

            case Constants.PlayStates.STATE_PREPARING:
                showloding();
                break;
            case Constants.PlayStates.STATE_PREPARED:
                mIsReady = true;
                hideLoading();
                if (mIsNeverPlay) {
                    mIsNeverPlay = false;
                }
                mHandler.sendEmptyMessageDelayed(MSG_UPDATE_SPEED, 500);
                break;
            case Constants.PlayStates.STATE_PLAYING:
                hideLoading();
                mHandler.removeMessages(MSG_TRY_RELOAD);
                break;
            case Constants.PlayStates.STATE_ERROR:
                mInterruptPosition = Math.max(mPlayView.getCurrentPosition(), mInterruptPosition);
                mPlayView.pause();
                if (mPlayView.getDuration() == -1 && !mIsReady) {
                    hideLoading();
                    mFlReload.setVisibility(View.VISIBLE);
                } else {
                    showloding();
                    mHandler.sendEmptyMessage(MSG_TRY_RELOAD);
                }
                break;

            case Constants.PlayStates.STATE_COMPLETED:
//                mPlayView.pause();
                if (mPlayView.getDuration() == -1 ||
                        (mPlayView.getCurrentPosition() + Constants.INTERVAL_TIME < mPlayView.getDuration())) {
                    mInterruptPosition = Math.max(mPlayView.getCurrentPosition(), mInterruptPosition);
                    Toast.makeText(this, "网络异常", Toast.LENGTH_SHORT).show();
                } else {
                    if (index >= mVideoInfos.size() - 1) {
                        finish();
                    } else {
                        showloding();
                        mIsNeverPlay = true;
                        index++;
                        IjkLog.i(TAG + " onCompletion  index new " + index);
                        mVideo = mVideoInfos.get(index);
                        mPlayView.setVideo(mVideo);
                    }
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        initReceiver();
//        mVideoInfos.add(new VideoInfo("视频1",ConstantsUrl.TEST_URL_1));
        mVideoInfos.add(new VideoInfo("视频2", ConstantsUrl.TEST_URL_2));
//        mVideoInfos.add(new VideoInfo("视频3",ConstantsUrl.TEST_URL_3));
        mVideo = mVideoInfos.get(index);
        initView();
    }

    private void initReceiver() {
        mNetReceiver = new NetBroadcastReceiver();
        //注册接受广播
        this.registerReceiver(mNetReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    private void initView() {
        mFullLayout = (FrameLayout) findViewById(R.id.full_layout);
        speedTv = (TextView) findViewById(R.id.tcp_speed);
        mMediaController = new IjkMediaController(this);
        mPlayView = (IjkPlayView) findViewById(R.id.video_ijkView);
        mLoadingLay = (RelativeLayout) this.findViewById(R.id.loading_lay);
        mPlayView.setVisibility(View.VISIBLE);
        mTvReload = (TextView) findViewById(R.id.tv_reload);
        mFlReload = findViewById(R.id.fl_reload_layout);
        mTvReload.setOnClickListener(this);

        initGesturesView();
        loadGuester();

        mPlayView.setMediaController(mMediaController);
        mPlayView.setInfoListener(this);

//        mPlayView.setOnPreparedListener(this);
//        mPlayView.setBufferingUpdateListener(this);
//        mPlayView.setSeekCompleteListener(this);
//        mPlayView.setOnCompletionListener(this);
//        mPlayView.setOnErrorListener(this);
//        mPlayView.setTimedTextListener(this);
//        mPlayView.setOnVideoSizeChangedListener(this);
//        mPlayView.setOnSurfaceListener(this);
        mPlayView.setPlayListener(this);
        mPlayView.setVideo(mVideoInfos.get(index));
        mPlayView.start();
    }

    private void initGesturesView() {
        mFlTouchLayout = (FrameLayout) findViewById(R.id.fl_touch_layout);
        mTvFastForward = (TextView) findViewById(R.id.tv_fast_forward);
        mTvFastrewind = (TextView) findViewById(R.id.tv_fast_rewind);
        mTvVolume = (TextView) findViewById(R.id.tv_volume);
        mTvBrightness = (TextView) findViewById(R.id.tv_brightness);
    }


    private void loadGuester() {
//        // 加载 IjkMediaPlayer 库
//        IjkMediaPlayer.loadLibrariesOnce(null);
//        IjkMediaPlayer.native_profileBegin("libijkplayer.so");
        // 声音
        mAudioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        mMaxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        // 亮度
        try {
            int e = Settings.System.getInt(this.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            float progress = 1.0F * (float) e / 255.0F;
            WindowManager.LayoutParams layout = this.getWindow().getAttributes();
            layout.screenBrightness = progress;
            this.getWindow().setAttributes(layout);
        } catch (Settings.SettingNotFoundException var7) {
            var7.printStackTrace();
        }

        mGestureDetector = new GestureDetector(this, mPlayerGestureListener);
//        mFullLayout.setClickable(true);
        mFullLayout.setOnTouchListener(mPlayerTouchListener);
//        mMediaController.setClickable(true);
        mMediaController.setOnTouchListener(mPlayerTouchListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mPlayView != null) {
            mPlayView.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mPlayView != null) {
            mPlayView.start();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.unregisterReceiver(mNetReceiver);
        mHandler.removeMessages(MSG_TRY_RELOAD);

        if (mPlayView != null) {
            mPlayView.release(true);
        }
        if (mMediaController != null) {
            mMediaController.release();
            mMediaController = null;
        }
        IjkMediaPlayer.native_profileEnd();
    }


//    @Override
//    public void onPrepared(IMediaPlayer iMediaPlayer) {
//        DebugLog.i(TAG + " onPrepared ");
//        hideLoading();
//        backdropIm.setImageBitmap(null);
//        if (mIsNeverPlay) {
//            mIsNeverPlay = false;
//        }
//    }

    @Override
    public void onBufferingUpdate(IMediaPlayer iMediaPlayer, int what) {
        IjkLog.i(TAG + " onBufferingUpdate " + what);
        if (what >= 100 || mPlayView.isPlaying()) {
            hideLoading();
        } else {
            if (what < 0) {
                showloding();
            } else {
                hideLoading();
            }
        }
    }

//    @Override
//    public void onCompletion(IMediaPlayer iMediaPlayer) {
//        DebugLog.i(TAG + " onCompletion  index old " + index);
//        if (index >= paths.size() - 1) {
//            backdropBitmap = null;
//            finish();
//        } else {
//            showloding();
//            mIsNeverPlay = true;
//            index++;
//            DebugLog.i(TAG + " onCompletion  index new " + index);
//            mVideoPath = paths.get(index);
//            backdropBitmap = BitmapUtil.getVideoThumb(mVideoPath);
//            backdropIm.setImageBitmap(backdropBitmap);
//            mPlayView.setVideoPath(mVideoPath);
//        }
//    }

//    @Override
//    public boolean onError(IMediaPlayer iMediaPlayer, int i, int i1) {
//        DebugLog.i(TAG + " onError " + i + "  " + i1);
//        return false;
//    }

    @Override
    public boolean onInfo(IMediaPlayer iMediaPlayer, int status, int extra) {
        IjkLog.i(TAG + " onInfo   status  " + status + "   extra  " + extra);
        switchStatus(status);
        return true;
    }

//    @Override
//    public void onSeekComplete(IMediaPlayer iMediaPlayer) {
//        DebugLog.i(TAG + " onSeekComplete ");
//
//    }
//
//    @Override
//    public void onVideoSizeChanged(IMediaPlayer iMediaPlayer, int i, int i1, int i2, int i3) {
//        DebugLog.i(TAG + " onVideoSizeChanged " + i + "  " + i1 + "  " + i2 + "  " + i3 + "  ");
//
//    }

    /**
     * 显示loading
     */
    private void showloding() {
        if (mLoadingLay != null) {
            IjkLog.i(TAG + "status showloding  mLoading != null");
            mLoadingLay.setVisibility(View.VISIBLE);
        }
        IjkLog.i(TAG + "status showloding ");
    }

    /**
     * 隐藏Loading
     */
    private void hideLoading() {
        if (mLoadingLay != null) {
            mLoadingLay.setVisibility(View.GONE);
        }
        IjkLog.i(TAG + "  status hideLoading ");
    }

    @Override
    public void backFinish() {
        this.finish();
    }



    /**
     * ============================ 触屏操作处理 ============================
     */
    // 进来还未播放
    private boolean mIsNeverPlay = true;

    /**
     * 手势监听
     */
    private GestureDetector.OnGestureListener mPlayerGestureListener = new GestureDetector.SimpleOnGestureListener() {
        // 是否是按下的标识，默认为其他动作，true为按下标识，false为其他动作
        private boolean isDownTouch;
        // 是否声音控制,默认为亮度控制，true为声音控制，false为亮度控制
        private boolean isVolume;
        // 是否横向滑动，默认为纵向滑动，true为横向滑动，false为纵向滑动
        private boolean isLandscape;

        @Override
        public boolean onDown(MotionEvent e) {
            isDownTouch = true;
            if (mMediaController.isShowing()) {
                mMediaController.removeFadeoutMessage();
            }
            return super.onDown(e);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (!mIsNeverPlay) {
                float mOldX = e1.getX(), mOldY = e1.getY();
                float deltaY = mOldY - e2.getY();
                float deltaX = mOldX - e2.getX();
                if (isDownTouch) {
                    // 判断左右或上下滑动
                    isLandscape = Math.abs(distanceX) >= Math.abs(distanceY);
                    // 判断是声音或亮度控制
                    isVolume = mOldX > getResources().getDisplayMetrics().widthPixels * 0.5f;
                    isDownTouch = false;
                }

                if (isLandscape) {
                    onProgressSlide(-deltaX / mPlayView.getWidth());
                } else {
                    float percent = deltaY / mPlayView.getHeight();
                    if (isVolume) {
                        onVolumeSlide(percent);
                    } else {
                        onBrightnessSlide(percent);
                    }
                }
            }
            return super.onScroll(e1, e2, distanceX, distanceY);
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            mPlayView.showOsd();
            return super.onSingleTapConfirmed(e);
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            // 如果未进行播放或从弹幕编辑状态返回则不执行双击操作
            if (mIsNeverPlay) {
                return true;
            }
            if (mPlayView.isPlaying()) {
                mPlayView.pause();
            } else {
                mPlayView.start();
            }
            mPlayView.showOsd();
            return true;
        }
    };

    /**
     * 快进或者快退滑动改变进度，这里处理触摸滑动不是拉动 SeekBar
     *
     * @param percent 拖拽百分比
     */
    private void onProgressSlide(float percent) {
        int position = mPlayView.getCurrentPosition();
        long duration = mPlayView.getDuration();
        // 单次拖拽最大时间差为100秒或播放时长的1/2
        long deltaMax = Math.min(100 * 1000, duration / 2);
        // 计算滑动时间
        long delta = (long) (deltaMax * percent);
        // 目标位置
        mTargetPosition = delta + position;
        if (mTargetPosition > duration) {
            mTargetPosition = duration;
        } else if (mTargetPosition <= 0) {
            mTargetPosition = 0;
        }
        int deltaTime = (int) ((mTargetPosition - position) / 1000);
        String desc;
        // 对比当前位置来显示快进或后退
        boolean isForward = true;
        if (mTargetPosition > position) {
//            desc = generateTime(mTargetPosition) + "/" + generateTime(duration) + "\n" + "+" + deltaTime + "秒";
            desc = StringUtil.stringForTime(mTargetPosition) + "/" + StringUtil.stringForTime(duration) + "\n" + "+" + deltaTime + "秒";
            isForward = true;
        } else {
            desc = StringUtil.stringForTime(mTargetPosition) + "/" + StringUtil.stringForTime(duration) + "\n" + deltaTime + "秒";
            isForward = false;
        }
        setFastForward(desc, isForward);
    }

    /**
     * 设置快进
     *
     * @param time
     */
    private void setFastForward(String time, boolean isForward) {
        if (mFlTouchLayout.getVisibility() == View.GONE) {
            mFlTouchLayout.setVisibility(View.VISIBLE);
        }
        if (isForward) {
            mTvFastrewind.setVisibility(View.GONE);
            if (mTvFastForward.getVisibility() == View.GONE) {
                mTvFastForward.setVisibility(View.VISIBLE);
            }
            mTvFastForward.setText(time);
        } else {
            mTvFastForward.setVisibility(View.GONE);
            if (mTvFastrewind.getVisibility() == View.GONE) {
                mTvFastrewind.setVisibility(View.VISIBLE);
            }
            mTvFastrewind.setText(time);
        }
    }

    /**
     * 设置声音控制显示
     *
     * @param volume
     */
    private void setVolumeInfo(int volume) {
        if (mFlTouchLayout.getVisibility() == View.GONE) {
            mFlTouchLayout.setVisibility(View.VISIBLE);
        }
        if (mTvVolume.getVisibility() == View.GONE) {
            mTvVolume.setVisibility(View.VISIBLE);
        }
        mTvVolume.setText((volume * 100 / mMaxVolume) + "%");
    }

    /**
     * 滑动改变声音大小
     *
     * @param percent
     */
    private void onVolumeSlide(float percent) {
        if (mCurVolume == Constants.INVALID_VALUE) {
            mCurVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (mCurVolume < 0) {
                mCurVolume = 0;
            }
        }
        int index = (int) (percent * mMaxVolume) + mCurVolume;
        if (index > mMaxVolume) {
            index = mMaxVolume;
        } else if (index < 0) {
            index = 0;
        }
        // 变更声音
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0);
        // 变更进度条
        setVolumeInfo(index);
    }

    /**
     * 滑动改变亮度大小
     *
     * @param percent
     */
    private void onBrightnessSlide(float percent) {
        if (mCurBrightness < 0) {
            mCurBrightness = this.getWindow().getAttributes().screenBrightness;
            if (mCurBrightness < 0.0f) {
                mCurBrightness = 0.5f;
            } else if (mCurBrightness < 0.01f) {
                mCurBrightness = 0.01f;
            }
        }
        WindowManager.LayoutParams attributes = this.getWindow().getAttributes();
        attributes.screenBrightness = mCurBrightness + percent;
        if (attributes.screenBrightness > 1.0f) {
            attributes.screenBrightness = 1.0f;
        } else if (attributes.screenBrightness < 0.01f) {
            attributes.screenBrightness = 0.01f;
        }
        setBrightnessInfo(attributes.screenBrightness);
        this.getWindow().setAttributes(attributes);
    }

    /**
     * 设置亮度控制显示
     *
     * @param brightness
     */
    private void setBrightnessInfo(float brightness) {
        if (mFlTouchLayout.getVisibility() == View.GONE) {
            mFlTouchLayout.setVisibility(View.VISIBLE);
        }
        if (mTvBrightness.getVisibility() == View.GONE) {
            mTvBrightness.setVisibility(View.VISIBLE);
        }
        mTvBrightness.setText(Math.ceil(brightness * 100) + "%");
    }

    /**
     * 触摸监听
     */
    private View.OnTouchListener mPlayerTouchListener = new View.OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (mGestureDetector.onTouchEvent(event)) {
                return true;
            }
            if (MotionEventCompat.getActionMasked(event) == MotionEvent.ACTION_UP) {
                endGesture();
            }
            return true;
        }
    };

    /**
     * 手势结束调用
     */
    private void endGesture() {
        if (mTargetPosition >= 0 && mTargetPosition != mPlayView.getCurrentPosition()) {
            // 更新视频播放进度
            mPlayView.seekTo((int) mTargetPosition);
//            mMediaController.setProgress((int) (mTargetPosition * mMediaController.MAX_VIDEO_SEEK / mPlayView.getDuration()));
            mTargetPosition = Constants.INVALID_VALUE;
        }
        // 隐藏触摸操作显示图像
        hideTouchView();
        if (mMediaController.isShowing()) {
            mMediaController.postFadeoutMessage();
        }
        mCurVolume = Constants.INVALID_VALUE;
        mCurBrightness = Constants.INVALID_VALUE;
    }

    /**
     * 隐藏触摸视图
     */
    private void hideTouchView() {
        if (mFlTouchLayout.getVisibility() == View.VISIBLE) {
            mTvFastrewind.setVisibility(View.GONE);
            mTvFastForward.setVisibility(View.GONE);
            mTvVolume.setVisibility(View.GONE);
            mTvBrightness.setVisibility(View.GONE);
            mFlTouchLayout.setVisibility(View.GONE);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.tv_reload:
                reload();
                break;
            default:
                break;
        }
    }

    public class NetBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            // 如果相等的话就说明网络状态发生了变化
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                mIsNetConnected = NetWorkUtils.isWifiConnected(PlayerActivity.this);
                mPlayView.reload();
                mPlayView.start();
            }
        }
    }
}
