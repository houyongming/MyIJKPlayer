package com.linglongtech.ijkplayer.controller;

import android.content.Context;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;

import com.linglongtech.ijkplayer.utils.StringUtil;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import com.linglongtech.ijklib.media.IRenderView;
import com.linglongtech.ijklib.utils.Constants;
import com.linglongtech.ijklib.playView.IMediaController;
import com.linglongtech.ijklib.playView.ICorePlayerControl;
import com.linglongtech.ijkplayer.R;
import com.linglongtech.ijkplayer.utils.AnimHelper;
import com.linglongtech.ijkplayer.utils.IjkLog;

public class IjkMediaController extends FrameLayout implements View.OnClickListener, IMediaController {

    private static final String TAG = IjkMediaController.class.getSimpleName();
    private ICorePlayerControl mPlayer;
    private Context mContext;
    private View mAnchor;
    private View mRoot;
    private WindowManager mWindowManager;
    private Window mWindow;
    private View mDecor;
    private WindowManager.LayoutParams mDecorLayoutParams;
    private TextView mEndTime, mCurrentTime;
    private boolean mShowing;
    // 进度条最大值
    public static final int MAX_VIDEO_SEEK = 1000;
    public static final int sDefaultTimeout = 5000;
    private static final int FADE_OUT = 1;
    private static final int SHOW_PROGRESS = 2;
    private ImageView mPauseButton;
    private TextView title;
    private TextView mTvRender;
    // 宽高比选项
    private TextView mTvSettings;
    private RadioGroup mRenderRadioOptions, mAspectRadioOptions;
    // 选项列表高度
    private int mRenderOptionsHeight, mAspectOptionsHeight;
    private SeekBar mProgress;
    private int position;
    private int duration;

    private final EventHandler mHandler = new EventHandler(this);

    public IjkMediaController(Context context, AttributeSet attrs) {
        super(context, attrs);
        mRoot = this;
        mContext = context;
    }

    @Override
    public void onFinishInflate() {
        if (mRoot != null)
            initControllerView(mRoot);
    }

    public IjkMediaController(Context context) {
        super(context);
        mContext = context;
        initFloatingWindowLayout();
        initFloatingWindow();
    }

    private void initFloatingWindow() {
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);

        Class<?> cls = null;
        try {
            cls = Class.forName("com.android.internal.policy.PhoneWindow");
            Constructor<?> localConstructor = cls.getConstructor(new Class[]{Context.class});
            mWindow = (Window) localConstructor.newInstance(new Object[]{mContext});
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }


//         mWindow = PolicyManager.makeNewWindow(mContext);
//        String POLICYMANAGER_CLASS_NAME = "com.android.internal.policy.PolicyManager";
//        try {
//            Class policyClass;
//            policyClass = Class.forName(POLICYMANAGER_CLASS_NAME);
//            Method meths[] = policyClass.getMethods();
//            Method makenewwindow = null;
//            // Method makenewwindow = policyClass.getMethod("makeNewWindow");
//            for (int i = 0; i < meths.length; i++) {
//                if (meths[i].getName().endsWith("makeNewWindow"))
//                    makenewwindow = meths[i];
//            }
//            Class[] paramTypes = makenewwindow.getParameterTypes();
//            if (paramTypes.length == 2) {
//                mWindow = (Window) makenewwindow.invoke(null, mContext, false);
//            } else {
//                mWindow = (Window) makenewwindow.invoke(null, mContext);
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        mWindow.setWindowManager(mWindowManager, null, null);
        mWindow.requestFeature(Window.FEATURE_NO_TITLE);
        mWindow.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        mDecor = mWindow.getDecorView();
        mDecor.setOnTouchListener(mTouchListener);
        mWindow.setContentView(this);
        mWindow.setBackgroundDrawableResource(android.R.color.transparent);

        // While the media controller is up, the volume control keys should
        // affect the media stream type
        mWindow.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        setFocusable(true);
        setFocusableInTouchMode(true);
        setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        requestFocus();
    }

    // Allocate and initialize the static parts of mDecorLayoutParams. Must
    // also call updateFloatingWindowLayout() to fill in the dynamic parts
    // (y and width) before mDecorLayoutParams can be used.
    private void initFloatingWindowLayout() {
        mDecorLayoutParams = new WindowManager.LayoutParams();
        WindowManager.LayoutParams p = mDecorLayoutParams;
        p.gravity = Gravity.TOP | Gravity.LEFT;
        p.height = LayoutParams.MATCH_PARENT;
        p.x = 0;
        p.format = PixelFormat.TRANSLUCENT;
        p.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
        p.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH | WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        p.token = null;
        p.windowAnimations = 0; // ;
    }

    // Update the dynamic parts of mDecorLayoutParams
    // Must be called with mAnchor != NULL.
    private void updateFloatingWindowLayout() {
        int[] anchorPos = new int[2];
        mAnchor.getLocationOnScreen(anchorPos);

        // we need to know the size of the controller so we can properly
        // position it
        // within its space
        mDecor.measure(MeasureSpec.makeMeasureSpec(mAnchor.getWidth(), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(mAnchor.getHeight(), MeasureSpec.AT_MOST));

        WindowManager.LayoutParams p = mDecorLayoutParams;
        p.width = mAnchor.getWidth();
        p.x = anchorPos[0] + (mAnchor.getWidth() - p.width) / 2;
        p.y = anchorPos[1] + mAnchor.getHeight() - mDecor.getMeasuredHeight();
    }

    // This is called whenever mAnchor's layout bound changes
    private OnLayoutChangeListener mLayoutChangeListener = new OnLayoutChangeListener() {

        public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop,
                                   int oldRight, int oldBottom) {
            updateFloatingWindowLayout();
            if (mShowing) {
                mWindowManager.updateViewLayout(mDecor, mDecorLayoutParams);
            }
        }
    };

    private OnTouchListener mTouchListener = new OnTouchListener() {

        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (mShowing) {
                    hide();
                }
            }
            return false;
        }
    };

    @Override
    public void setMediaPlayer(ICorePlayerControl player) {
        mPlayer = player;
        updatePausePlay();
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (mPauseButton != null) {
            mPauseButton.setEnabled(enabled);
        }
        if (mProgress != null) {
            mProgress.setEnabled(enabled);
        }
//        disableUnsupportedButtons();
        super.setEnabled(enabled);
    }

    /**
     * Set the view that acts as the anchor for the control view. This can for
     * example be a VideoView, or your Activity's main view. When VideoView
     * calls this method, it will use the VideoView's parent as the anchor.
     *
     * @param view The view to which to anchor the controller when it is visible.
     */
    @Override
    public void setAnchorView(View view) {
        if (mAnchor != null) {
            mAnchor.removeOnLayoutChangeListener(mLayoutChangeListener);
        }
        mAnchor = view;
        if (mAnchor != null) {
            mAnchor.addOnLayoutChangeListener(mLayoutChangeListener);
        }

        LayoutParams frameParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        removeAllViews();
        View v = makeControllerView();
        addView(v, frameParams);
    }


    /**
     * Create the view that holds the widgets that control playback. Derived
     * classes can override this to create their own.
     *
     * @return The controller view.
     * @hide This doesn't work as advertised
     */
    protected View makeControllerView() {
        LayoutInflater inflate = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflate.inflate(R.layout.player_ontroller_view, null);
        initControllerView(v);
        return v;
    }

    private void initControllerView(View v) {
        mPauseButton = (ImageView) v.findViewById(R.id.mediacontroller_player_pauseorplay);
        if (mPauseButton != null) {
            mPauseButton.requestFocus();
            mPauseButton.setOnClickListener(this);
        }

        title = (TextView) v.findViewById(R.id.mediacontroller_title);
        mTvRender = (TextView) v.findViewById(R.id.mediacontroller_renderview);
        mTvSettings = (TextView) v.findViewById(R.id.mediacontroller_settings);
        title.setOnClickListener(this);
        mTvRender.setOnClickListener(this);
        mTvSettings.setOnClickListener(this);

        mAspectRadioOptions = (RadioGroup) v.findViewById(R.id.aspect_ratio_group);
        // 选项列表高度
        mAspectOptionsHeight = getResources().getDimensionPixelSize(R.dimen.px50) * 4;
        mAspectRadioOptions.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                int aspectRadio = IRenderView.AR_ASPECT_FIT_PARENT;
                if (checkedId == R.id.aspect_fit_parent) {
                    aspectRadio = IRenderView.AR_ASPECT_FIT_PARENT;
                } else if (checkedId == R.id.aspect_fit_screen) {
                    aspectRadio = IRenderView.AR_ASPECT_FILL_PARENT;
                } else if (checkedId == R.id.aspect_16_and_9) {
                    aspectRadio = IRenderView.AR_16_9_FIT_PARENT;
                } else if (checkedId == R.id.aspect_4_and_3) {
                    aspectRadio = IRenderView.AR_4_3_FIT_PARENT;
                }
                AnimHelper.doClipViewHeight(mAspectRadioOptions, mAspectOptionsHeight, 0, 150);
                if (mPlayer != null) {
                    mPlayer.setAspectRatio(aspectRadio);
                }
            }
        });

        mRenderRadioOptions = (RadioGroup) v.findViewById(R.id.render_ratio_group);
        // 选项列表高度
        mRenderOptionsHeight = getResources().getDimensionPixelSize(R.dimen.px50) * 3;
        mRenderRadioOptions.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                int renderRadio = Constants.RenderViewStates.RENDER_SURFACE_VIEW;
                if (checkedId == R.id.render_no) {
                    renderRadio = Constants.RenderViewStates.RENDER_NONE;
                } else if (checkedId == R.id.render_surfacrview) {
                    renderRadio = Constants.RenderViewStates.RENDER_SURFACE_VIEW;
                } else if (checkedId == R.id.render_textureview) {
                    renderRadio = Constants.RenderViewStates.RENDER_TEXTURE_VIEW;
                }
                AnimHelper.doClipViewHeight(mRenderRadioOptions, mRenderOptionsHeight, 0, 150);
                if (mPlayer != null) {
                    mPlayer.setRenderViewOption(renderRadio);
                }
            }
        });

        mProgress = (SeekBar) v.findViewById(R.id.mediacontroller_progress);
        if (mProgress != null) {
            mProgress.setOnSeekBarChangeListener(mSeekListener);
            mProgress.setMax(MAX_VIDEO_SEEK);
        }
        mEndTime = (TextView) v.findViewById(R.id.mediacontroller_runtime_id);
        mCurrentTime = (TextView) v.findViewById(R.id.mediacontroller_remainingtime_id);
    }

    /**
     * Show the controller on screen. It will go away automatically after 3
     * seconds of inactivity.
     */
    @Override
    public void show() {
        show(sDefaultTimeout);
    }

    /**
     * Show the controller on screen. It will go away automatically after
     * 'timeout' milliseconds of inactivity.
     *
     * @param timeout The timeout in milliseconds. Use 0 to show the controller
     *                until hide() is called.
     */
    @Override
    public void show(int timeout) {
        if (!mShowing && mAnchor != null) {
            setProgress();
            updateFloatingWindowLayout();
            mWindowManager.addView(mDecor, mDecorLayoutParams);
            mShowing = true;
        }
        updatePausePlay();
        // cause the progress bar to be updated even if mShowing
        // was already true. This happens, for example, if we're
        // paused with the progress bar showing the user hits play.
        mHandler.sendEmptyMessage(SHOW_PROGRESS);
        Message msg = mHandler.obtainMessage(FADE_OUT);
        if (timeout != 0) {
            mHandler.removeMessages(FADE_OUT);
            mHandler.sendMessageDelayed(msg, timeout);
        }
    }

    public void removeFadeoutMessage() {
        mHandler.removeMessages(FADE_OUT);
    }

    public void postFadeoutMessage() {
        Message msg = mHandler.obtainMessage(FADE_OUT);
        mHandler.removeMessages(FADE_OUT);
        mHandler.sendMessageDelayed(msg, sDefaultTimeout);
    }

    @Override
    public boolean isShowing() {
        return mShowing;
    }

    /**
     * Remove the controller from the screen.
     */
    @Override
    public void hide() {
        if (mAnchor == null)
            return;
        Log.w("MediaController", "hide");
        if (mShowing) {
            try {
                mHandler.removeMessages(FADE_OUT);
                mHandler.removeMessages(SHOW_PROGRESS);
                mHandler.removeCallbacksAndMessages(null);
                showAspectRatioOptions(false);
                showRenderRatioOptions(false);
                mWindowManager.removeView(mDecor);
            } catch (IllegalArgumentException ex) {
                Log.w("MediaController", "already removed");
            }
            mShowing = false;
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.mediacontroller_title:
                mPlayer.finishPlay();
                break;
            case R.id.mediacontroller_renderview:
                showAspectRatioOptions(false);
                showRenderRatioOptions(true);
                break;
            case R.id.mediacontroller_settings:
                showRenderRatioOptions(false);
                showAspectRatioOptions(true);
                break;
            case R.id.mediacontroller_player_pauseorplay:
                doPauseResume();
                break;
            default:
                break;
        }
    }

    /**
     * 显示宽高比设置
     *
     * @param isShow
     */
    private void showAspectRatioOptions(boolean isShow) {
        if (isShow) {
            if (this.isShowing()) {
                AnimHelper.doClipViewHeight(mAspectRadioOptions, 0, mAspectOptionsHeight, 150);
            }
        } else {
            ViewGroup.LayoutParams layoutParams = mAspectRadioOptions.getLayoutParams();
            layoutParams.height = 0;
        }
    }

    /**
     * render设置
     *
     * @param isShow
     */
    private void showRenderRatioOptions(boolean isShow) {
        if (isShow) {
            if (this.isShowing()) {
                AnimHelper.doClipViewHeight(mRenderRadioOptions, 0, mRenderOptionsHeight, 150);
            }
        } else {
            ViewGroup.LayoutParams layoutParams = mRenderRadioOptions.getLayoutParams();
            layoutParams.height = 0;
        }
    }

    private static class EventHandler extends Handler {

        private final WeakReference<IjkMediaController> mController;

        public EventHandler(IjkMediaController controller) {
            this.mController = new WeakReference<IjkMediaController>(controller);
        }

        @Override
        public void handleMessage(Message msg) {
            IjkMediaController controller = mController.get();
            if (controller != null) {
                long pos;
                switch (msg.what) {
                    case FADE_OUT:
                        controller.hide();
                        break;
                    case SHOW_PROGRESS:
                        pos = controller.setProgress();
                        if (!controller.mDragging && controller.mShowing && controller.mPlayer.isPlaying()) {
                            msg = obtainMessage(SHOW_PROGRESS);
                            sendMessageDelayed(msg, 1000 - (pos % 1000));
                            controller.updatePausePlay();
                        }
                        break;
                }
            }
        }
    }

    private int setProgress() {
        if (mPlayer == null || mDragging) {
            return 0;
        }
        position = mPlayer.getCurrentPosition();
        duration = mPlayer.getDuration();
        if (mProgress != null) {
            if (duration > 0) {
                // use long to avoid overflow
                long pos = 1000L * position / duration;
                mProgress.setProgress((int) pos);
            }
            int percent = mPlayer.getBufferPercentage();
            Log.i("setProgress","   11111111   percent * 10  = "+percent * 10);
            mProgress.setSecondaryProgress(percent * 10 >= 980 ? 1000 : percent * 10);
        }

        if (mEndTime != null)
            mEndTime.setText(StringUtil.stringForTime(duration < 0 ? 0 : duration));
        if (mCurrentTime != null)
            mCurrentTime.setText(StringUtil.stringForTime(position < 0 ? 0 : position));
        return position;
    }

    public void setProgress(int progress) {
        mProgress.setProgress(progress);
    }

    private int i = 0;
    private void updatePausePlay() {
        if (mRoot == null && mPauseButton == null)
            return;
        boolean isPlaying = mPlayer.isPlaying();
        if(isPlaying){
            Log.i("updatePausePlay","updatePausePlay " + i + "  播放");
        }else{
            Log.i("updatePausePlay","updatePausePlay " + i + " 暂停");
        }
        if (mPlayer.isPlaying()) {
            Log.i("updatePausePlay","updatePausePlay  " + i + "确定是播放");
            mPauseButton.setImageResource(R.mipmap.ic_media_pause);
        } else {
            Log.i("updatePausePlay","updatePausePlay   " + i + "  确定是暂停");
            mPauseButton.setImageResource(R.mipmap.ic_media_play);
        }
        i++;
    }

    private void doPauseResume() {
        if (mPlayer.isPlaying()) {
            Log.i("updatePausePlay","doPauseResume " + i + "  播放  需要暂停");
            mPlayer.pause();
        } else {
            Log.i("updatePausePlay","doPauseResume " + i + "  暂停  需要播放");
            mPlayer.start();
        }
        //延时200ms，否则有时会来不及判断isPlaying
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updatePausePlay();
            }
        },200);
    }

    // There are two scenarios that can trigger the seekbar listener to trigger:
    //
    // The first is the user using the touchpad to adjust the posititon of the
    // seekbar's thumb. In this case onStartTrackingTouch is called followed by
    // a number of onProgressChanged notifications, concluded by
    // onStopTrackingTouch.
    // We're setting the field "mDragging" to true for the duration of the
    // dragging
    // session to avoid jumps in the position in case of ongoing playback.
    //
    // The second scenario involves the user operating the scroll ball, in this
    // case there WON'T BE onStartTrackingTouch/onStopTrackingTouch
    // notifications,
    // we will simply apply the updated position without suspending regular
    // updates.

    private boolean mDragging;
    private SeekBar.OnSeekBarChangeListener mSeekListener = new SeekBar.OnSeekBarChangeListener() {
        //touch之前的播放状态
//        private int state;
        long duration;
        long newposition;
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            if (!fromUser) {
                return;
            }
            duration = (long) mPlayer.getDuration();
            newposition = (duration * progress) / 1000L;
//            mPlayer.seekTo((int) newposition);
            if (mCurrentTime != null)
                mCurrentTime.setText(StringUtil.stringForTime((int) newposition));
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
//            state = mPlayer.getCurrentPlayState();
//            mPlayer.pause();
            show(3600000);
            mDragging = true;
            removeCallbacks(mShowProgress);
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            IjkLog.i(TAG + " onStopTrackingTouch  ");
            mDragging = false;
//            if(state != Constants.PlayStates.STATE_PAUSED){
//                mPlayer.start();
//            }
            mPlayer.seekTo((int) newposition);
            setProgress();
            updatePausePlay();
            show(sDefaultTimeout);
            mHandler.removeMessages(SHOW_PROGRESS);
            // Ensure that progress is properly updated in the future,
            // the call to show() does not guarantee this because it is a
            // no-op if we are already showing.
            post(mShowProgress);
        }
    };

    private final Runnable mShowProgress = new Runnable() {
        @Override
        public void run() {
            int pos = setProgress();
            if (!mDragging && mShowing && mPlayer.isPlaying()) {
                postDelayed(mShowProgress, 1000 - (pos % 1000));
            }
        }
    };

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName(IjkMediaController.class.getName());
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(IjkMediaController.class.getName());
    }

    // 设置title
    @Override
    public void setTitle(String name) {
        if (title != null) {
            if (TextUtils.isEmpty(name)) {
                title.setVisibility(View.INVISIBLE);
            } else {
                title.setVisibility(View.VISIBLE);
                title.setText(name);
            }
        }

    }

    public void release() {
        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
//                show(0); // show until hide is called
                break;
            case MotionEvent.ACTION_UP:
//                show(sDefaultTimeout); // start timeout
                break;
            case MotionEvent.ACTION_CANCEL:
//                hide();
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
//        show(sDefaultTimeout);
        return false;
    }

}
