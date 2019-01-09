package com.sebastianrask.bettersubscription.tasks;

import android.os.AsyncTask;

import com.sebastianrask.bettersubscription.service.Service;

public class FetchTwitchAPITask extends AsyncTask<String, Void, String> {

    private Callback callback;

    public FetchTwitchAPITask(Callback cb) {
        callback = cb;
    }

    @Override
    protected void onPostExecute(String s) {
        super.onPostExecute(s);
        if (callback != null) {
            callback.onTaskDone(s);
        }
    }

    @Override
    protected String doInBackground(String... params) {
        final String url = params[0];

        return Service.urlToJSONString(url);
    }

    public interface Callback {
        public void onTaskDone(String result);
    }

}
