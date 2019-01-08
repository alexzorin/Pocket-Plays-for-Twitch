package com.sebastianrask.bettersubscription.misc;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.PrintStream;

public class FileLoggingExceptionHandler implements Thread.UncaughtExceptionHandler {

    private static final String LOG_TAG = FileLoggingExceptionHandler.class.getSimpleName();

    private Context ctx;
    private Thread.UncaughtExceptionHandler rootHandler;

    public FileLoggingExceptionHandler(Context ctx) {
        this.ctx = ctx;
        rootHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
           final File f = new File(ctx.getFilesDir(), "POCKET_PLAYS_CRASHFILE");
           try (PrintStream ps = new PrintStream(f)) {
               throwable.printStackTrace(ps);
           }
           Log.d(LOG_TAG, "Logged exception to file: " + f.getAbsolutePath());
        } catch (Exception e) {
            Log.d(LOG_TAG, "Exception handler failed", e);
        } finally {
            rootHandler.uncaughtException(thread, throwable);
        }
    }
}
