package com.sebastianrask.bettersubscription.chat;

/**
 * Created by SebastianRask on 03-03-2016.
 */

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.util.LruCache;

import com.sebastianrask.bettersubscription.R;
import com.sebastianrask.bettersubscription.model.Badge;
import com.sebastianrask.bettersubscription.model.ChatBadge;
import com.sebastianrask.bettersubscription.model.ChatEmote;
import com.sebastianrask.bettersubscription.model.ChatMessage;
import com.sebastianrask.bettersubscription.model.Emote;
import com.sebastianrask.bettersubscription.service.Service;
import com.sebastianrask.bettersubscription.service.Settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PushbackReader;
import java.io.StringReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class ChatManager extends AsyncTask<Void, ChatManager.ProgressUpdate, Void> {
	private final String LOG_TAG = getClass().getSimpleName();

	// Default Twitch Chat connect IP/domain and port
	private String twitchChatServer = "irc.twitch.tv";
	private int twitchChatPort = 6697;

	private BufferedWriter writer;
	private BufferedReader reader;

	private Handler callbackHandler;
	private boolean isStopping;
	private String user;
	private String oauth_key;
	private String channelName;
	private String hashChannel;
	private int channelUserId;
	private ChatCallback callback;
	private Context context;
	private Settings appSettings;

	// Data about the user and how to display his/hers message
	private List<ChatBadge> userBadges;
	private String userDisplayName;
	private String userColor;

	// Data about room state
	private boolean chatIsR9kmode;
	private boolean chatIsSlowmode;
	private boolean chatIsSubsonlymode;

	private ChatEmoteManager mEmoteManager;

	public ChatManager(Context aContext, String aChannel, int aChannelUserId, ChatCallback aCallback){
		mEmoteManager = new ChatEmoteManager(aChannelUserId, aChannel, aContext);
		appSettings = new Settings(aContext);
		user = appSettings.getGeneralTwitchName();
		oauth_key = "oauth:" + appSettings.getGeneralTwitchAccessToken();
		hashChannel = "#" + aChannel;
		channelName = aChannel;
		channelUserId = aChannelUserId;
		callback = aCallback;
		context = aContext;

		executeOnExecutor(THREAD_POOL_EXECUTOR);
	}

	public interface ChatCallback {
		void onMessage(ChatMessage message);
		void onConnecting();
		void onReconnecting();
		void onConnected();
		void onConnectionFailed();
		void onRoomstateChange(boolean isR9K, boolean isSlow, boolean isSubsOnly);
		void onBttvEmoteIdFetched(List<Emote> bttvChannel, List<Emote> bttvGlobal);
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		callbackHandler = new Handler();
	}

	@Override
	protected Void doInBackground(Void... params) {
		Log.d(LOG_TAG, "Trying to start chat " + hashChannel + " for user " + user);
		mEmoteManager.loadBttvEmotes(new ChatEmoteManager.EmoteFetchCallback() {
			@Override
			public void onEmoteFetched() {
				onProgressUpdate(new ChatManager.ProgressUpdate(ChatManager.ProgressUpdate.UpdateType.ON_BTTV_FETCHED));
			}
		});
		mEmoteManager.loadGlobalChatBadges(new ChatEmoteManager.EmoteFetchCallback() {
			@Override
			public void onEmoteFetched() {
			}
		});
		mEmoteManager.loadChannelChatBadges(new ChatEmoteManager.EmoteFetchCallback() {
			@Override
			public void onEmoteFetched() {
			}
		});

		ChatProperties properties = fetchChatProperties();
		if(properties != null) {
			String ipAndPort = properties.getChatIp();
			String[] ipAndPortArr = ipAndPort.split(":");
			twitchChatServer = ipAndPortArr[0];
			twitchChatPort = Integer.parseInt(ipAndPortArr[1]);
		}

		connect(twitchChatServer, twitchChatPort);

		return null;
	}

	@Override
	protected void onProgressUpdate(ProgressUpdate... values) {
		super.onProgressUpdate(values);
		final ProgressUpdate update = values[0];
		final ProgressUpdate.UpdateType type = update.getUpdateType();
		callbackHandler.post(new Runnable() {
			@Override
			public void run() {
				switch (type) {
					case ON_MESSAGE:
						callback.onMessage(update.getMessage());
						break;
					case ON_CONNECTED:
						callback.onConnected();
						break;
					case ON_CONNECTING:
						callback.onConnecting();
						break;
					case ON_CONNECTION_FAILED:
						callback.onConnectionFailed();
						break;
					case ON_RECONNECTING:
						callback.onReconnecting();
						break;
					case ON_ROOMSTATE_CHANGE:
						callback.onRoomstateChange(chatIsR9kmode, chatIsSlowmode, chatIsSubsonlymode);
						break;
					case ON_BTTV_FETCHED:
						callback.onBttvEmoteIdFetched(
								mEmoteManager.getChanncelBttvEmotes(), mEmoteManager.getGlobalBttvEmotes()
						);
						break;
				}
			}
		});
	}

	@Override
	protected void onPostExecute(Void aVoid) {
		super.onPostExecute(aVoid);
		Log.d(LOG_TAG, "Finished executing - Ending chat");
	}

	/**
	 * Connect to twitch with the users twitch name and oauth key.
	 * Joins the chat hashChannel.
	 * Sends request to retrieve emote id and positions as well as username color
	 * Handles parsing messages, pings and disconnects.
	 * Inserts emotes, subscriber, turbo and mod drawables into messages. Also Colors the message username by the user specified color.
	 * When a message has been parsed it is sent via the callback interface.
	 */
	private void connect(String address, int port) {
		// Force TLS for chat
		if (port == 6667) {
			port = 6697;
		}
		if (port == 80) {
			port = 443;
		}
		try (final Socket socket = SSLSocketFactory.getDefault().createSocket(address, port)) {
			writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

			writer.write("PASS " + oauth_key + "\r\n");
			writer.write("NICK " + user + "\r\n");
			writer.write("USER " + user + " \r\n");
			writer.flush();

			String line = "";
			while ((line = reader.readLine()) != null) {
				if (isStopping) {
					leaveChannel();
					Log.d(LOG_TAG, "Stopping chat for " + channelName);
					break;
				}

				final IRCv3Message msg = IRCv3Message.parse(line);
				if (msg != null) {
					switch (msg.getCommand()) {
						case "004":
							onProgressUpdate(new ProgressUpdate(ProgressUpdate.UpdateType.ON_CONNECTED));
							sendRawMessage("CAP REQ :twitch.tv/tags twitch.tv/commands");
							sendRawMessage("JOIN " + hashChannel + "\r\n");
							break;
						case "USERSTATE":
							handleUserstate(msg);
							break;
						case "ROOMSTATE":
							if (hashChannel.equals(msg.getParams()))
								handleRoomstate(msg);
							break;
						case "USERNOTICE":
							if (hashChannel.equals(msg.getParamsTarget()))
								handleNotice(msg);
							break;
						case "PRIVMSG":
							if (hashChannel.equals(msg.getParamsTarget()))
								handleMessage(msg);
							break;
					}
				} else {
					Log.d(LOG_TAG, "Unhandled: " + line);

					if (line.contains("PING")) { // Twitch wants to know if we are still here. Send PONG and Server info back
						handlePing(line);
					} else if (line.toLowerCase().contains("disconnected"))	{
						Log.e(LOG_TAG, "Disconnected - trying to reconnect");
						onProgressUpdate(new ProgressUpdate(ProgressUpdate.UpdateType.ON_RECONNECTING));
						connect(address, port); //ToDo: Test if chat keeps playing if connection is lost
					} else if(line.contains("NOTICE * :Error logging in")) {
						onProgressUpdate(new ProgressUpdate(ProgressUpdate.UpdateType.ON_CONNECTION_FAILED));
					}
				}
			}

		} catch (IOException e) {
			Log.d(LOG_TAG, "Failed to connect to " + address + "/" + port, e);
			onProgressUpdate(new ProgressUpdate(ProgressUpdate.UpdateType.ON_CONNECTION_FAILED));
		}
	}

	private void handleNotice(final IRCv3Message msg) {
		final String msgId = msg.getTag("msg-id");
		switch (msgId) {
			case "subs_on":
				chatIsSubsonlymode = true;
				break;
			case "subs_off":
				chatIsSubsonlymode = false;
				break;
			case "slow_on":
				chatIsSlowmode = true;
				break;
			case "slow_off":
				chatIsSlowmode = false;
				break;
			case "r9k_on":
				chatIsR9kmode = true;
				break;
			case "r9k_off":
				chatIsR9kmode = false;
				break;
			case "sub":
			case "subgift":
			case "anonsubgift":
			case "resub":
				final List<ChatEmote> emotes = mEmoteManager.findTwitchEmotes(msg.getTag("emotes"));
				emotes.addAll(mEmoteManager.findBttvEmotes(msg.getParamsMessage()));
				final List<ChatBadge> badges = mEmoteManager.getChatBadgesForTag(msg.getTag("badges"));

				// Send one message for the sub/gift/resub
				final String systemMsg = msg.getTag("system-msg");
				if (systemMsg != null && systemMsg.length() > 0) {
					final ChatMessage subMsg = new ChatMessage(systemMsg, msg.getParamsTarget(),
							msg.getTag("color"), emotes, badges, false, true);
					publishProgress(new ProgressUpdate(ProgressUpdate.UpdateType.ON_MESSAGE, subMsg));
				}

				// And send an actual chat message, if there is one (for resubs)
				if (msg.getParamsMessage() != null && msg.getParamsMessage().length() > 0) {
					final ChatMessage cm = new ChatMessage(msg.getParamsMessage(),
							msg.getTag("display-name"), msg.getTag("color"), emotes, badges, false, false);
					publishProgress(new ProgressUpdate(ProgressUpdate.UpdateType.ON_MESSAGE, cm));
				}

				break;
		}

		onProgressUpdate(new ProgressUpdate(ProgressUpdate.UpdateType.ON_ROOMSTATE_CHANGE));
	}

	/**
	 * Parses the received line and gets the roomstate.
	 * If the roomstate has changed since last check variables are changed and the chatfragment is notified
	 * @param msg
	 */
	private void handleRoomstate(final IRCv3Message msg) {
		chatIsR9kmode = "1".equals(msg.getTag("r9k"));
		chatIsSlowmode= "1".equals(msg.getTag("slow"));
		chatIsSubsonlymode = "1".equals(msg.getTag("subs-only"));
	}

	/**
	 * Parses the received line and saves data such as the users color, if the user is mod, subscriber or turbouser
	 * @param msg
	 */
	private void handleUserstate(final IRCv3Message msg) {
		userBadges = mEmoteManager.getChatBadgesForTag(msg.getTag("badges"));
		userColor = msg.getTag("color");
		userDisplayName = msg.getTag("display-name");
	}

	/**
	 * Parses and builds retrieved messages.
	 * Sends build message back via callback.
	 * @param msg
	 */
	private void handleMessage(final IRCv3Message msg) {
		final List<ChatEmote> emotes = mEmoteManager.findTwitchEmotes(msg.getTag("emotes"));
		emotes.addAll(mEmoteManager.findBttvEmotes(msg.getParamsMessage()));
		final List<ChatBadge> badges = mEmoteManager.getChatBadgesForTag(msg.getTag("badges"));

		final ChatMessage cm = new ChatMessage(msg.getParamsMessage(),
				msg.getTag("display-name"), msg.getTag("color"), emotes, badges, false, false);
		publishProgress(new ProgressUpdate(ProgressUpdate.UpdateType.ON_MESSAGE, cm));
	}

	/**
	 * Sends a PONG with the connected twitch server, as specified by Twitch IRC API.
	 * @param line
	 * @throws IOException
	 */
	private void handlePing(String line) throws IOException {
		writer.write("PONG " + line.substring(5) + "\r\n");
		writer.flush();
	}

	/**
	 * Sends an non manipulated String message to Twitch.
	 */
	private void sendRawMessage(String message) {
		try {
			writer.write(message + " \r\n");
			writer.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Makes the ChatManager stop retrieving messages.
	 */
	public void stop() {
		isStopping = true;
	}

	/**
	 * Send a message to a hashChannel on Twitch (Don't need to be on that hashChannel)
	 * @param message The message that will be sent
	 */
	public void sendMessage(final String message) {
		try {
			if (writer != null) {
				writer.write("PRIVMSG " + hashChannel + " :" + message + "\r\n");
				writer.flush();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Leaves the current hashChannel
	 */
	public void leaveChannel() {
		sendRawMessage("PART " + hashChannel);
	}

	/**
	 * Returns a Bitmap of the emote with the specified emote id.
	 * If the emote has not been cached from an earlier download the method
	 */
	public Bitmap getEmoteFromId(String emoteId, boolean isBttvEmote) {
		return mEmoteManager.getEmoteFromId(emoteId, isBttvEmote);
	}


	/**
	 * Fetches the chat properties from Twitch.
	 * Should never be called on the UI thread.
	 * @return
	 */
	private ChatProperties fetchChatProperties() {
		final String URL = "https://api.twitch.tv/api/channels/" + channelName + "/chat_properties";
		final String HIDE_LINKS_BOOL = "hide_chat_links";
		final String REQUIRE_VERIFIED_ACC_BOOL = "require_verified_account";
		final String SUBS_ONLY_BOOL = "subsonly";
		final String EVENT_BOOL = "devchat";
		final String CLUSTER_STRING = "cluster";
		final String CHAT_SERVERS_ARRAY = "chat_servers";

		try {
			JSONObject dataObject = new JSONObject(Service.urlToJSONString(URL));
			boolean hideLinks = dataObject.getBoolean(HIDE_LINKS_BOOL);
			boolean requireVerifiedAccount = dataObject.getBoolean(REQUIRE_VERIFIED_ACC_BOOL);
			boolean subsOnly = dataObject.getBoolean(SUBS_ONLY_BOOL);
			boolean isEvent = dataObject.getBoolean(EVENT_BOOL);
			String cluster = "";//dataObject.getString(CLUSTER_STRING);
			JSONArray chatServers = dataObject.getJSONArray(CHAT_SERVERS_ARRAY);

			ArrayList<String> chatServersResult = new ArrayList<>();
			for(int i = 0; i < chatServers.length(); i++) {
				chatServersResult.add(chatServers.getString(i));
			}

			return new ChatProperties(hideLinks, requireVerifiedAccount, subsOnly, isEvent, cluster, chatServersResult);

		} catch (JSONException e) {
			e.printStackTrace();
		}
		return null;
	}

	public String getUserDisplayName() {
		return userDisplayName;
	}

	public String getUserColor() {
		return userColor;
	}

	public List<ChatBadge> getUserBadges() {
		return userBadges;
	}

	/**
	 * Class used for determining which callback to make in the AsyncTasks OnProgressUpdate
	 */
	protected static class ProgressUpdate {
		public enum UpdateType {
			ON_MESSAGE,
			ON_CONNECTING,
			ON_RECONNECTING,
			ON_CONNECTED,
			ON_CONNECTION_FAILED,
			ON_ROOMSTATE_CHANGE,
			ON_BTTV_FETCHED
		}

		private UpdateType updateType;
		private ChatMessage message;

		public ProgressUpdate(UpdateType type) {
			updateType = type;
		}

		public ProgressUpdate(UpdateType type, ChatMessage aMessage) {
			updateType = type;
			message = aMessage;
		}

		public UpdateType getUpdateType() {
			return updateType;
		}

		public void setUpdateType(UpdateType updateType) {
			this.updateType = updateType;
		}

		public ChatMessage getMessage() {
			return message;
		}

		public void setMessage(ChatMessage message) {
			this.message = message;
		}
	}

	private static class IRCv3Message {

		private static final Pattern ircv3Pattern =
				Pattern.compile("^(@?[^\\r\\n ]+\\s){0,1}:([^\\s]+) (\\w+) (.*)$");

		private Map<String,String> tags;
		private String command;
		private String params;
		private String raw;

		private IRCv3Message() {
		}

		public boolean hasTag(String tag) {
			return tags.containsKey(tag);
		}

		public String getTag(String tag) {
			return tags.get(tag);
		}

		public String getCommand() {
			return command;
		}

		public String getParams() {
			return params;
		}

		public String getParamsMessage() {
			if (params == null) {
				return null;
			}
			final int msgStart = params.indexOf(":");
			if (msgStart == -1 || msgStart == params.length()) {
				return null;
			}

			return params.substring(msgStart + 1);
		}

		public String getParamsTarget() {
			if (params == null) {
				return null;
			}
			final int targetEnd = params.indexOf(" ");
			if (targetEnd <= 0) {
				return params;
			}

			return params.substring(0, targetEnd);
		}

		public String getRaw() {
			return raw;
		}

		public static IRCv3Message parse(final String msg) {
			final Matcher m = ircv3Pattern.matcher(msg);
			if (!m.find()) {
				return null;
			}

			final IRCv3Message result = new IRCv3Message();

			result.raw = msg;
			result.command = m.group(3);
			result.params = m.group(4);

			final String allTags = m.group(1);
			if (allTags == null) {
				return result;
			}

			result.tags = new HashMap<>();

			final String[] splitTags = allTags.substring(1).split(";");
			for (final String tagSpec : splitTags) {
				final String[] tagSplit = tagSpec.split("=");

				final String tagName = tagSplit[0];
				final String tagValue = (tagSplit.length == 2 ? tagSplit[1] : "")
						.replace("\\:", ";")
						.replace("\\s", " ")
						.replace("\\\\", "\\")
						.replace("\\n", "\n")
						.replace("\\r", "\r");

				result.tags.put(tagName, tagValue);
			}

			return result;
		}

		@Override
		public String toString() {
			return String.format("IRCv3Message{command=%s,params=%s,tags=%s," +
							"paramsMessage=%s,paramsTarget=%s}",
					command, params, tags, getParamsMessage(), getParamsTarget());
		}

	}
}