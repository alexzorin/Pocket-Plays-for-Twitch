package com.sebastianrask.bettersubscription;

import android.annotation.SuppressLint;
import android.content.Context;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.multidex.MultiDex;
import androidx.multidex.MultiDexApplication;

/**
 * Created by SebastianRask on 20-02-2016.
 */
@SuppressLint("StaticFieldLeak") // It is alright to store application context statically
public class PocketPlaysApplication extends MultiDexApplication {
    private static Context mContext;

    public static boolean isCrawlerUpdate = false; //ToDo remember to disable for crawler updates

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this.getApplicationContext();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    public static void trackEvent(@StringRes int category, @StringRes int action, @Nullable String label) {
        // No-op
    }

    public static void trackEvent(String category, String action, @Nullable String label, @Nullable Long value) {
        // No-op
    }

}
