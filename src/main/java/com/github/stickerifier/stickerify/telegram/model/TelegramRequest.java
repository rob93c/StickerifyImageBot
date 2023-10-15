package com.github.stickerifier.stickerify.telegram.model;

import static com.github.stickerifier.stickerify.telegram.Answer.ABOUT;
import static com.github.stickerifier.stickerify.telegram.Answer.HELP;
import static java.util.Comparator.comparing;

import com.github.stickerifier.stickerify.telegram.Answer;
import com.pengrad.telegrambot.model.Document;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.Sticker;
import com.pengrad.telegrambot.model.Video;
import com.pengrad.telegrambot.model.VideoNote;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Data class wrapping a {@link Message} instance to represent a Telegram request.
 *
 * @param message the message to wrap
 */
public record TelegramRequest(Message message) {

	public static final String NEW_USER = " (new user)";
	private static final String START_COMMAND = "/start";
	private static final String HELP_COMMAND = "/help";

	public TelegramFile getFile() {
		return getMessageMedia()
				.map(media -> switch (media) {
					case PhotoSize[] photos when photos.length > 0 -> getBestPhoto(photos);
					case Document document -> new TelegramFile(document.fileId(), document.fileSize());
					case Sticker sticker -> new TelegramFile(sticker.fileId(), sticker.fileSize());
					case Video video -> new TelegramFile(video.fileId(), video.fileSize());
					case VideoNote videoNote -> new TelegramFile(videoNote.fileId(), videoNote.fileSize());
					default -> TelegramFile.NOT_SUPPORTED;
				})
				.orElse(null);
	}

	private Optional<?> getMessageMedia() {
		return Stream.of(message.photo(), message.document(), message.sticker(),
						message.video(), message.videoNote(),
						message.audio(), message.voice())
				.filter(Objects::nonNull)
				.findFirst();
	}

	private TelegramFile getBestPhoto(PhotoSize[] photos) {
		return Arrays.stream(photos)
				.map(photo -> new TelegramFile(photo.fileId(), photo.fileSize()))
				.filter(TelegramFile::canBeDownloaded)
				.max(comparing(TelegramFile::size))
				.orElse(TelegramFile.TOO_LARGE);
	}

	public Long getChatId() {
		return message.chat().id();
	}

	public Integer getMessageId() {
		return message.messageId();
	}

	/**
	 * Creates a String describing the current request,
	 * writing <b>only</b> the username and if the sender is a new user.
	 *
	 * @return the description of the request
	 */
	public String getDescription() {
		var description = "request from " + getUsername();

		if (START_COMMAND.equals(message.text())) {
			description += NEW_USER;
		}

		return description;
	}

	private String getUsername() {
		return Optional.ofNullable(message.from().username())
				.map(username -> "@" + username)
				.orElse("id:" + message.from().id());
	}

	public Answer getAnswerMessage() {
		return isHelpCommand() ? HELP : ABOUT;
	}

	private boolean isHelpCommand() {
		return HELP_COMMAND.equalsIgnoreCase(message.text());
	}

	@Override
	public String toString() {
		var file = Optional.ofNullable(getFile()).map(TelegramFile::id).orElse(null);
		var text = Optional.ofNullable(message.text()).orElse(message.caption());

		return "request ["
				+ "chat=" + getChatId()
				+ ", from=" + getUsername()
				+ writeIfNotEmpty("file", file)
				+ writeIfNotEmpty("text", text)
				+ "]";
	}

	private static String writeIfNotEmpty(String field, String value) {
		return value != null && !value.isEmpty()
				? ", " + field + "=" + value
				: "";
	}
}
