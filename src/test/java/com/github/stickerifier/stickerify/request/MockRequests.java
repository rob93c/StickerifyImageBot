package com.github.stickerifier.stickerify.request;

import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.utility.BotUtils;

public class MockRequests {

	static final Message START_MESSAGE = withContent("""
			text: "/start"
			""");

	static final Message HELP_MESSAGE = withContent("""
			text: "/help"
			""");

	static final Message PRIVACY_MESSAGE = withContent("""
			text: "/privacy"
			""");

	static final Message FILE_NOT_SUPPORTED = withContent("""
			audio: {
				file_id: "audio.mp3"
			}
			""");

	static final Message FILE_TOO_LARGE = withContent("""
			video: {
				file_id: "video.mp4",
				file_size: 100000000
			}
			""");

	static final Message ANIMATED_STICKER = withContent("""
			sticker: {
				file_id: "animated_sticker.tgs",
				file_size: 64000
			}
			""");

	static final Message PNG_FILE = withContent("""
			photo: [
				{
					file_id: "big.png",
					file_size: 200000
				}
			]
			""");

	static final Message WEBP_FILE = withContent("""
			photo: [
				{
					file_id: "valid.webp",
					file_size: 200000
				}
			]
			""");

	static final Message MOV_FILE = withContent("""
			video: {
				file_id: "long.mov",
				file_size: 200000
			}
			""");

	static final Message WEBM_FILE = withContent("""
			video: {
				file_id: "short_low_fps.webm",
				file_size: 200000
			}
			""");

	static final Message GIF_FILE = withContent("""
			sticker: {
				file_id: "valid.gif",
				file_size: 200000
			}
			""");

	static final Message DOCUMENT = withContent("""
			document: {
				file_id: "document.txt",
				file_size: 200000
			}
			""");

	static final Message CORRUPTED_FILE = withContent("""
			video: {
				file_id: "corrupted.mp4",
				file_size: 200000
			}
			""");

	static Message withContent(String content) {
		return BotUtils.fromJson(STR."""
			{
				message_id: 1,
				from: {
					id: 123456
				},
				chat: {
					id: 1
				},
				\{content}
			}
			""", Message.class);
	}

	private MockRequests() {
		throw new UnsupportedOperationException();
	}
}
