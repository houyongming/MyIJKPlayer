package com.linglongtech.ijklib.utils;

/**
 * Created by admin on 2018/9/7.
 */

public class Constants {

    // 无效变量
    public static final int INVALID_VALUE = -1;

    // 达到文件时长的允许误差值，用来判断是否播放完成
    public static final int INTERVAL_TIME = 1000;

    /**
     * 播放状态设置
     */
    public final class PlayStates {
        // 空闲
        public static final int STATE_IDLE = 330;
        // 错误
        public static final int STATE_ERROR = 331;
        // 加载中
        public static final int STATE_PREPARING = 332;
        // 加载完成
        public static final int STATE_PREPARED = 333;
        // 播放中
        public static final int STATE_PLAYING = 334;
        // 暂停
        public static final int STATE_PAUSED = 335;
        // 结束
        public static final int STATE_COMPLETED = 336;
    }

    /**
     * 绘制器选择设置
     */
    public final class RenderViewStates {
        public static final int RENDER_NONE = 0;
        public static final int RENDER_SURFACE_VIEW = 1;
        public static final int RENDER_TEXTURE_VIEW = 2;
    }
}
