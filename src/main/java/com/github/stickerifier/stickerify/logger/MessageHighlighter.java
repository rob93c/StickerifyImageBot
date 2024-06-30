package com.github.stickerifier.stickerify.logger;

import static ch.qos.logback.core.pattern.color.ANSIConstants.BOLD;
import static ch.qos.logback.core.pattern.color.ANSIConstants.DEFAULT_FG;
import static ch.qos.logback.core.pattern.color.ANSIConstants.RESET;
import static ch.qos.logback.core.pattern.color.ANSIConstants.YELLOW_FG;
import static com.github.stickerifier.stickerify.logger.HighlightHelper.changeColorTo;
import static com.github.stickerifier.stickerify.logger.HighlightHelper.greenHighlight;
import static com.github.stickerifier.stickerify.logger.HighlightHelper.replaceFirst;
import static com.github.stickerifier.stickerify.logger.HighlightHelper.retrieveMimeType;
import static com.github.stickerifier.stickerify.telegram.model.TelegramRequest.NEW_USER;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.Converter;

/**
 * Custom converter class to be used by Logback in order to highlight important substrings.
 *
 * @see Converter
 */
public class MessageHighlighter extends MessageConverter {

	private static final String START_YELLOW = changeColorTo(BOLD + YELLOW_FG);
	static final String CONTINUE_WHITE = changeColorTo(RESET + DEFAULT_FG);
	static final String HIGHLIGHTED_NEW_USER = STR." \{START_YELLOW}\{NEW_USER.substring(1)}\{CONTINUE_WHITE}";

	@Override
	public String convert(ILoggingEvent event) {
		var message = event.getFormattedMessage();

		if (message != null) {
			if (message.contains(NEW_USER)) {
				return replaceFirst(message, NEW_USER, HIGHLIGHTED_NEW_USER);
			}

			var mimeType = retrieveMimeType(message);
			if (mimeType != null) {
				var highlightedMimeType = greenHighlight(mimeType, CONTINUE_WHITE);
				return replaceFirst(message, mimeType, highlightedMimeType);
			}
		}

		return message;
	}
}
