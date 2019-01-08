package com.sebastianrask.bettersubscription.tasks;

import android.os.AsyncTask;
import android.util.Log;
import android.util.Pair;

import com.sebastianrask.bettersubscription.misc.FollowHandler;
import com.sebastianrask.bettersubscription.service.Service;

/**
 * Created by Sebastian Rask on 19-04-2016.
 */
public class CheckFollowingTask extends AsyncTask<Pair<Integer, Integer>, Void, Boolean> {

	private String LOG_TAG = getClass().getSimpleName();

	private boolean isFollowed;
	private TaskCallback callback;

	public CheckFollowingTask(TaskCallback callback) {
		this.callback = callback;
	}

	@Override
	protected Boolean doInBackground(Pair<Integer, Integer>... params) {
		isFollowed = Service.isUserFollowingStreamer(params[0].first, params[0].second);
		return isFollowed;
	}

	@Override
	protected void onPostExecute(Boolean aBoolean) {
		if (callback != null) {
			callback.onTaskDone(isFollowed);
		}
		super.onPostExecute(aBoolean);
	}

	public interface TaskCallback {
		void onTaskDone(Boolean result);
	}
}
