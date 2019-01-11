package com.linglongtech.ijklib.playView;

import android.content.Context;
import android.util.Log;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * Created by admin on 2018/9/11.
 */

public class PlayerInstance {
    public static final String TAG = "PlayerInstance";
    private static PlayerInstance mPlayerInstance;
    public IjkMediaPlayer ijkMediaPlayer;

    private Context mContext;

    private PlayerInstance(Context context) {
        mContext = context.getApplicationContext();
        ijkMediaPlayer = initPlayer();
    }

    public synchronized static PlayerInstance getInstance(Context context) {
        if (mPlayerInstance == null) {
            mPlayerInstance = new PlayerInstance(context);
        }
        return mPlayerInstance;
    }

    private synchronized IjkMediaPlayer initPlayer() {
        if (ijkMediaPlayer == null) {
            ijkMediaPlayer = new IjkMediaPlayer();
            Log.i(TAG ,"openVideoPlay initPlayer");
            ijkMediaPlayer.native_setLogLevel(IjkMediaPlayer.IJK_LOG_DEBUG);
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 5 * 1024 * 1024);//设置缓冲区为5MB
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 100);// 视频的话，设置100帧即开始播放
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1);//重连模式
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1); //seek 关键帧问题
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 0);
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0);
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", IjkMediaPlayer.SDL_FCC_RV32);
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1);
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0);
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0);
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48);
        }
        return ijkMediaPlayer;
    }

    public IjkMediaPlayer getIjkPlayer() {
        return ijkMediaPlayer;
    }
}
