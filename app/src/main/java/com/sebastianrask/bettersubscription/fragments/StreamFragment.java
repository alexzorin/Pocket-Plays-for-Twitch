package com.sebastianrask.bettersubscription.fragments;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.DrawableRes;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.transition.Transition;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.CheckedTextView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.sebastianrask.bettersubscription.R;
import com.sebastianrask.bettersubscription.activities.ChannelActivity;
import com.sebastianrask.bettersubscription.activities.stream.StreamActivity;
import com.sebastianrask.bettersubscription.adapters.PanelAdapter;
import com.sebastianrask.bettersubscription.misc.FollowHandler;
import com.sebastianrask.bettersubscription.misc.ResizeWidthAnimation;
import com.sebastianrask.bettersubscription.model.ChannelInfo;
import com.sebastianrask.bettersubscription.model.Panel;
import com.sebastianrask.bettersubscription.model.SleepTimer;
import com.sebastianrask.bettersubscription.service.Service;
import com.sebastianrask.bettersubscription.service.Settings;
import com.sebastianrask.bettersubscription.tasks.GetLiveStreamURL;
import com.sebastianrask.bettersubscription.tasks.GetPanelsTask;
import com.sebastianrask.bettersubscription.tasks.GetVODStreamURL;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;
import com.squareup.picasso.Target;
import com.transitionseverywhere.TransitionManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StreamFragment extends Fragment {
    private final int PLAY_PAUSE_ANIMATION_DURATION = 500,
            HIDE_ANIMATION_DELAY = 3000,
            SNACKBAR_SHOW_DURATION = 4000;

    private final String LOG_TAG = getClass().getSimpleName();
    private final Handler delayAnimationHandler = new Handler(),
            progressHandler = new Handler(),
            fetchViewCountHandler = new Handler(),
            fetchChattersHandler = new Handler();

    public boolean isFullscreen = false, embedded = false;
    public ChannelInfo mChannelInfo;
    public String vodId;
    public boolean castingViewVisible = false,
            audioViewVisible = false,
            chatOnlyViewVisible = false,
            autoPlay = true,
            hasPaused = false;
    private HeadsetPlugIntentReceiver headsetIntentReceiver;
    private Settings settings;
    private SleepTimer sleepTimer;
    private HashMap<String, String> qualityURLs;
    private BiMap<String, String> supportedQualities;
    private boolean isLandscape = false, previewInbackGround = false;
    private Runnable fetchViewCountRunnable;
    private Runnable fetchChattersRunnable;

    private View mVideoBackground;
    private PlayerView mPlayerView;
    private Toolbar mToolbar;
    private RelativeLayout mControlToolbar,
            mVideoWrapper;
    private ImageView mQualityButton,
            mPreview;
    private TextView source, high, medium, low, mobile, auto;
    private AppCompatActivity mActivity;
    private Snackbar snackbar;
    private SimpleExoPlayer mPlayer;
    private boolean chatHidden;

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPlaying()) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    delayHiding();
                    if (!previewInbackGround) {
                        hidePreview();
                    }
                }
            }
            progressHandler.postDelayed(this, 1000);
        }
    };
    private BottomSheetDialog mQualityBottomSheet, mProfileBottomSheet;
    private CheckedTextView mChatOnlySelector;
    private ViewGroup rootView;
    private MenuItem optionsMenuItem;
    private LinearLayout mQualityWrapper;
    private final Runnable hideAnimationRunnable = new Runnable() {
        @Override
        public void run() {
            if (getActivity() != null)
                hideVideoInterface();
        }
    };
    private int originalCtrlToolbarPadding,
            originalMainToolbarPadding,
            vodLength = 0,
            currentProgress = 0,
            videoHeightBeforeChatOnly,
            fetchViewCountDelay = 1000 * 60, // A minute
            fetchChattersDelay = 1000 * 60; // 30 seco... Nah just kidding. Also a minute.

    private Integer triesForNextBest = 0;

    public static StreamFragment newInstance(Bundle args) {
        StreamFragment fragment = new StreamFragment();
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Finds and returns the TRUE width of the screen
     *
     * @return
     */
    public static int getScreenWidth(Activity activity) {
        if (activity != null) {
            Display display = activity.getWindowManager().getDefaultDisplay();
            DisplayMetrics metrics = new DisplayMetrics();
            Point size = new Point();
            int width, height;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInMultiWindowMode()) {
                    display.getMetrics(metrics);
                } else {
                    display.getRealMetrics(metrics);
                }

                width = metrics.widthPixels;
                height = metrics.heightPixels;
            } else {
                display.getSize(size);
                width = size.x;
                height = size.y;
            }

            return Math.max(width, height);
        }

        return 0;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        Bundle args = getArguments();
        setHasOptionsMenu(true);
        settings = new Settings(getActivity());

        if (args != null) {
            mChannelInfo = args.getParcelable(getString(R.string.stream_fragment_streamerInfo));
            vodId = args.getString(getString(R.string.stream_fragment_vod_id));
            vodLength = args.getInt(getString(R.string.stream_fragment_vod_length));
            autoPlay = args.getBoolean(getString(R.string.stream_fragment_autoplay));

            settings.setVodLength(vodId, vodLength);
        }

        final View mRootView = inflater.inflate(R.layout.fragment_stream, container, false);
        mRootView.requestLayout();

        // If the user has been in FULL SCREEN mode and presses the back button, we want to change the orientation to portrait.
        // As soon as the orientation has change we don't want to force the user to will be in portrait, so we "release" the request.
        if (getActivity().getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            isLandscape = true;
        }

        //  If no streamer info is available we cant show the stream.
        if (mChannelInfo == null) {
            if (getActivity() != null) {
                getActivity().finish();
            }
            return rootView;
        }

        mPlayer = ExoPlayerFactory.newSimpleInstance(getContext());

        rootView = (ViewGroup) mRootView;
        mToolbar = mRootView.findViewById(R.id.main_toolbar);
        mControlToolbar = mRootView.findViewById(R.id.control_toolbar_wrapper);
        mVideoWrapper = mRootView.findViewById(R.id.video_wrapper);
        mPlayerView = mRootView.findViewById(R.id.PlayerView);
        mVideoBackground = mRootView.findViewById(R.id.video_background);
        mPreview = mRootView.findViewById(R.id.preview);
        mQualityButton = mRootView.findViewById(R.id.settings_icon);
        mActivity = ((AppCompatActivity) getActivity());

        setPreviewAndCheckForSharedTransition();
        setupToolbar();
        setupSpinner();
        setupProfileBottomSheet();
        chatHidden = true;
        setupLandscapeChat();

        mVideoWrapper.setOnClickListener(v -> {
            delayAnimationHandler.removeCallbacks(hideAnimationRunnable);
            if (isVideoInterfaceShowing()) {
                hideVideoInterface();
                if (isDeviceBelowKitkat())
                    setAndroidUiMode();
            } else {
                showVideoInterface();

                // Show the navigation bar
                if (isLandscape && settings.getStreamPlayerShowNavigationBar() && Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                    View decorView = getActivity().getWindow().getDecorView();
                    decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN // Hide Status bar
                            | View.SYSTEM_UI_FLAG_IMMERSIVE);
                }

                if (isPlaying()) {
                    delayHiding();
                }

                Handler h = new Handler();
                h.postDelayed(() -> setAndroidUiMode(), HIDE_ANIMATION_DELAY);
            }
        });

        mPlayer.addListener(new Player.EventListener() {
            @Override
            public void onPlayerError(ExoPlaybackException error) {
                Log.e(LOG_TAG, "Something went wrong playing the stream: " + error);
                error.printStackTrace();
                playbackFailed();
            }

            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_BUFFERING) {
                    showVideoInterface();
                    mPlayerView.showController();
                } else {
                    hideVideoInterface();
                    mPlayerView.hideController();
                }
            }
        });

        mPlayerView.setControllerVisibilityListener(new PlayerControlView.VisibilityListener() {
            @Override
            public void onVisibilityChange(int visibility) {
                if (visibility == View.VISIBLE) {
                    showVideoInterface();
                } else {
                    hideVideoInterface();
                }
            }
        });
        mPlayerView.setPlayer(mPlayer);

        mRootView.setOnSystemUiVisibilityChangeListener(
                visibility -> {
                    if (visibility == 0) {
                        showVideoInterface();
                        delayHiding();
                        Handler h = new Handler();
                        h.postDelayed(this::setAndroidUiMode, HIDE_ANIMATION_DELAY);
                    }
                }
        );


        progressHandler.postDelayed(progressRunnable, 1000);

        keepScreenOn();

        if (autoPlay || vodId != null) {
            startStreamWithQuality(settings.getPrefStreamQuality());
        }

        headsetIntentReceiver = new HeadsetPlugIntentReceiver();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getActivity().registerReceiver(headsetIntentReceiver, new IntentFilter(AudioManager.ACTION_HEADSET_PLUG));
        }

        return mRootView;
    }

    /**
     * Hides the preview image and updates the state
     */
    private void hidePreview() {
        Service.bringToBack(mPreview);
        previewInbackGround = true;
    }

    public void backPressed() {
        mPlayerView.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Checks the orientation of the screen
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            isLandscape = true;
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            isLandscape = false;
        }

        updateUI();
    }

    @Override
    public void onResume() {
        super.onResume();

        originalMainToolbarPadding = mToolbar.getPaddingRight();
        originalCtrlToolbarPadding = mControlToolbar.getPaddingRight();

        startStreamWithQuality(settings.getPrefStreamQuality());

        if (!chatOnlyViewVisible) {
            showVideoInterface();
            updateUI();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        Log.d(LOG_TAG, "Stream Fragment paused");
        hasPaused = true;
    }

    @Override
    public void onStop() {
        Log.d(LOG_TAG, "Stream Fragment Stopped");
        super.onStop();

        if (!castingViewVisible && !audioViewVisible) {
            pauseStream();
        }

        if (vodId != null) {
            settings.setVodProgress(vodId, currentProgress);
            settings.setVodLength(vodId, vodLength);
            Log.d(LOG_TAG, "Saving Current progress: " + currentProgress);
        }

    }

    @Override
    public void onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && getActivity() != null) {
            getActivity().unregisterReceiver(headsetIntentReceiver);
        }
        Log.d(LOG_TAG, "Destroying");
        if (fetchViewCountRunnable != null) {
            fetchViewCountHandler.removeCallbacks(fetchViewCountRunnable);
        }

        progressHandler.removeCallbacks(progressRunnable);
        super.onDestroy();
    }

    /**
     * Sets up the show chat button.
     * Sets the correct visibility and the onclicklistener
     */
    private void profileButtonClicked() {
        mProfileBottomSheet.show();
    }

    private void sleepButtonClicked() {
        if (sleepTimer == null) {
            sleepTimer = new SleepTimer(new SleepTimer.SleepTimerDelegate() {
                @Override
                public void onTimesUp() {
                    pauseStream();
                }

                @Override
                public void onStart(String message) {
                    showSnackbar(message, SNACKBAR_SHOW_DURATION);
                }

                @Override
                public void onStop(String message) {
                    showSnackbar(message, SNACKBAR_SHOW_DURATION);
                }
            }, getContext());
        }

        sleepTimer.show(getActivity());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (!embedded) {
            optionsMenuItem = menu.findItem(R.id.menu_item_options);
            optionsMenuItem.setVisible(false);
            optionsMenuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem) {
                    if (mQualityButton != null) {
                        mQualityButton.performClick();
                    }
                    return true;
                }
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!isVideoInterfaceShowing()) {
            mVideoWrapper.performClick();
            return true;
        }

        switch (item.getItemId()) {
            case R.id.menu_item_sleep:
                sleepButtonClicked();
                return true;
            case R.id.menu_item_profile:
                profileButtonClicked();
                return true;
            case R.id.menu_item_external:
                playWithExternalPlayer();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setupLandscapeChat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && vodId == null && settings.isChatLandscapeSwipable() && settings.isChatInLandscapeEnabled()) {
            final int width = getScreenWidth(getActivity());

            final GestureDetector gd = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    Log.d(LOG_TAG, "Got two touch");
                    if (chatHidden) {
                        showLandscapeChat();
                    } else {
                        hideLandscapeChat();
                    }
                    rootView.invalidate();

                    return true;
                }

                @Override
                public boolean onDoubleTapEvent(MotionEvent e) {
                    return true;
                }
            });
            mPlayerView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    Log.d(LOG_TAG, "Got one touch");
                    return gd.onTouchEvent(motionEvent);
                }
            });
        }

    }

    /**
     * Show the landscape chat with an animation that changes the width of the videoview wrapper.
     * The ShowChatButton is also rotated
     */
    private void showLandscapeChat() {
        chatHidden = false;
        int width = getScreenWidth(getActivity());
        ResizeWidthAnimation resizeWidthAnimation = new ResizeWidthAnimation(mVideoWrapper, (width - getLandscapeChatTargetWidth()));
        resizeWidthAnimation.setDuration(250);
        mVideoWrapper.startAnimation(resizeWidthAnimation);
    }

    /**
     * hides the landscape chat with an animation that changes the width of the videoview wrapper to the width of the screen.
     * The ShowChatButton is also rotated
     */
    private void hideLandscapeChat() {
        chatHidden = true;
        int width = getScreenWidth(getActivity());
        ResizeWidthAnimation resizeWidthAnimation = new ResizeWidthAnimation(mVideoWrapper, width);
        resizeWidthAnimation.setDuration(250);
        mVideoWrapper.startAnimation(resizeWidthAnimation);
    }

    public int getLandscapeChatTargetWidth() {
        return (int) (getScreenWidth(getActivity()) * (settings.getChatLandscapeWidth() / 100.0));
    }

    private String getBestCastQuality(Map<String, String> castQualities, String quality, Integer numberOfTries) {
        if (numberOfTries > GetLiveStreamURL.CAST_QUALITIES.length - 1) {
            return null;
        }

        if (quality.equals(GetLiveStreamURL.QUALITY_AUTO) || quality.equals(GetLiveStreamURL.QUALITY_AUDIO_ONLY)) {
            quality = GetLiveStreamURL.QUALITY_MEDIUM;
        }

        if (castQualities.containsKey(quality)) {
            return quality;
        } else {
            numberOfTries++;
            List<String> qualityList = Arrays.asList(GetLiveStreamURL.CAST_QUALITIES);
            int next = qualityList.indexOf(quality) - 1;
            if (next < 0) {
                quality = GetLiveStreamURL.QUALITY_SOURCE;
            } else {
                quality = qualityList.get(next);
            }
            return getBestCastQuality(castQualities, quality, numberOfTries);
        }
    }

    /**
     * Checks if the activity was started with a shared view in high API levels.
     */
    private void setPreviewAndCheckForSharedTransition() {
        final Intent intent = getActivity().getIntent();
        if (intent.hasExtra(getString(R.string.stream_preview_url))) {
            String imageUrl = intent.getStringExtra(getString(R.string.stream_preview_url));

            if (imageUrl == null || imageUrl.isEmpty()) {
                return;
            }

            RequestCreator creator = Picasso.get().load(imageUrl + "?ts=" +
                    System.currentTimeMillis());
            Target target = new Target() {
                @Override
                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                    mPreview.setImageBitmap(bitmap);
                }

                public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                }

                public void onPrepareLoad(Drawable placeHolderDrawable) {
                }
            };
            creator.into(target);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && intent.getBooleanExtra(getString(R.string.stream_shared_transition), false)) {
            mPreview.setTransitionName(getString(R.string.stream_preview_transition));

            final View[] viewsToHide = {mPlayerView, mToolbar, mControlToolbar, mVideoBackground};
            for (View view : viewsToHide) {
                view.setVisibility(View.INVISIBLE);
            }

            getActivity().getWindow().getEnterTransition().addListener(new Transition.TransitionListener() {

                @Override
                public void onTransitionEnd(Transition transition) {
                    TransitionManager.beginDelayedTransition(
                            mVideoWrapper,
                            new com.transitionseverywhere.Fade()
                                    .setDuration(340)
                                    .excludeTarget(mPlayerView, true)
                                    .excludeTarget(mPreview, true)
                    );

                    for (View view : viewsToHide) {
                        view.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onTransitionCancel(Transition transition) {
                    onTransitionEnd(transition);
                }

                public void onTransitionStart(Transition transition) {
                }

                public void onTransitionPause(Transition transition) {
                }

                public void onTransitionResume(Transition transition) {
                }
            });
        }

    }

    /**
     * Checks if the user is currently in progress of watching a VOD. If so seek forward to where the user left off.
     */
    private void checkVodProgress() {
        if (vodId != null) {
            if (currentProgress == 0) {
                currentProgress = settings.getVodProgress(vodId);
                mPlayerView.getPlayer().seekTo(currentProgress * 1000);
                Log.d(LOG_TAG, "Current progress: " + currentProgress);
            } else {
                mPlayerView.getPlayer().seekTo(currentProgress * 1000);
                Log.d(LOG_TAG, "Seeking to " + currentProgress * 1000);
            }
        }
    }

    /**
     * Call to make sure the UI is shown correctly
     */
    private void updateUI() {
        setAndroidUiMode();
        keepControlIconsInView();
        setVideoViewLayout();
    }

    /**
     * This makes sure that the System UI automatically hides when the user changes focus by opening the navigation drawer.
     *
     * @param hasFocus
     */
    public void onWindowFocusChanged(boolean hasFocus) {
        Log.d(LOG_TAG, "WindowFocusChanged to " + hasFocus + " - isLandscape " + isLandscape);
        setAndroidUiMode();
    }

    /**
     * Sets the System UI visibility so that the status- and navigation bar automatically hides if the app is current in fullscreen or in landscape.
     * But they will automatically show when the user touches the screen.
     */
    private void setAndroidUiMode() {
        if (getActivity() == null) {
            return;
        }

        View decorView = getActivity().getWindow().getDecorView();
        if (isLandscape || isFullscreen) {
            Log.d(LOG_TAG, "Hiding navigation");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // Hide navigation bar
                        | View.SYSTEM_UI_FLAG_FULLSCREEN // Hide Status bar
                        | View.SYSTEM_UI_FLAG_IMMERSIVE);
            } else {
                decorView.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // Hide navigation bar
                                | View.SYSTEM_UI_FLAG_FULLSCREEN // Hide Status bar

                );
            }

        } else {
            Log.d(LOG_TAG, "Showing navigation");
            decorView.setSystemUiVisibility(0); // Remove all flags.
        }
    }

    private void setVideoViewLayout() {
        DisplayMetrics displaymetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        int width = displaymetrics.widthPixels;
        RelativeLayout.LayoutParams layoutWrapper = (RelativeLayout.LayoutParams) mVideoWrapper.getLayoutParams();
        if (isLandscape) {
            layoutWrapper.height = FrameLayout.LayoutParams.MATCH_PARENT;
        } else {
            layoutWrapper.height = (int) Math.ceil(1.0 * width / (16.0 / 9.0));
        }
        layoutWrapper.width = RelativeLayout.LayoutParams.MATCH_PARENT;
        mVideoWrapper.setLayoutParams(layoutWrapper);
    }

    /**
     * Delays the hiding for the Video control interface.
     */
    private void delayHiding() {
        delayAnimationHandler.postDelayed(hideAnimationRunnable, HIDE_ANIMATION_DELAY);
    }

    /**
     * Checks if the video interface is fully showing
     *
     * @return
     */
    public boolean isVideoInterfaceShowing() {
        return mControlToolbar.getAlpha() == 1f;
    }

    /**
     * Hides the video control interface with animations
     */
    private void hideVideoInterface() {
        if (mToolbar != null && !audioViewVisible && !chatOnlyViewVisible) {
            mToolbar.animate().alpha(0f).setInterpolator(new AccelerateDecelerateInterpolator()).start();
            mControlToolbar.animate().alpha(0f).setInterpolator(new AccelerateDecelerateInterpolator()).start();
        }
    }

    /**
     * Shows the video control interface with animations
     */
    protected void showVideoInterface() {
        int MaintoolbarY = 0, CtrlToolbarY = 0;
        if ((isFullscreen || isLandscape) && isDeviceBelowKitkat()) {
            MaintoolbarY = getStatusBarHeight();
        }
        if ((isFullscreen || isLandscape) && Service.isTablet(getContext()) && isDeviceBelowKitkat()) {
            CtrlToolbarY = getNavigationBarHeight();
        }

        mControlToolbar.setTranslationY(-CtrlToolbarY);
        mControlToolbar.animate().alpha(1f).start();
        mToolbar.setTranslationY(MaintoolbarY);
        mToolbar.animate().alpha(1f).start();
    }

    /**
     * Keeps the rightmost icons on the toolbars in view when the device is in landscape.
     * Otherwise the icons would be covered my the navigationbar
     */
    private void keepControlIconsInView() {
        if (isDeviceBelowKitkat() || settings.getStreamPlayerShowNavigationBar()) {
            int ctrlPadding = originalCtrlToolbarPadding;
            int mainPadding = originalMainToolbarPadding;
            int delta = getNavigationBarHeight();

            if ((isFullscreen || isLandscape) && !Service.isTablet(getContext())) {
                ctrlPadding += delta;
                mainPadding += delta;
            }

            mToolbar.setPadding(0, 0, mainPadding, 0);
            mControlToolbar.setPadding(0, 0, ctrlPadding, 0);
        }
    }

    /**
     * Returns the height of the statusbar
     */
    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    /**
     * Returns the height of the navigation bar.
     * If the device doesn't have a navigaion bar (Such as Samsung Galaxy devices) the height is 0
     */
    private int getNavigationBarHeight() {
        Resources resources = getResources();
        int resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return resources.getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    /**
     * If the device isn't currently in fullscreen a request is sent to turn the device into landscape.
     * Otherwise if the device is in fullscreen then is releases the lock by requesting for an unspecified orientation
     * After and update to the VideoView layout is requested.
     */
    public void toggleFullscreen() {
        isFullscreen = !isFullscreen;
        if (isFullscreen) {
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else {
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        setVideoViewLayout();
    }

    /**
     * Pauses and stops the playback of the Video Stream
     */
    private void pauseStream() {
        Log.d(LOG_TAG, "Chat, pausing stream");

        delayAnimationHandler.removeCallbacks(hideAnimationRunnable);

        if (isAudioOnlyModeEnabled()) {
            Log.d(LOG_TAG, "Pausing audio");
        } else {
            mPlayerView.getPlayer().setPlayWhenReady(false);
        }
        releaseScreenOn();
    }

    /**
     * Goes forward to live and starts plackback of the VideoView
     */
    private void resumeStream() {
        if (isAudioOnlyModeEnabled()) {
        } else {
            if (vodId == null) {
                mPlayerView.getPlayer().setPlayWhenReady(true); // Go forward to  live
            }

            mPlayerView.getPlayer().setPlayWhenReady(true);
        }

        checkVodProgress();
        keepScreenOn();
    }

    /**
     * Tries playing stream with a quality.
     * If the given quality doesn't exist for the stream the try the next best quality option.
     * If no Quality URLS have yet been created then try to start stream with an aync task.
     *
     * @param quality
     */
    protected void startStreamWithQuality(String quality) {
        if (qualityURLs == null) {
            startStreamWithTask();
        } else {
            if (qualityURLs.containsKey(quality)) {
                if (chatOnlyViewVisible || audioViewVisible) {
                    return;
                }

                playUrl(qualityURLs.get(quality));
                showQualities();
                updateSelectedQuality(quality);
                Log.d(LOG_TAG, "Starting Stream With a quality on " + quality + " for " + mChannelInfo.getDisplayName());
                Log.d(LOG_TAG, "URLS: " + qualityURLs.keySet().toString());
            } else if (!qualityURLs.isEmpty()) {
                Log.d(LOG_TAG, "Quality unavailable for this stream -  " + quality + ". Trying next best");
                tryNextBestQuality(quality);
            }
        }
    }

    /**
     * Starts and Aync task that fetches all available Stream URLs for a stream,
     * then tries to start stream with the latest user defined quality setting.
     * If no URLs are available for the stream, the user is notified.
     */
    private void startStreamWithTask() {
        GetLiveStreamURL.AsyncResponse callback = new GetLiveStreamURL.AsyncResponse() {
            @Override
            public void finished(HashMap<String, String> url) {
                try {
                    if (!url.isEmpty()) {
                        updateQualitySelections(url.keySet());
                        qualityURLs = url;

                        startStreamWithQuality(new Settings(getContext()).getPrefStreamQuality());
                    } else {
                        playbackFailed();
                        return;
                    }
                } catch (IllegalStateException | NullPointerException e) {
                    e.printStackTrace();
                }
            }
        };

        final Settings settings = new Settings(getActivity());
        if (vodId == null) {
            GetLiveStreamURL task = new GetLiveStreamURL(callback);
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mChannelInfo.getStreamerName(),
                    settings.getGeneralTwitchAccessToken());
        } else {
            GetLiveStreamURL task = new GetVODStreamURL(callback);
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, vodId.substring(1));
        }
    }

    /**
     * Connects to the Twitch API to fetch the live stream url and quality selection urls.
     * If the task is successful the quality selector views' click listeners will be updated.
     */
    private void updateQualitySelectorsWithTask() {
        GetLiveStreamURL.AsyncResponse delegate = new GetLiveStreamURL.AsyncResponse() {
            @Override
            public void finished(HashMap<String, String> url) {
                try {
                    if (!url.isEmpty()) {
                        updateQualitySelections(url.keySet());
                        qualityURLs = url;
                    }
                } catch (IllegalStateException | NullPointerException e) {
                    e.printStackTrace();
                }
            }
        };

        if (vodId == null) {
            GetLiveStreamURL task = new GetLiveStreamURL(delegate);
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mChannelInfo.getStreamerName());
        } else {
            GetLiveStreamURL task = new GetVODStreamURL(delegate);
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, vodId.substring(1));
        }
    }

    /**
     * Stops the buffering and notifies the user that the stream could not be played
     */
    private void playbackFailed() {
        // Zero out qualityURLs in order to force the next attempt to stream
        // to re-fetch the stream URLs
        qualityURLs = null;
    }

    private void showSnackbar(String message, int duration) {
        if (getActivity() != null && !isDetached()) {
            View mainView = ((StreamActivity) getActivity()).getMainContentLayout();

            if ((snackbar == null || !snackbar.isShown()) && mainView != null) {
                snackbar = Snackbar.make(mainView, message, duration);
                snackbar.show();
            }
        }

    }

    private void tryNextBestQuality(String quality) {
        if (triesForNextBest < GetLiveStreamURL.QUALITIES.length - 1) { // Subtract 1 as we don't count AUDIO ONLY as a quality
            triesForNextBest++;
            List<String> qualityList = Arrays.asList(GetLiveStreamURL.QUALITIES);
            int next = qualityList.indexOf(quality) - 1;
            if (next < 0) {
                startStreamWithQuality(GetLiveStreamURL.QUALITY_SOURCE);
            } else {
                startStreamWithQuality(qualityList.get(next));
            }
        } else {
            playbackFailed();
        }
    }

    /**
     * Sets the URL to the VideoView and ChromeCast and starts playback.
     *
     * @param url
     */
    private void playUrl(String url) {
        final DataSource.Factory dsf = new DefaultDataSourceFactory(getContext(),
                Util.getUserAgent(getContext(), "Twitch.tv"));
        final MediaSource src = new HlsMediaSource.Factory(dsf).createMediaSource(
                Uri.parse(url)
        );
        mPlayer.prepare(src);
        resumeStream();
    }

    private void playWithExternalPlayer() {
        Toast errorToast = Toast.makeText(getContext(), R.string.error_external_playback_failed, Toast.LENGTH_LONG);
        if (qualityURLs == null) {
            errorToast.show();
            return;
        }

        String castQuality = getBestCastQuality(qualityURLs, settings.getPrefStreamQuality(), 0);
        if (castQuality == null) {
            errorToast.show();
            return;
        }

        updateSelectedQuality(castQuality);
        String url = qualityURLs.get(castQuality);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse(url), "video/*");
        startActivity(Intent.createChooser(intent, getString(R.string.stream_external_play_using)));
    }

    /**
     * Notifies the system that the screen though not timeout and fade to black.
     */
    private void keepScreenOn() {
        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /**
     * Notifies the system that the screen can now time out.
     */
    private void releaseScreenOn() {
        getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void updateSelectedQuality(String quality) {
        //TODO: Bad design
        if (quality == null) {
            resetQualityViewBackground(null);
        } else if (quality.equals(GetLiveStreamURL.QUALITY_AUTO)) {
            resetQualityViewBackground(auto);
        } else if (quality.equals(GetLiveStreamURL.QUALITY_SOURCE)) {
            resetQualityViewBackground(source);
        } else if (quality.equals(GetLiveStreamURL.QUALITY_HIGH)) {
            resetQualityViewBackground(high);
        } else if (quality.equals(GetLiveStreamURL.QUALITY_MEDIUM)) {
            resetQualityViewBackground(medium);
        } else if (quality.equals(GetLiveStreamURL.QUALITY_LOW)) {
            resetQualityViewBackground(low);
        } else if (quality.equals(GetLiveStreamURL.QUALITY_MOBILE)) {
            resetQualityViewBackground(mobile);
        }
    }

    /**
     * Resets the background color of all the select quality views in the bottom dialog
     */
    private void resetQualityViewBackground(TextView selected) {
        TextView[] textViews = {auto, source, high, medium, low, mobile};
        for (TextView v : textViews) {
            if (v.equals(selected)) {
                v.setBackgroundColor(Service.getColorAttribute(R.attr.navigationDrawerHighlighted, R.color.grey_300, getContext()));
            } else {
                v.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.transparent));
            }
        }
    }

    /**
     * Adds the available qualities for a stream to the spinner menu
     *
     * @param availableQualities
     */
    private void updateQualitySelections(Set<String> availableQualities) {
        setupViewQuality(auto, GetLiveStreamURL.QUALITY_AUTO, availableQualities);
        setupViewQuality(source, GetLiveStreamURL.QUALITY_SOURCE, availableQualities);
        setupViewQuality(high, GetLiveStreamURL.QUALITY_HIGH, availableQualities);
        setupViewQuality(medium, GetLiveStreamURL.QUALITY_MEDIUM, availableQualities);
        setupViewQuality(low, GetLiveStreamURL.QUALITY_LOW, availableQualities);
        setupViewQuality(mobile, GetLiveStreamURL.QUALITY_MOBILE, availableQualities);
    }

    /**
     * Sets up a select quality view in the bottom dialog. If the quality doesn't exist in the availableQualities the view is hidden.
     * If it exists an onClick listener is set
     *
     * @param view               The select quality view
     * @param quality            The quality name corresponding to the view
     * @param availableQualities Set of available qualities
     */
    private void setupViewQuality(TextView view, String quality, Set<String> availableQualities) {
        if (!availableQualities.contains(quality)) {
            view.setVisibility(View.GONE);
        } else {
            setQualityOnClick(view);
        }
    }

    /**
     * Sets an OnClickListener on a select quality view (From bottom dialog).
     * The Listener starts the stream with a new quality setting and updates the background for the select quality views in the bottom dialog
     *
     * @param qualityView
     */
    private void setQualityOnClick(final TextView qualityView) {
        qualityView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // We zero out qualityURLs here in order to force a refresh
                // of all of the stream URLs, any time we re-select the quality
                qualityURLs = null;

                String quality = supportedQualities.get(qualityView.getText());
                settings.setPrefStreamQuality(quality);
                startStreamWithQuality(quality);
                resetQualityViewBackground(qualityView);
                mQualityBottomSheet.dismiss();
            }
        });
    }

    private BottomSheetBehavior getDefaultBottomSheetBehaviour(View bottomSheetView) {
        BottomSheetBehavior behavior = BottomSheetBehavior.from((View) bottomSheetView.getParent());
        behavior.setPeekHeight(getContext().getResources().getDisplayMetrics().heightPixels / 3);
        return behavior;
    }

    private void setupProfileBottomSheet() {
        View v = LayoutInflater.from(getContext()).inflate(R.layout.stream_profile_preview, null);
        mProfileBottomSheet = new BottomSheetDialog(getContext());
        mProfileBottomSheet.setContentView(v);
        final BottomSheetBehavior behavior = getDefaultBottomSheetBehaviour(v);

        mProfileBottomSheet.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });

        TextView mNameView = mProfileBottomSheet.findViewById(R.id.twitch_name);
        TextView mFollowers = mProfileBottomSheet.findViewById(R.id.txt_followers);
        ImageView mFollowButton = mProfileBottomSheet.findViewById(R.id.follow_unfollow_icon);
        ImageView mFullProfileButton = mProfileBottomSheet.findViewById(R.id.full_profile_icon);
        RecyclerView mPanelsRecyclerView = mProfileBottomSheet.findViewById(R.id.panel_recyclerview);

        mNameView.setText(mChannelInfo.getDisplayName());
        mFollowers.setText(mChannelInfo.getFollowers() + "");

        mFullProfileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mProfileBottomSheet.dismiss();

                final Intent intent = new Intent(getContext(), ChannelActivity.class);
                intent.putExtra(getContext().getResources().getString(R.string.channel_info_intent_object), mChannelInfo);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                getContext().startActivity(intent);
            }
        });

        setupFollowButton(mFollowButton);
        setupPanels(mPanelsRecyclerView);
    }

    private void setupFollowButton(final ImageView imageView) {
        final FollowHandler mFollowHandler = new FollowHandler(
                mChannelInfo,
                getContext(),
                new FollowHandler.Delegate() {
                    @Override
                    public void streamerIsFollowed() {
                        updateFollowIcon(imageView, true);
                    }

                    @Override
                    public void streamerIsNotFollowed() {
                        updateFollowIcon(imageView, false);
                    }

                    @Override
                    public void userIsNotLoggedIn() {
                        imageView.setVisibility(View.GONE);
                    }

                    @Override
                    public void followSuccess() {
                    }

                    @Override
                    public void followFailure() {
                    }

                    @Override
                    public void unfollowSuccess() {
                    }

                    @Override
                    public void unfollowFailure() {
                    }
                }
        );

        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final boolean isFollowed = mFollowHandler.isStreamerFollowed();
                if (isFollowed) {
                    mFollowHandler.unfollowStreamer();
                } else {
                    mFollowHandler.followStreamer();
                }

                final int ANIMATION_DURATION = 240;

                imageView.animate()
                        .setDuration(ANIMATION_DURATION)
                        .alpha(0f)
                        .start();

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        updateFollowIcon(imageView, !isFollowed);
                        imageView.animate().alpha(1f).setDuration(ANIMATION_DURATION).start();
                    }
                }, ANIMATION_DURATION);
            }
        });
    }

    private void updateFollowIcon(ImageView imageView, boolean isFollowing) {
        @DrawableRes int imageRes = isFollowing
                ? R.drawable.ic_heart_broken_24dp
                : R.drawable.ic_heart_24dp;
        imageView.setImageResource(imageRes);
    }

    private void setupPanels(RecyclerView recyclerView) {
        final PanelAdapter mPanelAdapter = new PanelAdapter(getActivity());
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.VERTICAL, false));
        recyclerView.setAdapter(mPanelAdapter);

        GetPanelsTask mTask = new GetPanelsTask(mChannelInfo.getStreamerName(), new GetPanelsTask.Delegate() {
            @Override
            public void onPanelsFetched(List<Panel> result) {
                mPanelAdapter.addPanels(result);
            }
        });
        mTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Setups the Quality Select spinner.
     * Automatically hides the text of the selected Quality
     */
    private void setupSpinner() {
        supportedQualities = HashBiMap.create();
        supportedQualities.put(getResources().getString(R.string.quality_auto), GetLiveStreamURL.QUALITY_AUTO);
        supportedQualities.put(getResources().getString(R.string.quality_source), GetLiveStreamURL.QUALITY_SOURCE);
        supportedQualities.put(getResources().getString(R.string.quality_high), GetLiveStreamURL.QUALITY_HIGH);
        supportedQualities.put(getResources().getString(R.string.quality_medium), GetLiveStreamURL.QUALITY_MEDIUM);
        supportedQualities.put(getResources().getString(R.string.quality_low), GetLiveStreamURL.QUALITY_LOW);
        supportedQualities.put(getResources().getString(R.string.quality_mobile), GetLiveStreamURL.QUALITY_MOBILE);

        mQualityButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mQualityBottomSheet.show();
            }
        });

        View v = LayoutInflater.from(getContext()).inflate(R.layout.stream_settings, null);
        mQualityBottomSheet = new BottomSheetDialog(getContext());
        mQualityBottomSheet.setContentView(v);

        final BottomSheetBehavior behavior = getDefaultBottomSheetBehaviour(v);

        mQualityBottomSheet.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });

        auto = mQualityBottomSheet.findViewById(R.id.auto);
        source = mQualityBottomSheet.findViewById(R.id.source);
        high = mQualityBottomSheet.findViewById(R.id.high);
        medium = mQualityBottomSheet.findViewById(R.id.medium);
        low = mQualityBottomSheet.findViewById(R.id.low);
        mobile = mQualityBottomSheet.findViewById(R.id.mobile);

        mQualityWrapper = mQualityBottomSheet.findViewById(R.id.quality_wrapper);
        mChatOnlySelector = mQualityBottomSheet.findViewById(R.id.chat_only_selector);
        TextView optionsTitle = mQualityBottomSheet.findViewById(R.id.options_text);

        if (optionsTitle != null) {
            optionsTitle.setVisibility(View.VISIBLE);
        }

        if (vodId == null) {
            mChatOnlySelector.setVisibility(View.VISIBLE);
        }
    }

    private boolean isAudioOnlyModeEnabled() {
        return false;
    }

    /**
     * Setups the toolbar by giving it a bit of extra right padding (To make sure the icons are 16dp from right)
     * Also adds the main toolbar as the support actionbar
     */
    private void setupToolbar() {
        mToolbar.setPadding(0, 0, Service.dpToPixels(getActivity(), 5), 0);
        setHasOptionsMenu(true);
        mActivity.setSupportActionBar(mToolbar);
        mActivity.getSupportActionBar().setTitle(mChannelInfo.getDisplayName());
        mActivity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        //mActivity.getSupportActionBar().setDefaultDisplayHomeAsUpEnabled(true);
        mToolbar.bringToFront();
    }

    /**
     * Checks if the device is below SDK API 19 (Kitkat)
     *
     * @return the result
     */
    private boolean isDeviceBelowKitkat() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT;
    }

    private void showQualities() {
        mQualityWrapper.setVisibility(View.VISIBLE);
    }

    private void hideQualities() {
        mQualityWrapper.setVisibility(View.GONE);
    }

    /**
     * Broadcast class for detecting when the user plugs or unplug a headset.
     */
    private class HeadsetPlugIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
                int state = intent.getIntExtra("state", -1);
                switch (state) {
                    case 0:
                        if (isPlaying()) {
                            Log.d(LOG_TAG, "Chat, pausing from headsetPlug");
                            showVideoInterface();
                            pauseStream();
                        }
                        break;
                    case 1:
                        showVideoInterface();
                        break;
                    default:

                }
            }
        }
    }

    private boolean isPlaying() {
        return mPlayer != null && mPlayer.getPlayWhenReady();
    }
}
