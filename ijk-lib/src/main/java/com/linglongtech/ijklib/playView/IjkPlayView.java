/*
 * Copyright (C) 2015 Bilibili
 * Copyright (C) 2015 Zhang Rui <bbcallen@gmail.com>
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

package com.linglongtech.ijklib.playView;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.linglongtech.ijklib.media.IRenderView;
import com.linglongtech.ijklib.media.SurfaceRenderView;
import com.linglongtech.ijklib.media.TextureRenderView;
import com.linglongtech.ijklib.utils.Constants;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.linglongtech.ijklib.entity.VideoInfo;
import com.linglongtech.ijklib.media.FileMediaDataSource;
import com.linglongtech.ijklib.media.MediaPlayerCompat;
//import linglongtech.ijk.player.IjkPlayer;
import com.linglongtech.ijklib.utils.IjkPlayLog;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.misc.IMediaDataSource;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;

public class IjkPlayView extends FrameLayout implements ICorePlayerControl {

    private IjkMediaPlayer mMediaPlayer;
    private String TAG = "IjkPlayView";
    private Context mContext;
    private VideoInfo mVideo;
    private Uri mUri;
    private String title;

    //默认绘制器
    private List<Integer> mAllRenders = new ArrayList<Integer>();
    private int mCurrentRenderIndex = 1;
    private int mCurrentRender = Constants.RenderViewStates.RENDER_SURFACE_VIEW;

    // All the stuff we need for playing and showing a video
    private IRenderView.ISurfaceHolder mSurfaceHolder = null;
    private IMediaController mMediaController;
    private IRenderView mRenderView;
    private int mVideoSarNum;
    private int mVideoSarDen;
    private int mVideoWidth;
    private int mVideoHeight;
    private int mSurfaceWidth;
    private int mSurfaceHeight;

    private int mCurrentState = Constants.PlayStates.STATE_IDLE;
    private int mTargetState = Constants.PlayStates.STATE_IDLE;

    // 当前进度
    private int mCurPosition = Constants.INVALID_VALUE;
    // seek position while preparing
    private int mSeekWhenPrepared;
    // 当前百分比进度
    private int mCurrentBufferPercentage;

    private IMediaPlayer.OnPreparedListener mPreparedListener = null;
    private IMediaPlayer.OnCompletionListener mCompletionListener = null;
    private IMediaPlayer.OnBufferingUpdateListener mVideoBufferingUpdateListener = null;
    private IMediaPlayer.OnSeekCompleteListener mSeekCompleteListener = null;
    private IMediaPlayer.OnVideoSizeChangedListener mVideoSizeChangedListener = null;
    private IMediaPlayer.OnErrorListener mErrorListener = null;
    private IMediaPlayer.OnInfoListener mVideoInfoListener = null;
    private IMediaPlayer.OnTimedTextListener mTimedTextListener = null;
    private OnSurfaceListener mSurfaceListener = null;
    private OnPlayListener mPlayListener;

    /**
     * ============================ 播放状态控制 ============================
     */

    public IjkPlayView(Context context) {
        super(context);
        initVideoView(context);
    }

    public IjkPlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initVideoView(context);
    }

    public IjkPlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initVideoView(context);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public IjkPlayView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initVideoView(context);
    }

    // REMOVED: onMeasure
    // REMOVED: onInitializeAccessibilityEvent
    // REMOVED: onInitializeAccessibilityNodeInfo
    // REMOVED: resolveAdjustedSize
    private void initVideoView(Context context) {
        mContext = context.getApplicationContext();
        mMediaPlayer = initMediaPlayer();
        initRenders();
        mVideoWidth = 0;
        mVideoHeight = 0;
        // REMOVED: getHolder().addCallback(mSHCallback);
        // REMOVED: getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        // REMOVED: mPendingSubtitleTracks = new Vector<Pair<InputStream, MediaFormat>>();
        mCurrentState = Constants.PlayStates.STATE_IDLE;
        mTargetState = Constants.PlayStates.STATE_IDLE;
        notifyMediaStatus();
    }

    /**
     * 默认Aspect
     */
    private int mCurrentAspectRatio = IRenderView.AR_ASPECT_FIT_PARENT;

    /**
     * 添加设置绘制器
     */
    private void initRenders() {
        mAllRenders.clear();
        mAllRenders.add(Constants.RenderViewStates.RENDER_NONE);
        mAllRenders.add(Constants.RenderViewStates.RENDER_SURFACE_VIEW);
        mAllRenders.add(Constants.RenderViewStates.RENDER_TEXTURE_VIEW);
        mCurrentRender = mAllRenders.get(mCurrentRenderIndex);
        setRender(mCurrentRender);
    }

    public void setRender(int render) {
        switch (render) {
            case Constants.RenderViewStates.RENDER_NONE:
                setRenderView(null);
                break;
            case Constants.RenderViewStates.RENDER_TEXTURE_VIEW: {
                TextureRenderView renderView = new TextureRenderView(getContext());
                if (mMediaPlayer != null) {
                    renderView.getSurfaceHolder().bindToMediaPlayer(mMediaPlayer);
                    renderView.setVideoSize(mMediaPlayer.getVideoWidth(), mMediaPlayer.getVideoHeight());
                    renderView.setVideoSampleAspectRatio(mMediaPlayer.getVideoSarNum(), mMediaPlayer.getVideoSarDen());
                    renderView.setAspectRatio(mCurrentAspectRatio);
                }
                setRenderView(renderView);
                break;
            }
            case Constants.RenderViewStates.RENDER_SURFACE_VIEW: {
                SurfaceRenderView renderView = new SurfaceRenderView(getContext());
                setRenderView(renderView);
                break;
            }
            default:
                Log.e(TAG, String.format(Locale.getDefault(), "invalid render %d\n", render));
                break;
        }
    }

    public void setRenderView(IRenderView renderView) {
        if (mRenderView != null) {
            if (mMediaPlayer != null)
                mMediaPlayer.setDisplay(null);
            View renderUIView = mRenderView.getView();
            mRenderView.removeRenderCallback(mSHCallback);
            mRenderView = null;
            removeView(renderUIView);
        }
        if (renderView == null)
            return;

        mRenderView = renderView;
        renderView.setAspectRatio(mCurrentAspectRatio);
        if (mVideoWidth > 0 && mVideoHeight > 0)
            renderView.setVideoSize(mVideoWidth, mVideoHeight);
        if (mVideoSarNum > 0 && mVideoSarDen > 0)
            renderView.setVideoSampleAspectRatio(mVideoSarNum, mVideoSarDen);

        View renderUIView = mRenderView.getView();
        LayoutParams lp = new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        renderUIView.setLayoutParams(lp);
        addView(renderUIView);
        mRenderView.addRenderCallback(mSHCallback);
    }

    /**
     * Set video path.
     *
     * @param mVideo the video.
     */
    public void setVideo(VideoInfo mVideo) {
        if (mVideo != null) {
            this.mVideo = mVideo;
            setVideoURI(Uri.parse(mVideo.videoPath));
            title = getVideoName(mVideo.videoPath);
        }
    }

    private String getVideoName(String path) {
        String videoName;
        String name = path.substring(path.lastIndexOf("/") + 1, path.length());
        int indexName = name.lastIndexOf(".");
        if (indexName > 0) {
            videoName = name.substring(0, indexName);
        } else {
            videoName = name;
        }
        return videoName;
    }

    /**
     * Sets video URI.
     *
     * @param uri the URI of the video.
     */
    public void setVideoURI(Uri uri) {
        setVideoURI(uri, null);
        if (mCurPosition != Constants.INVALID_VALUE) {
            seekTo(mCurPosition);
            mCurPosition = Constants.INVALID_VALUE;
        } else {
            seekTo(0);
        }
    }

    /**
     * Sets video URI using specific headers.
     *
     * @param uri     the URI of the video.
     * @param headers the headers for the URI request.
     *                Note that the cross domain redirection is allowed by default, but that can be
     *                changed with key/value pairs through the headers parameter with
     *                "android-allow-cross-domain-redirect" as the key and "0" or "1" as the value
     *                to disallow or allow cross domain redirection.
     */
    private void setVideoURI(Uri uri, Map<String, String> headers) {
        mUri = uri;
//        mHeaders = headers;
        mSeekWhenPrepared = 0;
        openPlay();
        requestLayout();
        invalidate();
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void openNewVideoPLay() {
        if (mUri == null || mSurfaceHolder == null) {
            // not ready for playback just yet, will try again later
            return;
        }
        // we shouldn't clear the target state, because somebody might have
        // called start() previously
        release(false);
        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        try {
            if (mMediaPlayer == null) {
                mMediaPlayer = initMediaPlayer();
                mCurrentBufferPercentage = 0;
                String scheme = mUri.getScheme();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        (TextUtils.isEmpty(scheme) || scheme.equalsIgnoreCase("file"))) {
                    IMediaDataSource dataSource = new FileMediaDataSource(new File(mUri.toString()));
                    mMediaPlayer.setDataSource(dataSource);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                    mMediaPlayer.setDataSource(mContext, mUri, null);
                } else {
                    mMediaPlayer.setDataSource(mUri.toString());
                }
                bindSurfaceHolder(mMediaPlayer, mSurfaceHolder);
                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mMediaPlayer.setScreenOnWhilePlaying(true);
                mMediaPlayer.prepareAsync();
            }

            // REMOVED: mPendingSubtitleTracks
            // we don't set the target state here either, but preserve the
            // target state that was there before.
            mCurrentState = Constants.PlayStates.STATE_PREPARING;
            attachMediaController();
        } catch (IOException ex) {
            Log.w(TAG, "Unable to open content: " + mUri, ex);
            mCurrentState = Constants.PlayStates.STATE_ERROR;
            mTargetState = Constants.PlayStates.STATE_ERROR;
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
        } catch (IllegalArgumentException ex) {
            Log.w(TAG, "Unable to open content: " + mUri, ex);
            mCurrentState = Constants.PlayStates.STATE_ERROR;
            mTargetState = Constants.PlayStates.STATE_ERROR;
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
        } finally {
            // REMOVED: mPendingSubtitleTracks.clear();
            notifyMediaStatus();
        }
    }

    private void openPlay() {
        if (isRelease()) {
            openNewVideoPLay();
        } else {
            try {
                if (mMediaPlayer != null) {
                    mCurrentBufferPercentage = 0;
                    mMediaPlayer.reset();
                    setRenderViewOption(mCurrentRender);
                    mMediaPlayer.setDataSource(mContext, mUri, null);
                    mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    mMediaPlayer.setScreenOnWhilePlaying(true);
                    mMediaPlayer.prepareAsync();
                    mMediaPlayer.start();
                    mCurrentState = Constants.PlayStates.STATE_PREPARING;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean isRelease() {
        return mMediaPlayer == null ? true : false;
    }


    /**
     * MediaPlayer Prepared
     */
    private IMediaPlayer.OnPreparedListener mOnPreparedListener = new IMediaPlayer.OnPreparedListener() {

        @Override
        public void onPrepared(IMediaPlayer mp) {
            mCurrentState = Constants.PlayStates.STATE_PREPARED;
            notifyMediaStatus();
            if (mPreparedListener != null) {
                mPreparedListener.onPrepared(mp);
            }
            if (mMediaController != null) {
                mMediaController.setEnabled(true);
            }
            mVideoHeight = mp.getVideoHeight();
            mVideoWidth = mp.getVideoWidth();
            IjkPlayLog.i(TAG + "OnPreparedListener  " + mVideoHeight + "  w  " + mVideoWidth + " mSeekWhenPrepared " + mSeekWhenPrepared);
            int seekToPosition = mSeekWhenPrepared;
            if (seekToPosition != 0) {
                seekTo(seekToPosition);
            }
            if (mVideoHeight != 0 && mVideoWidth != 0) {
                if (mRenderView != null) {
                    mRenderView.setVideoSize(mVideoWidth, mVideoHeight);
                    mRenderView.setVideoSampleAspectRatio(mVideoSarNum, mVideoSarDen);
                    if (!mRenderView.shouldWaitForResize() || mSurfaceWidth == mVideoWidth && mSurfaceHeight == mVideoHeight) {
                        // We didn't actually change the size (it was already at the size
                        // we need), so we won't get a "surface changed" callback, so
                        // start the video here instead of in the callback.
                        if (mTargetState == Constants.PlayStates.STATE_PLAYING) {
                            start();
                            showOsd();
                        } else if (!isPlaying() &&
                                (seekToPosition != 0 || getCurrentPosition() > 0)) {
                            if (mMediaController != null) {
                                // Show the media controls when we're paused into a video and make 'em stick.
                                mMediaController.show(0);
                            }
                        }
                    }
                }
            } else {
                if (mTargetState == Constants.PlayStates.STATE_PLAYING) {
                    start();
                }
            }
        }
    };

    private IMediaPlayer.OnCompletionListener mOnCompletionListener = new IMediaPlayer.OnCompletionListener() {

        @Override
        public void onCompletion(IMediaPlayer mp) {
            IjkPlayLog.i(TAG + "---" + "      OnCompletionListener    ");
            mCurrentState = Constants.PlayStates.STATE_COMPLETED;
            mTargetState = Constants.PlayStates.STATE_COMPLETED;
            notifyMediaStatus();
            if (mMediaController != null) {
                mMediaController.hide();
            }
            if (mCompletionListener != null) {
                mCompletionListener.onCompletion(null);
            }
        }
    };

    private IMediaPlayer.OnBufferingUpdateListener mOnBufferingUpdateListener = new IMediaPlayer.OnBufferingUpdateListener() {

        @Override
        public void onBufferingUpdate(IMediaPlayer mp, int percent) {
            IjkPlayLog.i(TAG + "  onBufferUpdate    " + percent);
            mCurrentBufferPercentage = percent;
            if (mVideoBufferingUpdateListener != null) {
                mVideoBufferingUpdateListener.onBufferingUpdate(mp, percent);
            }
        }
    };

    private IMediaPlayer.OnSeekCompleteListener mOnSeekCompleteListener = new IMediaPlayer.OnSeekCompleteListener() {

        @Override
        public void onSeekComplete(IMediaPlayer mp) {

            if (mSeekCompleteListener != null) {
                mSeekCompleteListener.onSeekComplete(mp);
            }
        }
    };

    IMediaPlayer.OnVideoSizeChangedListener mOnVideoSizeChangedListener =
            new IMediaPlayer.OnVideoSizeChangedListener() {
                public void onVideoSizeChanged(IMediaPlayer mp, int width, int height, int sarNum, int sarDen) {
                    mVideoWidth = mp.getVideoWidth();
                    mVideoHeight = mp.getVideoHeight();
                    mVideoSarNum = mp.getVideoSarNum();
                    mVideoSarDen = mp.getVideoSarDen();
                    if (mVideoWidth != 0 && mVideoHeight != 0) {
                        if (mRenderView != null) {
                            mRenderView.setVideoSize(mVideoWidth, mVideoHeight);
                            mRenderView.setVideoSampleAspectRatio(mVideoSarNum, mVideoSarDen);
                        }
                        // REMOVED: getHolder().setFixedSize(mVideoWidth, mVideoHeight);
                        requestLayout();
                    }
                }
            };


    private IMediaPlayer.OnErrorListener mOnErrorListener =
            new IMediaPlayer.OnErrorListener() {
                public boolean onError(IMediaPlayer mp, int framework_err, int impl_err) {
                    IjkPlayLog.e(TAG + "   OnErrorListener  Error:  framework_err  " + framework_err + " impl_err  " + impl_err);
                    mCurrentState = Constants.PlayStates.STATE_ERROR;
                    mTargetState = Constants.PlayStates.STATE_ERROR;
                    notifyMediaStatus();
                    if (mMediaController != null) {
                        mMediaController.hide();
                    }
                    /* If an error handler has been supplied, use it and finish. */
                    if (mErrorListener != null) {
                        if (mErrorListener.onError(mp, framework_err, impl_err)) {
                            return true;
                        }
                    }

                    return true;
                }
            };


    private IMediaPlayer.OnInfoListener mOnInfoListener = new IMediaPlayer.OnInfoListener() {

        @Override
        public boolean onInfo(IMediaPlayer mp, int status, int extra) {
            IjkPlayLog.i(TAG + "onInfo" + status + extra);
            if (mVideoInfoListener != null) {
                mVideoInfoListener.onInfo(mp, status, extra);
            }
            return true;
        }
    };

    /**
     * SurfaceCallBack
     */

    // REMOVED: mSHCallback
    private void bindSurfaceHolder(IMediaPlayer mp, IRenderView.ISurfaceHolder holder) {
        if (mp == null)
            return;

        if (holder == null) {
            mp.setDisplay(null);
            return;
        }
        holder.bindToMediaPlayer(mp);
    }

    IRenderView.IRenderCallback mSHCallback = new IRenderView.IRenderCallback() {
        @Override
        public void onSurfaceChanged(@NonNull IRenderView.ISurfaceHolder holder, int format, int w, int h) {
            if (holder.getRenderView() != mRenderView) {
                Log.e(TAG, "onSurfaceChanged: unmatched render callback\n");
                return;
            }
            mSurfaceWidth = w;
            mSurfaceHeight = h;
            boolean isValidState = (mTargetState == Constants.PlayStates.STATE_PLAYING);
            boolean hasValidSize = !mRenderView.shouldWaitForResize() || (mVideoWidth == w && mVideoHeight == h);
            if (mMediaPlayer != null && isValidState && hasValidSize) {
                if (mSeekWhenPrepared != 0) {
                    seekTo(mSeekWhenPrepared);
                }
                start();
            }
        }

        @Override
        public void onSurfaceCreated(@NonNull IRenderView.ISurfaceHolder holder, int width, int height) {
            if (holder.getRenderView() != mRenderView) {
                Log.e(TAG, "onSurfaceCreated: unmatched render callback\n");
                return;
            }

            mSurfaceHolder = holder;
            if (mMediaPlayer != null)
                bindSurfaceHolder(mMediaPlayer, holder);
            else
                openPlay();
        }

        @Override
        public void onSurfaceDestroyed(@NonNull IRenderView.ISurfaceHolder holder) {
            if (holder.getRenderView() != mRenderView) {
                Log.e(TAG, "onSurfaceDestroyed: unmatched render callback\n");
                return;
            }

            // after we return from this we can't use the surface any more
            mSurfaceHolder = null;
            // REMOVED: if (mMediaController != null) mMediaController.hide();
            // REMOVED: release(true);
            releaseWithoutStop();
        }
    };

    public void releaseWithoutStop() {
        if (mMediaPlayer != null)
            mMediaPlayer.setDisplay(null);
    }

    public void stopPlayback() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
//            mMediaPlayer = null;
            mCurrentState = Constants.PlayStates.STATE_IDLE;
            mTargetState = Constants.PlayStates.STATE_IDLE;
            notifyMediaStatus();
            AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            am.abandonAudioFocus(null);
        }
    }

    /*
     * release the media player in any state
     */
    public void release(boolean cleartargetstate) {
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
            // REMOVED: mPendingSubtitleTracks.clear();
            mCurrentState = Constants.PlayStates.STATE_IDLE;
            notifyMediaStatus();
            if (cleartargetstate) {
                mTargetState = Constants.PlayStates.STATE_IDLE;
            }
            AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            am.abandonAudioFocus(null);
        }
    }

    private void notifyMediaStatus() {
        if (mOnInfoListener != null && mMediaPlayer != null) {
            mOnInfoListener.onInfo(mMediaPlayer, mCurrentState, -1);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (isInPlaybackState() && mMediaController != null && ev.getAction() == MotionEvent.ACTION_UP) {
            toggleMediaControlsVisiblity();
        }
        return false;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        if (isInPlaybackState() && mMediaController != null && ev.getAction() == MotionEvent.ACTION_UP) {
            toggleMediaControlsVisiblity();
        }
        return false;
    }

    private void toggleMediaControlsVisiblity() {
        if (mMediaController != null) {
            if (mMediaController.isShowing()) {
                mMediaController.hide();
            } else {
                mMediaController.show();
            }
        }
    }

    public boolean isInPlaybackState() {
        return (mMediaPlayer != null &&
                mCurrentState != Constants.PlayStates.STATE_ERROR &&
                mCurrentState != Constants.PlayStates.STATE_IDLE &&
                mCurrentState != Constants.PlayStates.STATE_PREPARING);
    }

    public void reload() {
        mCurrentState = Constants.PlayStates.STATE_PLAYING;
        mTargetState = Constants.PlayStates.STATE_PLAYING;
    }

    public void showOsd() {
        if (mMediaController != null) {
            mMediaController.setTitle(title);
            mMediaController.show();
        }
    }

    public void setMediaController(IMediaController controller) {
        if (mMediaController != null) {
            mMediaController.hide();
        }
        mMediaController = controller;
        attachMediaController();
    }

    private void attachMediaController() {
        if (mMediaPlayer != null && mMediaController != null) {
            mMediaController.setMediaPlayer(this);
            View anchorView = this.getParent() instanceof View ?
                    (View) this.getParent() : this;
            mMediaController.setAnchorView(anchorView);
            mMediaController.setEnabled(isInPlaybackState());
        }
    }

    /**
     * ICorePlayerControl
     */
    @Override
    public void start() {
        if (isInPlaybackState() && mMediaPlayer != null) {
            mMediaPlayer.start();
            mCurrentState = Constants.PlayStates.STATE_PLAYING;
            notifyMediaStatus();
//            showOsd();
        }
        mTargetState = Constants.PlayStates.STATE_PLAYING;
    }

    @Override
    public void resetStart() {

    }

    @Override
    public void pause() {
        if (isInPlaybackState() && mMediaPlayer != null) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.pause();
                mCurrentState = Constants.PlayStates.STATE_PAUSED;
                notifyMediaStatus();
            }
//            mMediaController.show();
        }
        mTargetState = Constants.PlayStates.STATE_PAUSED;
    }

    @Override
    public void resume() {
        if (isInPlaybackState() && mMediaPlayer != null) {
            mMediaPlayer.start();
            mCurrentState = Constants.PlayStates.STATE_PLAYING;
        }
        mTargetState = Constants.PlayStates.STATE_PLAYING;
    }

    @Override
    public long getTcpSpeed() {
        if (isInPlaybackState() && mMediaPlayer != null) {
            return (int) mMediaPlayer.getTcpSpeed();
        }
        return 0;
    }

    @Override
    public void finishPlay() {
        if (mMediaController != null) {
            mMediaController.hide();
        }
        if (mCompletionListener != null) {
            mCompletionListener.onCompletion(null);
        }
        release(true);
        if(mPlayListener != null){
            mPlayListener.backFinish();
        }

    }

    @Override
    public int getDuration() {
        if (isInPlaybackState() && mMediaPlayer != null) {
            return (int) mMediaPlayer.getDuration();
        }
        return -1;
    }

    @Override
    public int getCurrentPosition() {
        if (isInPlaybackState() && mMediaPlayer != null) {
            return (int) mMediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    @Override
    public int getCurrentPlayState() {
        return mCurrentState;
    }

    @Override
    public boolean isPlaying() {
        if (isInPlaybackState() && mMediaPlayer != null) {
            return isInPlaybackState() && mMediaPlayer.isPlaying();
        }
        return false;
    }

    @Override
    public void seekTo(int msec) {
        if (isInPlaybackState() && mMediaPlayer != null) {
            mMediaPlayer.seekTo(msec);
            mSeekWhenPrepared = 0;
        } else {
            mSeekWhenPrepared = msec;
        }
    }

    @Override
    public int getBufferPercentage() {
        if (mMediaPlayer != null) {
            return mCurrentBufferPercentage;
        }
        return 0;
    }

    @Override
    public void setAspectRatio(int aspectRatio) {
        mCurrentAspectRatio = aspectRatio;
        if (mRenderView != null)
            mRenderView.setAspectRatio(mCurrentAspectRatio);
    }

    @Override
    public void setRenderViewOption(int renderRatio) {
        mCurrentRenderIndex = renderRatio;
        setRenderView();
    }

    public void setRenderView() {
        setRender(mCurrentRenderIndex);
    }

    /**
     * tracks  start
     */
    public ITrackInfo[] getTrackInfo() {
        if (mMediaPlayer == null)
            return null;
        return mMediaPlayer.getTrackInfo();
    }

    public void selectTrack(int stream) {
        if (mMediaPlayer == null) {
            MediaPlayerCompat.selectTrack(mMediaPlayer, stream);
        }
    }

    public void deselectTrack(int stream) {
        if (mMediaPlayer == null) {
            MediaPlayerCompat.deselectTrack(mMediaPlayer, stream);
        }
    }

    public int getSelectedTrack(int trackType) {
        if (mMediaPlayer == null) {
            return MediaPlayerCompat.getSelectedTrack(mMediaPlayer, trackType);
        }
        return -1;

    }


    /**
     * listener start
     */

    /**
     * 接口回调
     */
    public interface OnSurfaceListener {
        void onSufaceCreate();

        void onSurfaceDestory();
    }

    public interface OnPlayListener {
//        void showloding();
//
//        void hideLoading();

        void backFinish();
    }

    public void setOnSurfaceListener(OnSurfaceListener listener) {
        mSurfaceListener = listener;
    }

    public void setPlayListener(OnPlayListener listener) {
        this.mPlayListener = listener;
    }

    public void setOnPreparedListener(IMediaPlayer.OnPreparedListener listener) {
        mPreparedListener = listener;
    }

    public void setBufferingUpdateListener(IMediaPlayer.OnBufferingUpdateListener listener) {
        mVideoBufferingUpdateListener = listener;
    }

    public void setSeekCompleteListener(IMediaPlayer.OnSeekCompleteListener listener) {
        mSeekCompleteListener = listener;
    }

    public void setOnCompletionListener(IMediaPlayer.OnCompletionListener listener) {
        mCompletionListener = listener;
    }

    public void setOnErrorListener(IMediaPlayer.OnErrorListener listener) {
        mErrorListener = listener;
    }

    public void setInfoListener(IMediaPlayer.OnInfoListener listener) {
        mVideoInfoListener = listener;
    }

    public void setTimedTextListener(IMediaPlayer.OnTimedTextListener listener) {
        mTimedTextListener = listener;
    }

    public void setOnVideoSizeChangedListener(IMediaPlayer.OnVideoSizeChangedListener listener) {
        mVideoSizeChangedListener = listener;
    }
    /**
     * listener end
     */


    /**
     * IJkMediaPlayet init
     */

    private IjkMediaPlayer initMediaPlayer() {
        if (mMediaPlayer == null) {
            Log.i(TAG, "openVideoPlay initPlayer");
            mMediaPlayer = PlayerInstance.getInstance(mContext.getApplicationContext()).getIjkPlayer();
        }
        mMediaPlayer.setOnPreparedListener(mOnPreparedListener);
        mMediaPlayer.setOnCompletionListener(mOnCompletionListener);
        mMediaPlayer.setOnBufferingUpdateListener(mOnBufferingUpdateListener);
        mMediaPlayer.setOnVideoSizeChangedListener(mOnVideoSizeChangedListener);
        mMediaPlayer.setOnErrorListener(mOnErrorListener);
        mMediaPlayer.setOnInfoListener(mOnInfoListener);
        mMediaPlayer.setOnSeekCompleteListener(mOnSeekCompleteListener);
        return mMediaPlayer;
    }

}
