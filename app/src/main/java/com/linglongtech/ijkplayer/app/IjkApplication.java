package com.linglongtech.ijkplayer.app;

import android.content.Context;

import com.linglongtech.utils.app.BaseApplication;
import com.nostra13.universalimageloader.cache.disc.naming.Md5FileNameGenerator;
import com.nostra13.universalimageloader.cache.memory.impl.LruMemoryCache;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.QueueProcessingType;

import com.linglongtech.ijklib.playView.PlayerInstance;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * Created by admin on 2018/7/30.
 */

public class IjkApplication extends BaseApplication {

    @Override
    public void onCreate() {
        super.onCreate();

        initImageLoader(this);
        initIjkPlayer();
    }

    // 加载 IjkMediaPlayer 库
    private void initIjkPlayer() {
        IjkMediaPlayer.loadLibrariesOnce(null);
        IjkMediaPlayer.native_profileBegin("libijkplayer.so");
        PlayerInstance.getInstance(getApplicationContext());
    }

    // 初始化ImageLoader
    private void initImageLoader(Context context) {
        ImageLoaderConfiguration config = new ImageLoaderConfiguration.Builder(
                context).threadPriority(Thread.NORM_PRIORITY - 2)
                .denyCacheImageMultipleSizesInMemory()
                .memoryCache(new LruMemoryCache(5 * 1024 * 1024))
                .memoryCacheSize(5 * 1024 * 1024)
                .diskCacheFileCount(100)
                .diskCacheSize(50 * 1024 * 1024)
                .discCacheFileNameGenerator(new Md5FileNameGenerator())
                .tasksProcessingOrder(QueueProcessingType.LIFO)
                .writeDebugLogs() // Remove for release app
                .build();
        ImageLoader.getInstance().init(config);
    }
}
