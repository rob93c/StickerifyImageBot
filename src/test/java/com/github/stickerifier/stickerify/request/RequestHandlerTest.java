package com.github.stickerifier.stickerify.request;

import com.github.stickerifier.stickerify.junit.ClearTempFiles;
import com.github.stickerifier.stickerify.telegram.Answer;
import com.github.stickerifier.stickerify.telegram.model.TelegramRequest;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URLEncoder;

import static com.github.stickerifier.stickerify.ResourceHelper.loadResource;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ClearTempFiles
class RequestHandlerTest {

	private MockWebServer server;
	private TelegramBot bot;

	@BeforeEach
	void setup() {
		this.server = new MockWebServer();
		this.bot = new TelegramBot.Builder("token")
				.apiUrl(server.url("api/").toString())
				.fileApiUrl(server.url("files/").toString())
				.build();
	}

	@AfterEach
	void cleanup() throws IOException {
		bot.shutdown();
		server.close();
	}

	@Test
	void startMessage() throws Exception {
		server.enqueue(MockResponses.OK);

		makeRequest(MockRequests.START_MESSAGE);

		var sendMessage = server.takeRequest();
		assertEquals("/api/token/sendMessage", sendMessage.getPath());
		assertResponseContainsMessage(sendMessage, Answer.HELP);
	}

	private void makeRequest(Message message) throws Exception {
		var request = new TelegramRequest(message);
		var callable = RequestHandler.from(request, bot);
		callable.call();
	}

	private static void assertResponseContainsMessage(RecordedRequest request, Answer answer) {
		var message = URLEncoder.encode(answer.getText(), UTF_8);
		assertThat(request.getBody().readUtf8(), containsString(message));
	}

	@Test
	void helpMessage() throws Exception {
		server.enqueue(MockResponses.OK);

		makeRequest(MockRequests.HELP_MESSAGE);

		var sendMessage = server.takeRequest();
		assertEquals("/api/token/sendMessage", sendMessage.getPath());
		assertResponseContainsMessage(sendMessage, Answer.HELP);
	}

	@Test
	void privacyMessage() throws Exception {
		server.enqueue(MockResponses.OK);

		makeRequest(MockRequests.PRIVACY_MESSAGE);

		var sendMessage = server.takeRequest();
		assertEquals("/api/token/sendMessage", sendMessage.getPath());
		assertResponseContainsMessage(sendMessage, Answer.PRIVACY_POLICY);
	}

	@Test
	void fileNotSupported() throws Exception {
		server.enqueue(MockResponses.OK);

		makeRequest(MockRequests.FILE_NOT_SUPPORTED);

		var sendMessage = server.takeRequest();
		assertEquals("/api/token/sendMessage", sendMessage.getPath());
		assertResponseContainsMessage(sendMessage, Answer.ERROR);
	}

	@Test
	void fileTooBig() throws Exception {
		server.enqueue(MockResponses.OK);

		makeRequest(MockRequests.FILE_TOO_LARGE);

		var sendMessage = server.takeRequest();
		assertEquals("/api/token/sendMessage", sendMessage.getPath());
		assertResponseContainsMessage(sendMessage, Answer.FILE_TOO_LARGE);
	}

	@Test
	void fileAlreadyValid() throws Exception {
		server.enqueue(MockResponses.fileInfo("animated_sticker.tgs"));
		server.enqueue(MockResponses.fileDownload(loadResource("animated_sticker.tgs")));
		server.enqueue(MockResponses.OK);

		makeRequest(MockRequests.ANIMATED_STICKER);

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
		server.enqueue(MockResponses.fileInfo("big.png"));
		server.enqueue(MockResponses.fileDownload(loadResource("big.png")));
		server.enqueue(MockResponses.OK);

		makeRequest(MockRequests.PNG_FILE);

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
		server.enqueue(MockResponses.fileInfo("valid.webp"));
		server.enqueue(MockResponses.fileDownload(loadResource("valid.webp")));
		server.enqueue(MockResponses.OK);

		makeRequest(MockRequests.WEBP_FILE);

		var getFile = server.takeRequest();
		assertEquals("/api/token/getFile", getFile.getPath());
		assertEquals("file_id=valid.webp", getFile.getBody().readUtf8());

		var download = server.takeRequest();
		assertEquals("/files/token/valid.webp", download.getPath());

		var sendDocument = server.takeRequest();
		assertEquals("/api/token/sendDocument", sendDocument.getPath());
		assertThat(sendDocument.getBody().readUtf8(), containsString(Answer.FILE_READY.getText()));
	}

	@Test
	void convertedMov() throws Exception {
		server.enqueue(MockResponses.fileInfo("long.mov"));
		server.enqueue(MockResponses.fileDownload(loadResource("long.mov")));
		server.enqueue(MockResponses.OK);

		makeRequest(MockRequests.MOV_FILE);

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
		server.enqueue(MockResponses.fileInfo("short_low_fps.webm"));
		server.enqueue(MockResponses.fileDownload(loadResource("short_low_fps.webm")));
		server.enqueue(MockResponses.OK);

		makeRequest(MockRequests.WEBM_FILE);

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
		server.enqueue(MockResponses.fileInfo("valid.gif"));
		server.enqueue(MockResponses.fileDownload(loadResource("valid.gif")));
		server.enqueue(MockResponses.OK);

		makeRequest(MockRequests.GIF_FILE);

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
		server.enqueue(MockResponses.fileInfo("document.txt"));
		server.enqueue(MockResponses.fileDownload(loadResource("document.txt")));
		server.enqueue(MockResponses.OK);

		makeRequest(MockRequests.DOCUMENT);

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
		server.enqueue(MockResponses.fileInfo("corrupted.mp4"));
		server.enqueue(MockResponses.fileDownload(loadResource("corrupted.mp4")));
		server.enqueue(MockResponses.OK);

		makeRequest(MockRequests.CORRUPTED_FILE);

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
