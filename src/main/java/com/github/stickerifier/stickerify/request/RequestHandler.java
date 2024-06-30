package com.github.stickerifier.stickerify.request;

import com.github.stickerifier.stickerify.media.MediaConverter;
import com.github.stickerifier.stickerify.telegram.Answer;
import com.github.stickerifier.stickerify.telegram.exception.TelegramApiException;
import com.github.stickerifier.stickerify.telegram.model.TelegramFile;
import com.github.stickerifier.stickerify.telegram.model.TelegramRequest;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.GetFile;
import com.pengrad.telegrambot.request.SendDocument;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.BaseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;

import static com.github.stickerifier.stickerify.telegram.Answer.CORRUPTED;
import static com.github.stickerifier.stickerify.telegram.Answer.ERROR;
import static com.github.stickerifier.stickerify.telegram.Answer.FILE_ALREADY_VALID;
import static com.github.stickerifier.stickerify.telegram.Answer.FILE_READY;
import static com.github.stickerifier.stickerify.telegram.Answer.FILE_TOO_LARGE;
import static com.pengrad.telegrambot.model.request.ParseMode.MarkdownV2;
import static java.util.HashSet.newHashSet;

public class RequestHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(RequestHandler.class);
	private static final Semaphore SEMAPHORE = new Semaphore(getMaxConcurrentThreads());
	public static final String TMP_FILE_PREFIX = "OriginalFile-";

	public static Callable<Void> from(TelegramRequest request, TelegramBot bot) {
		return () -> {
			var handler = new RequestHandler(bot);
			handler.answer(request);
			return null;
		};
	}

	private final TelegramBot bot;

	private RequestHandler(TelegramBot bot) {
		this.bot = bot;
	}

	private void answer(TelegramRequest request) {
		var file = request.getFile();

		if (file != null) {
			answerFile(request, file);
		} else {
			answerText(request.getAnswerMessage(), request);
		}
	}

	private void answerFile(TelegramRequest request, TelegramFile file) {
		if (file == TelegramFile.NOT_SUPPORTED) {
			answerText(ERROR, request);
		} else if (file.canBeDownloaded()) {
			answerFile(request, file.id());
		} else {
			LOGGER.atInfo().log("Passed-in file is too large");

			answerText(FILE_TOO_LARGE, request);
		}
	}

	private void answerFile(TelegramRequest request, String fileId) {
		Set<Path> pathsToDelete = newHashSet(2);

		try {
			var originalFile = retrieveFile(fileId);
			pathsToDelete.add(originalFile.toPath());

			var outputFile = convertMedia(originalFile);

			if (outputFile == null) {
				answerText(FILE_ALREADY_VALID, request);
			} else {
				pathsToDelete.add(outputFile.toPath());

				var answerWithFile = new SendDocument(request.getChatId(), outputFile)
						.replyToMessageId(request.getMessageId())
						.caption(FILE_READY.getText())
						.parseMode(MarkdownV2);

				execute(answerWithFile);
			}
		} catch (TelegramApiException e) {
			processFailure(request, e);
		} finally {
			deleteTempFiles(pathsToDelete);
		}
	}

	private File retrieveFile(String fileId) throws TelegramApiException {
		var file = execute(new GetFile(fileId)).file();

		try {
			var fileContent = bot.getFileContent(file);
			var downloadedFile = File.createTempFile(TMP_FILE_PREFIX, null);
			Files.write(downloadedFile.toPath(), fileContent);

			return downloadedFile;
		} catch (IOException e) {
			throw new TelegramApiException(e);
		}
	}

	private void processFailure(TelegramRequest request, TelegramApiException e) {
		if (e.getMessage().endsWith("Bad Request: message to reply not found")) {
			LOGGER.atInfo().log("Unable to reply to the {}: the message sent has been deleted", request.getDescription());
		} else if ("The video could not be processed successfully".equals(e.getMessage())) {
			LOGGER.atInfo().log("Unable to reply to the {}: the file is corrupted", request.getDescription());
			answerText(CORRUPTED, request);
		} else {
			LOGGER.atWarn().setCause(e).log("Unable to process the file {}", request.getFile());
			answerText(ERROR, request);
		}
	}

	private void answerText(Answer answer, TelegramRequest request) {
		var answerWithText = new SendMessage(request.getChatId(), answer.getText())
				.replyToMessageId(request.getMessageId())
				.parseMode(MarkdownV2)
				.disableWebPagePreview(answer.isDisableWebPreview());

		try {
			execute(answerWithText);
		} catch (TelegramApiException e) {
			LOGGER.atError().setCause(e).log("Unable to reply to the {}", request);
		}
	}

	private <T extends BaseRequest<T, R>, R extends BaseResponse> R execute(BaseRequest<T, R> request) throws TelegramApiException {
		var response = bot.execute(request);

		if (response.isOk()) {
			return response;
		}

		throw new TelegramApiException("Telegram couldn't execute the {} request: {}", request.getMethod(), response.description());
	}

	private static File convertMedia(File inputFile) throws TelegramApiException {
		try {
			SEMAPHORE.acquire();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new TelegramApiException(e);
		}

		try {
			return MediaConverter.convert(inputFile);
		} finally {
			SEMAPHORE.release();
		}
	}

	private static void deleteTempFiles(Set<Path> pathsToDelete) {
		for (var path : pathsToDelete) {
			try {
				Files.deleteIfExists(path);
			} catch (IOException e) {
				LOGGER.atError().setCause(e).log("An error occurred trying to delete temp file {}", path);
			}
		}
	}

	private static int getMaxConcurrentThreads() {
		var value = System.getenv("CONCURRENT_THREADS");
		return value == null ? 5 : Integer.parseInt(value);
	}
}
