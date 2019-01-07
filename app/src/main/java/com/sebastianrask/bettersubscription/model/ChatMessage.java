package com.sebastianrask.bettersubscription.model;


import java.util.List;

public class ChatMessage {
	private String message;
	private String name;
	private String color = "";
	private List<ChatEmote> emotes;
	private List<ChatBadge> badges;
	private boolean highlight;
	private boolean notice;

    public ChatMessage(String message, String name, String color, List<ChatEmote> emotes, List<ChatBadge> badges, boolean highlight, boolean notice) {
        this.message = message;
        this.name = name;
        this.color = color;
        this.emotes = emotes;
		this.highlight = highlight;
		this.badges = badges;
		this.notice = notice;
    }

    public String getMessage() {
		return message;
	}

	public String getName() {
		return name;
	}

	public String getColor() {
		return color;
	}

	public List<ChatEmote> getEmotes() {
		return emotes;
	}

	public List<ChatBadge> getBadges() {
		return badges;
	}

	public boolean isHighlight() {
		return highlight;
	}

	public boolean isNotice() {
		return notice;
	}

	public void setHighlight(boolean highlight) {
		this.highlight = highlight;
	}

	@Override
	public String toString() {
		return "ChatMessage{" +
				"message='" + message + '\'' +
				", name='" + name + '\'' +
				", color='" + color + '\'' +
				", emotes=" + emotes +
				", badges =" + badges +
				'}';
	}
}
