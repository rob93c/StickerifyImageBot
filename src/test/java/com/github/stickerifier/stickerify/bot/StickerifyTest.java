package com.github.stickerifier.stickerify.bot;

import static com.github.stickerifier.stickerify.ResourceHelper.loadResource;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.stickerifier.stickerify.junit.ClearTempFiles;
import com.github.stickerifier.stickerify.telegram.Answer;
import com.pengrad.telegrambot.TelegramBot;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.URLEncoder;

@ClearTempFiles
@ExtendWith(MockWebServerExtension.class)
class StickerifyTest {

	private MockWebServer server;

	@BeforeEach
	void setup() {
		server = new MockWebServer();
	}

	@Test
	void startMessage() throws Exception {
		server.enqueue(MockResponses.START_MESSAGE);

		startBot();

		var getUpdates = server.takeRequest();
		assertEquals("/api/token/getUpdates", getUpdates.getPath());

		var sendMessage = server.takeRequest();
		assertEquals("/api/token/sendMessage", sendMessage.getPath());
		assertResponseContainsMessage(sendMessage, Answer.HELP);
	}

	private void startBot() {
		var bot = new TelegramBot.Builder("token")
				.apiUrl(server.url("api/").toString())
				.fileApiUrl(server.url("files/").toString())
				.updateListenerSleep(500)
				.build();

		new Stickerify(bot, Runnable::run);
	}

	private static void assertResponseContainsMessage(RecordedRequest request, Answer answer) {
		var message = URLEncoder.encode(answer.getText(), UTF_8);
		assertThat(request.getBody().readUtf8(), containsString(message));
	}

	@Test
	void helpMessage() throws Exception {
		server.enqueue(MockResponses.HELP_MESSAGE);

		startBot();

		var getUpdates = server.takeRequest();
		assertEquals("/api/token/getUpdates", getUpdates.getPath());

		var sendMessage = server.takeRequest();
		assertEquals("/api/token/sendMessage", sendMessage.getPath());
		assertResponseContainsMessage(sendMessage, Answer.HELP);
	}

	@Test
	void privacyMessage() throws Exception {
		server.enqueue(MockResponses.PRIVACY_MESSAGE);

		startBot();

		var getUpdates = server.takeRequest();
		assertEquals("/api/token/getUpdates", getUpdates.getPath());

		var sendMessage = server.takeRequest();
		assertEquals("/api/token/sendMessage", sendMessage.getPath());
		assertResponseContainsMessage(sendMessage, Answer.PRIVACY_POLICY);
	}

	@Test
	void fileNotSupported() throws Exception {
		server.enqueue(MockResponses.FILE_NOT_SUPPORTED);

		startBot();

		var getUpdates = server.takeRequest();
		assertEquals("/api/token/getUpdates", getUpdates.getPath());

		var sendMessage = server.takeRequest();
		assertEquals("/api/token/sendMessage", sendMessage.getPath());
		assertResponseContainsMessage(sendMessage, Answer.ERROR);
	}

	@Test
	void fileTooBig() throws Exception {
		server.enqueue(MockResponses.FILE_TOO_BIG);

		startBot();

		var getUpdates = server.takeRequest();
		assertEquals("/api/token/getUpdates", getUpdates.getPath());

		var sendMessage = server.takeRequest();
		assertEquals("/api/token/sendMessage", sendMessage.getPath());
		assertResponseContainsMessage(sendMessage, Answer.FILE_TOO_LARGE);
	}

	@Test
	void fileAlreadyValid() throws Exception {
		server.enqueue(MockResponses.ANIMATED_STICKER);
		server.enqueue(MockResponses.fileInfo("animated_sticker.tgs"));
		server.enqueue(MockResponses.fileDownload(loadResource("animated_sticker.tgs")));

		startBot();

		var getUpdates = server.takeRequest();
		assertEquals("/api/token/getUpdates", getUpdates.getPath());

		var getFile = server.takeRequest();
		assertEquals("/api/token/getFile", getFile.getPath());
		assertEquals("file_id=animated_sticker.tgs", getFile.getBody().readUtf8());

		var download = server.takeRequest();
		assertEquals("/files/token/animated_sticker.tgs", download.getPath());

		var sendMessage = server.takeRequest();
		assertEquals("/api/token/sendMessage", sendMessage.getPath());
		assertResponseContainsMessage(sendMessage, Answer.FILE_ALREADY_VALID);
	}

	@Test
	void convertedPng() throws Exception {
		server.enqueue(MockResponses.PNG_FILE);
		server.enqueue(MockResponses.fileInfo("big.png"));
		server.enqueue(MockResponses.fileDownload(loadResource("big.png")));

		startBot();

		var getUpdates = server.takeRequest();
		assertEquals("/api/token/getUpdates", getUpdates.getPath());

		var getFile = server.takeRequest();
		assertEquals("/api/token/getFile", getFile.getPath());
		assertEquals("file_id=big.png", getFile.getBody().readUtf8());

		var download = server.takeRequest();
		assertEquals("/files/token/big.png", download.getPath());

		var sendDocument = server.takeRequest();
		assertEquals("/api/token/sendDocument", sendDocument.getPath());
		assertThat(sendDocument.getBody().readUtf8(), containsString(Answer.FILE_READY.getText()));
	}

	@Test
	void convertedWebp() throws Exception {
		server.enqueue(MockResponses.WEBP_FILE);
		server.enqueue(MockResponses.fileInfo("static.webp"));
		server.enqueue(MockResponses.fileDownload(loadResource("static.webp")));

		startBot();

		var getUpdates = server.takeRequest();
		assertEquals("/api/token/getUpdates", getUpdates.getPath());

		var getFile = server.takeRequest();
		assertEquals("/api/token/getFile", getFile.getPath());
		assertEquals("file_id=static.webp", getFile.getBody().readUtf8());

		var download = server.takeRequest();
		assertEquals("/files/token/static.webp", download.getPath());

		var sendDocument = server.takeRequest();
		assertEquals("/api/token/sendDocument", sendDocument.getPath());
		assertThat(sendDocument.getBody().readUtf8(), containsString(Answer.FILE_READY.getText()));
	}

	@Test
	void convertedMov() throws Exception {
		server.enqueue(MockResponses.MOV_FILE);
		server.enqueue(MockResponses.fileInfo("long.mov"));
		server.enqueue(MockResponses.fileDownload(loadResource("long.mov")));

		startBot();

		var getUpdates = server.takeRequest();
		assertEquals("/api/token/getUpdates", getUpdates.getPath());

		var getFile = server.takeRequest();
		assertEquals("/api/token/getFile", getFile.getPath());
		assertEquals("file_id=long.mov", getFile.getBody().readUtf8());

		var download = server.takeRequest();
		assertEquals("/files/token/long.mov", download.getPath());

		var sendDocument = server.takeRequest();
		assertEquals("/api/token/sendDocument", sendDocument.getPath());
		assertThat(sendDocument.getBody().readUtf8(), containsString(Answer.FILE_READY.getText()));
	}

	@Test
	void convertedWebm() throws Exception {
		server.enqueue(MockResponses.WEBM_FILE);
		server.enqueue(MockResponses.fileInfo("short_low_fps.webm"));
		server.enqueue(MockResponses.fileDownload(loadResource("short_low_fps.webm")));

		startBot();

		var getUpdates = server.takeRequest();
		assertEquals("/api/token/getUpdates", getUpdates.getPath());

		var getFile = server.takeRequest();
		assertEquals("/api/token/getFile", getFile.getPath());
		assertEquals("file_id=short_low_fps.webm", getFile.getBody().readUtf8());

		var download = server.takeRequest();
		assertEquals("/files/token/short_low_fps.webm", download.getPath());

		var sendDocument = server.takeRequest();
		assertEquals("/api/token/sendDocument", sendDocument.getPath());
		assertThat(sendDocument.getBody().readUtf8(), containsString(Answer.FILE_READY.getText()));
	}

	@Test
	void convertedGif() throws Exception {
		server.enqueue(MockResponses.GIF_FILE);
		server.enqueue(MockResponses.fileInfo("valid.gif"));
		server.enqueue(MockResponses.fileDownload(loadResource("valid.gif")));

		startBot();

		var getUpdates = server.takeRequest();
		assertEquals("/api/token/getUpdates", getUpdates.getPath());

		var getFile = server.takeRequest();
		assertEquals("/api/token/getFile", getFile.getPath());
		assertEquals("file_id=valid.gif", getFile.getBody().readUtf8());

		var download = server.takeRequest();
		assertEquals("/files/token/valid.gif", download.getPath());

		var sendDocument = server.takeRequest();
		assertEquals("/api/token/sendDocument", sendDocument.getPath());
		assertThat(sendDocument.getBody().readUtf8(), containsString(Answer.FILE_READY.getText()));
	}

	@Test
	void documentNotSupported() throws Exception {
		server.enqueue(MockResponses.DOCUMENT);
		server.enqueue(MockResponses.fileInfo("document.txt"));
		server.enqueue(MockResponses.fileDownload(loadResource("document.txt")));

		startBot();

		var getUpdates = server.takeRequest();
		assertEquals("/api/token/getUpdates", getUpdates.getPath());

		var getFile = server.takeRequest();
		assertEquals("/api/token/getFile", getFile.getPath());
		assertEquals("file_id=document.txt", getFile.getBody().readUtf8());

		var download = server.takeRequest();
		assertEquals("/files/token/document.txt", download.getPath());

		var sendMessage = server.takeRequest();
		assertEquals("/api/token/sendMessage", sendMessage.getPath());
		assertResponseContainsMessage(sendMessage, Answer.ERROR);
	}

	@Test
	void corruptedVideo() throws Exception {
		server.enqueue(MockResponses.CORRUPTED_FILE);
		server.enqueue(MockResponses.fileInfo("corrupted.mp4"));
		server.enqueue(MockResponses.fileDownload(loadResource("corrupted.mp4")));

		startBot();

		var getUpdates = server.takeRequest();
		assertEquals("/api/token/getUpdates", getUpdates.getPath());

		var getFile = server.takeRequest();
		assertEquals("/api/token/getFile", getFile.getPath());
		assertEquals("file_id=corrupted.mp4", getFile.getBody().readUtf8());

		var download = server.takeRequest();
		assertEquals("/files/token/corrupted.mp4", download.getPath());

		var sendMessage = server.takeRequest();
		assertEquals("/api/token/sendMessage", sendMessage.getPath());
		assertResponseContainsMessage(sendMessage, Answer.CORRUPTED);
	}
}
