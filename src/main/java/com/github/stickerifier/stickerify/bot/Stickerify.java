package com.github.stickerifier.stickerify.bot;

import com.github.stickerifier.stickerify.request.RequestHandler;
import com.github.stickerifier.stickerify.telegram.model.TelegramRequest;
import com.pengrad.telegrambot.ExceptionHandler;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.TelegramException;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.ThreadFactory;

/**
 * Telegram bot to convert medias in the format required to be used as Telegram stickers.
 *
 * @author Roberto Cella
 */
public class Stickerify implements AutoCloseable, ExceptionHandler, UpdatesListener {

	private static final Logger LOGGER = LoggerFactory.getLogger(Stickerify.class);
	private static final ThreadFactory VIRTUAL_THREAD_FACTORY = Thread.ofVirtual().name("Virtual-", 0).factory();

	private final TelegramBot bot;

	/**
	 * Instantiate the bot processing requests with virtual threads.
	 *
	 * @see Stickerify
	 */
	public Stickerify() {
		var token = System.getenv("STICKERIFY_TOKEN");
		this(new TelegramBot(token));
	}

	Stickerify(TelegramBot bot) {
		this.bot = bot;
	}

	/**
	 * Starts listening for new Updates
	 */
	public void start() {
		bot.setUpdatesListener(this, this);
	}

	@Override
	public void close() {
		bot.removeGetUpdatesListener();
		bot.shutdown();
	}

	@Override
	public void onException(TelegramException e) {
		LOGGER.atError().log("There was an unexpected failure: {}", e.getMessage());
	}

	@Override
	public int process(List<Update> updates) {
		try (var scope = new StructuredTaskScope<>(null, VIRTUAL_THREAD_FACTORY)) {
			for (Update update : updates) {
				if (update.message() != null) {
					var request = new TelegramRequest(update.message());
					LOGGER.atInfo().log("Received {}", request.getDescription());
					scope.fork(RequestHandler.from(request, bot));
				}
			}

			scope.join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}

		return UpdatesListener.CONFIRMED_UPDATES_ALL;
	}
}
