package com.linglongtech.ijklib.playView;


/**
 * 绘制器 渲染界面 实现接口，封装player方法
 */
public interface ICorePlayerControl {
    /**
     * 播放
     */
    void start();

    /**
     * 播放下一个
     */
    void resetStart();

    /**
     * 暂停
     */
    void pause();

    /**
     * 暂停
     */
    void resume();

    /**
     * 缓存下载速度
     */
    long getTcpSpeed();

    /**
     * 结束播放，退出
     */
    void finishPlay();

    /**
     * 时长
     *
     * @return int
     */
    int getDuration();

    /**
     * 获取当前播放位置
     *
     * @return int
     */
    int getCurrentPosition();

    /**
     * 当前播放状态
     *
     * @return int
     */
    int getCurrentPlayState();

    /**
     * 是否播放
     *
     * @return boolean
     */
    boolean isPlaying();

    /**
     * seek 播放位置
     *
     * @param pos long
     */
    void seekTo(int pos);

    int getBufferPercentage();

    void setAspectRatio(int aspectRatio);

    void setRenderViewOption(int aspectRatio);

}