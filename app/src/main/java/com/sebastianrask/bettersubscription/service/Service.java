package com.sebastianrask.bettersubscription.service;

import android.animation.Animator;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;

import com.sebastianrask.bettersubscription.R;
import com.sebastianrask.bettersubscription.activities.main.FeaturedStreamsActivity;
import com.sebastianrask.bettersubscription.activities.main.MyChannelsActivity;
import com.sebastianrask.bettersubscription.activities.main.MyGamesActivity;
import com.sebastianrask.bettersubscription.activities.main.MyStreamsActivity;
import com.sebastianrask.bettersubscription.activities.main.TopGamesActivity;
import com.sebastianrask.bettersubscription.activities.main.TopStreamsActivity;
import com.sebastianrask.bettersubscription.misc.SecretKeys;
import com.sebastianrask.bettersubscription.model.ChannelInfo;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;
import java.util.Random;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorRes;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.customview.widget.ViewDragHelper;
import androidx.drawerlayout.widget.DrawerLayout;

/**
 * Created by Sebastian Rask on 12-02-2015.
 * Class made purely for adding utility methods for other classes
 */
// TODO: Split this service out to multiple more cohesive service classes
public class Service {

    private static final String LOG_TAG = Service.class.getSimpleName();

    /**
     * Returns the Twitch Client ID
     *
     * @return The ID
     */
    public static String getApplicationClientID() {
        return SecretKeys.TWITCH_CLIENT_ID;
    }

    public static String getErrorEmote() {
        String[] emotes = {"('.')", "('x')", "(>_<)", "(>.<)", "(;-;)", "\\(o_o)/", "(O_o)", "(o_0)", "(≥o≤)", "(≥o≤)", "(·.·)", "(·_·)"};
        Random rnd = new Random();
        return emotes[rnd.nextInt(emotes.length - 1)];
    }

    /**
     * Checks if two calendar objects have the same day of the year
     *
     * @param one I think it's pretty obvious
     * @param two what these two objects are for
     * @return True if the day is the same, otherwise false
     */
    public static boolean isCalendarSameDay(Calendar one, Calendar two) {
        return one.get(Calendar.YEAR) == two.get(Calendar.YEAR) && one.get(Calendar.DAY_OF_YEAR) == two.get(Calendar.DAY_OF_YEAR);
    }

    /**
     * Makes a timestamp from a length in seconds.
     *
     * @param videoLengthInSeconds Length in seconds
     * @return
     */
    public static String calculateTwitchVideoLength(int videoLengthInSeconds) {
        String result = "";
        double hours = videoLengthInSeconds / 60.0 / 60.0,
                minutes,
                seconds;

        double minutesAsDecimalHours = hours - Math.floor(hours);
        minutes = 60.0 * minutesAsDecimalHours;
        double secondsAsDecimalMinutes = minutes - Math.floor(minutes);
        seconds = 60.0 * secondsAsDecimalMinutes;

        if (hours >= 1) {
            result = ((int) Math.floor(hours)) + ":";
        }
        if (minutes >= 1 || hours >= 1) {
            result += numberToTime(minutes) + ":";
        }
        result += numberToTime(Math.round(seconds));

        return result;
    }

    /**
     * Converts Double to time. f.eks. 4.5 becomes "04"
     */
    public static String numberToTime(double time) {
        int timeInt = ((int) Math.floor(time));

        if (timeInt < 10) {
            return "0" + timeInt;
        } else {
            return "" + timeInt;
        }
    }

    public static Bitmap removeBlackBars(Bitmap bitmap) {
        final int BLACKBARS_SIZE_PX = 30;
        return Bitmap.createBitmap(bitmap, 0, BLACKBARS_SIZE_PX, bitmap.getWidth(), bitmap.getHeight() - BLACKBARS_SIZE_PX * 2);
    }

    /**
     * Creates a bitmap with rounded corners.
     *
     * @param bitmap The bitmap
     * @param i      the corner radius in pixels
     * @return The bitmap with rounded corners
     */
    public static Bitmap getRoundedCornerBitmap(Bitmap bitmap, int i) {
        if (bitmap == null) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        }

        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(),
                bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        final RectF rectF = new RectF(rect);

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawRoundRect(rectF, (float) i, (float) i, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);

        return output;
    }

    /**
     * Returns the class related to the user-selected startup page
     *
     * @param context The Context from which the method is called
     * @return The Class of the startup activity
     */
    public static Class getClassFromStartPageTitle(Context context, String title) {
        Class result = MyStreamsActivity.class;
        if (title.equals(context.getString(R.string.navigation_drawer_featured_title))) {
            result = FeaturedStreamsActivity.class;
        } else if (title.equals(context.getString(R.string.navigation_drawer_follows_title))) {
            result = MyChannelsActivity.class;
        } else if (title.equals(context.getString(R.string.navigation_drawer_my_games_title))) {
            result = MyGamesActivity.class;
        } else if (title.equals(context.getString(R.string.navigation_drawer_top_streams_title))) {
            result = TopStreamsActivity.class;
        } else if (title.equals(context.getString(R.string.navigation_drawer_top_games_title))) {
            result = TopGamesActivity.class;
        }

        return result;
    }

    /**
     * Returns an intent with the right destination activity for when the user is logged in.
     *
     * @param context The context from which the method is called
     * @return The intent
     */
    public static Intent getLoggedInIntent(Context context) {
        Class startPageClass = getClassFromStartPageTitle(context, new Settings(context).getStartPage());
        return new Intent(context, startPageClass);
    }

    /**
     * Returns an intent with the right destination activity for when the user is NOT logged in.
     *
     * @param context The context from which the method is called
     * @return The intent
     */

    public static Intent getNotLoggedInIntent(Context context) {
        Settings settings = new Settings(context);
        Class startPageClass = getClassFromStartPageTitle(context, settings.getStartPage());
        if (startPageClass == MyStreamsActivity.class ||
                startPageClass == MyGamesActivity.class ||
                startPageClass == MyChannelsActivity.class) {
            startPageClass = getClassFromStartPageTitle(context, settings.getDefaultNotLoggedInStartUpPageTitle());
        }
        return new Intent(context, startPageClass);
    }

    /**
     * Animates the background color of a view from one color to another color.
     *
     * @param v         The view to animate
     * @param toColor   The To Color
     * @param fromColor The From Color
     * @param duration  The Duration of the animation
     * @return the animator
     */
    public static Animator animateBackgroundColorChange(View v, int toColor, int fromColor, int duration) {
        ObjectAnimator colorFade = ObjectAnimator.ofObject(v, "backgroundColor", new ArgbEvaluator(), fromColor, toColor);
        colorFade.setDuration(duration);
        colorFade.start();
        return colorFade;
    }

    /**
     * Finds and returns an attribute color. If it was not found the method returns the default color
     */
    public static int getColorAttribute(@AttrRes int attribute, @ColorRes int defaultColor, Context context) {
        TypedValue a = new TypedValue();
        context.getTheme().resolveAttribute(attribute, a, true);
        if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT && a.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return a.data;
        } else {
            return ContextCompat.getColor(context, defaultColor);
        }
    }

    /**
     * @param view         The view to get the color from
     * @param defaultColor The color to return if the view's background isn't a ColorDrawable
     * @return The color
     */
    public static int getBackgroundColorFromView(View view, int defaultColor) {
        int color = defaultColor;
        Drawable background = view.getBackground();
        if (background instanceof ColorDrawable) {
            color = ((ColorDrawable) background).getColor();
        }

        return color;
    }

    /**
     * Decodes a byte array to a bitmap and returns it.
     */
    public static Bitmap getBitmapFromByteArray(byte[] bytes) {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    /**
     * Creates a byte-array for a drawable and returns it.
     * This is useful for sending images with intents.
     */
    public static byte[] getDrawableByteArray(Drawable aDrawable) {
        Bitmap bitmap = drawableToBitmap(aDrawable);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
        return baos.toByteArray();
    }

    /**
     * Converts a drawable to a bitmap and returns it.
     */
    public static Bitmap drawableToBitmap(Drawable drawable) {
        Bitmap bitmap = null;

        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if (bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap();
            }
        }

        if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); // Single color bitmap will be created of 1x1 pixel
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        }

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    /**
     * Creates a string with a unicode emoticon.
     *
     * @param unicode
     * @return
     */
    public static String getEmijoByUnicode(int unicode) {
        return new String(Character.toChars(unicode));
    }

    /**
     * Hides the onscreen keyboard if it is visisble
     */
    public static void hideKeyboard(Activity activity) {
        // Check if no view has focus:
        View view = activity.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * Shows the soft keyboard
     */
    public static void showKeyboard(Activity activity) {
        // Check if no view has focus:
        View view = activity.getCurrentFocus();
        if (view != null) {
            InputMethodManager inputMethodManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.toggleSoftInputFromWindow(view.getApplicationWindowToken(), InputMethodManager.SHOW_FORCED, 0);
        }
    }

    /**
     * Returns whether the device is a tablet or not.
     */
    public static boolean isTablet(Context context) {
        if (context == null) {
            return false;
        } else {
            return context.getResources().getBoolean(R.bool.isTablet);
        }
    }

    /**
     * Gets the accent color from the current theme
     */
    public static int getAccentColor(Context mContext) {
        TypedValue typedValue = new TypedValue();

        TypedArray a = mContext.obtainStyledAttributes(typedValue.data, new int[]{R.attr.colorAccent});
        int color = a.getColor(0, 0);

        a.recycle();

        return color;
    }

    /**
     * Returns a resized bitmap with a spcified factor to change the width and height with.
     */
    public static Bitmap getResizedBitmap(Bitmap bm, float factorchange) {
        return getResizedBitmap(bm, (int) (bm.getWidth() * factorchange), (int) (bm.getHeight() * factorchange));
    }


    /**
     * Creates a new resized bitmap with a specified width and height.
     */
    public static Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);

        Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, false);
        //bm.recycle();
        return resizedBitmap;
    }

    public static Bitmap getResizedBitmap(Bitmap bm, int dpHeight, Context context) {
        try {
            Bitmap.Config mConfig = bm.getConfig() == null ? Bitmap.Config.ARGB_8888 : bm.getConfig();

            Bitmap resizedBitmap = bm.copy(mConfig, true);
            int heightPx = Service.dpToPixels(context, dpHeight);
            int widthPx = (int) ((1.0 * resizedBitmap.getWidth() / resizedBitmap.getHeight()) * (heightPx * 1.0));
            return getResizedBitmap(resizedBitmap, widthPx, heightPx);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Method for increasing a Navigation Drawer's edge size.
     */
    public static void increaseNavigationDrawerEdge(DrawerLayout aDrawerLayout, Context context) {
        // Increase the area from which you can open the navigation drawer.
        try {
            Field mDragger = aDrawerLayout.getClass().getDeclaredField("mLeftDragger");
            mDragger.setAccessible(true);
            ViewDragHelper draggerObj = (ViewDragHelper) mDragger.get(aDrawerLayout);

            Field mEdgeSize = draggerObj.getClass().getDeclaredField("mEdgeSize");
            mEdgeSize.setAccessible(true);
            int edgeSize = mEdgeSize.getInt(draggerObj) * 3;

            mEdgeSize.setInt(draggerObj, edgeSize); //optimal value as for me, you may set any constant in dp
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks if the device is connected to a valid network
     * Can only be called on a thread
     */
    public static boolean isNetworkConnectedThreadOnly(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();

        if (networkInfo != null && networkInfo.isConnectedOrConnecting()) {
            try {
                HttpURLConnection urlc = (HttpURLConnection)
                        (new URL("https://clients3.google.com/generate_204")
                                .openConnection());
                urlc.setRequestProperty("User-Agent", "Android");
                urlc.setRequestProperty("Connection", "close");
                urlc.setConnectTimeout(1500);
                urlc.connect();
                return (urlc.getResponseCode() == 204 &&
                        urlc.getContentLength() == 0);
            } catch (IOException e) {
                Log.e("SERVICE", "Error checking internet connection", e);
            }
        } else {
            Log.d("SERVICE", "No network available!");
        }

        return false;
    }

    /**
     * Does the opposite of the View.bringToFront() method
     *
     * @param v the view you want to send to the back
     */
    public static void bringToBack(final View v) {
        final ViewGroup parent = (ViewGroup) v.getParent();
        if (null != parent) {
            parent.removeView(v);
            parent.addView(v, 0);
        }
    }

    public static void saveImageToStorage(Bitmap image, String key, Context context) {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream()) {
            // Create an ByteArrayOutputStream and feed a compressed bitmap image in it
            image.compress(Bitmap.CompressFormat.PNG, 100, byteStream); // PNG as only format with transparency

            // Create a FileOutputStream with out key and set the mode to private to ensure
            // Only this app and read the file. Write out ByteArrayOutput to the file and close it
            try (FileOutputStream fileOut = context.openFileOutput(key, Context.MODE_PRIVATE)) {
                fileOut.write(byteStream.toByteArray());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Bitmap getImageFromStorage(String key, Context context) throws IOException {
        InputStream fileIn = context.openFileInput(key);
        return BitmapFactory.decodeStream(fileIn);
    }

    public static boolean doesStorageFileExist(String key, Context context) {
        File file = context.getFileStreamPath(key);
        return file.exists();
    }

    /**
     * Gets the navigation drawer toggle view from a toolbar
     *
     * @param toolbar The toolbar containing the navigation button
     * @return The ImageButton
     */
    public static ImageButton getNavButtonView(Toolbar toolbar) {
        try {
            Class<?> toolbarClass = Toolbar.class;
            Field navButtonField = toolbarClass.getDeclaredField("mNavButtonView");
            navButtonField.setAccessible(true);

            return (ImageButton) navButtonField.get(toolbar);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Returns the height of the device screen
     */
    public static int getScreenHeight(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        final DisplayMetrics displayMetrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics.heightPixels;
    }

    public static String urlToJSONString(String urlToRead) {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();

        try {
            final HttpURLConnection conn = openConnection(new URL(urlToRead));

            conn.setReadTimeout(5000);
            conn.setConnectTimeout(3000);
            conn.setRequestProperty("Client-ID", Service.getApplicationClientID());
            conn.setRequestProperty("Accept", "application/vnd.twitchtv.v5+json");
            conn.setRequestMethod("GET");

            try (InputStream is = conn.getInputStream()) {
                byte[] buf = new byte[8192];
                int length, off = 0;
                while ((length = is.read(buf)) != -1) {
                    os.write(buf, 0, length);
                }
            }

            return os.toString();
        } catch (Exception e) {
            Log.d(LOG_TAG, "Failed to fetch " + urlToRead, e);
            return null;
        }
    }

    public static HttpURLConnection openConnection(URL url) throws IOException {
        return (HttpURLConnection) url.openConnection();
    }

    public static ChannelInfo getStreamerInfoFromUserId(int streamerId) throws NullPointerException {

        ChannelInfo channelInfo = null;
        try {
            JSONObject JSONString = new JSONObject(urlToJSONString("https://api.twitch.tv/kraken/channels/" + streamerId));

            int userId = JSONString.getInt("_id");
            String displayName = JSONString.getString("display_name");
            String name = JSONString.getString("name");
            int followers = JSONString.getInt("followers");
            int views = JSONString.getInt("views");
            URL logoURL = null;
            URL videoBannerURL = null;
            URL profileBannerURL = null;

            // Make sure streamer has actually set the pictures
            if (!JSONString.isNull("logo")) {
                logoURL = new URL(JSONString.getString("logo"));
            }
            if (!JSONString.isNull("video_banner")) {
                videoBannerURL = new URL(JSONString.getString("video_banner"));
            }
            if (!JSONString.isNull("profile_banner")) {
                profileBannerURL = new URL(JSONString.getString("profile_banner"));
            }

            JSONObject JSONStringTwo = new JSONObject(urlToJSONString("https://api.twitch.tv/kraken/users/" + streamerId));
            String description = JSONStringTwo.getString("bio");

            channelInfo = new ChannelInfo(userId, name, displayName, description, followers, views, logoURL, videoBannerURL, profileBannerURL, false);

        } catch (JSONException e) {
            Log.v("Service: ", e.getMessage());
        } catch (MalformedURLException ef) {
            Log.v("Service : ", ef.getMessage());
        }

        return channelInfo;
    }

    /**
     * Determines whether or not the user is currently following a streamer.
     */
    public static boolean isUserFollowingStreamer(String streamername, Context context) {
        return false;
    }


    public static int dpToPixels(Context context, float dp) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }

    /**
     * Gets Bitmap from the specified URL
     * Must not be called on Main UI Thread
     */
    public static Bitmap getBitmapFromUrl(String url) {
        try {
            final HttpURLConnection connection = openConnection(new URL(url));
            connection.connect();
            try(final InputStream is = connection.getInputStream()) {
                return BitmapFactory.decodeStream(is);
            }
        } catch (Exception e) {
            Log.d(LOG_TAG, "Failed to fetch bitmap " + url, e);
            return null;
        }
    }
}
