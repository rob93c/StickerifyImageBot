package com.github.stickerifier.stickerify.bot;

import mockwebserver3.MockResponse;
import okio.Buffer;
import okio.Okio;

import java.io.File;

public final class MockResponses {

	static final MockResponse START_MESSAGE = new MockResponse.Builder().body("""
			{
				ok: true,
				result: [
					{
						update_id: 1,
						message: {
							message_id: 1,
							from: {
								id: 123456
							},
							chat: {
								id: 1
							},
							text: "/start"
						}
					}
				]
			}
			""").build();

	static final MockResponse HELP_MESSAGE = new MockResponse.Builder().body("""
			{
				ok: true,
				result: [
					{
						update_id: 1,
						message: {
							message_id: 1,
							from: {
								id: 123456
							},
							chat: {
								id: 1
							},
							text: "/help"
						}
					}
				]
			}
			""").build();

	static final MockResponse PRIVACY_MESSAGE = new MockResponse.Builder().body("""
			{
				ok: true,
				result: [
					{
						update_id: 1,
						message: {
							message_id: 1,
							from: {
								id: 123456
							},
							chat: {
								id: 1
							},
							text: "/privacy"
						}
					}
				]
			}
			""").build();

	static final MockResponse FILE_NOT_SUPPORTED = new MockResponse.Builder().body("""
			{
				ok: true,
				result: [
					{
						update_id: 1,
						message: {
							message_id: 1,
							from: {
								id: 123456
							},
							chat: {
								id: 1
							},
							audio: {
								file_id: "audio.mp3"
							}
						}
					}
				]
			}
			""").build();

	static final MockResponse FILE_TOO_BIG = new MockResponse.Builder().body("""
			{
				ok: true,
				result: [
					{
						update_id: 1,
						message: {
							message_id: 1,
							from: {
								id: 123456
							},
							chat: {
								id: 1
							},
							video: {
								file_id: "video.mp4",
								file_size: 100000000
							}
						}
					}
				]
			}
			""").build();

	static final MockResponse ANIMATED_STICKER = new MockResponse.Builder().body("""
			{
				ok: true,
				result: [
					{
						update_id: 1,
						message: {
							message_id: 1,
							from: {
								id: 123456
							},
							chat: {
								id: 1
							},
							sticker: {
								file_id: "animated_sticker.tgs",
								file_size: 64000
							}
						}
					}
				]
			}
			""").build();

	static final MockResponse PNG_FILE = new MockResponse.Builder().body("""
			{
				ok: true,
				result: [
					{
						update_id: 1,
						message: {
							message_id: 1,
							from: {
								id: 123456
							},
							chat: {
								id: 1
							},
							photo: [
								{
									file_id: "big.png",
									file_size: 200000
								}
							]
						}
					}
				]
			}
			""").build();

	static final MockResponse WEBP_FILE = new MockResponse.Builder().body("""
			{
				ok: true,
				result: [
					{
						update_id: 1,
						message: {
							message_id: 1,
							from: {
								id: 123456
							},
							chat: {
								id: 1
							},
							photo: [
								{
									file_id: "static.webp",
									file_size: 200000
								}
							]
						}
					}
				]
			}
			""").build();

	static final MockResponse MOV_FILE = new MockResponse.Builder().body("""
			{
				ok: true,
				result: [
					{
						update_id: 1,
						message: {
							message_id: 1,
							from: {
								id: 123456
							},
							chat: {
								id: 1
							},
							video: {
								file_id: "long.mov",
								file_size: 200000
							}
						}
					}
				]
			}
			""").build();

	static final MockResponse WEBM_FILE = new MockResponse.Builder().body("""
			{
				ok: true,
				result: [
					{
						update_id: 1,
						message: {
							message_id: 1,
							from: {
								id: 123456
							},
							chat: {
								id: 1
							},
							video: {
								file_id: "short_low_fps.webm",
								file_size: 200000
							}
						}
					}
				]
			}
			""").build();

	static final MockResponse GIF_FILE = new MockResponse.Builder().body("""
			{
				ok: true,
				result: [
					{
						update_id: 1,
						message: {
							message_id: 1,
							from: {
								id: 123456
							},
							chat: {
								id: 1
							},
							sticker: {
								file_id: "valid.gif",
								file_size: 200000
							}
						}
					}
				]
			}
			""").build();

	static final MockResponse DOCUMENT = new MockResponse.Builder().body("""
			{
				ok: true,
				result: [
					{
						update_id: 1,
						message: {
							message_id: 1,
							from: {
								id: 123456
							},
							chat: {
								id: 1
							},
							document: {
								file_id: "document.txt",
								file_size: 200000
							}
						}
					}
				]
			}
			""").build();

	static final MockResponse CORRUPTED_FILE = new MockResponse.Builder().body("""
			{
				ok: true,
				result: [
					{
						update_id: 1,
						message: {
							message_id: 1,
							from: {
								id: 123456
							},
							chat: {
								id: 1
							},
							video: {
								file_id: "corrupted.mp4",
								file_size: 200000
							}
						}
					}
				]
			}
			""").build();

	static MockResponse fileInfo(String id) {
		return new MockResponse.Builder().body("""
				{
					ok: true,
					result: {
						file_id: "%1$s",
						file_path: "%1$s"
					}
				}
				""".formatted(id)).build();
	}

	static MockResponse fileDownload(File file) throws Exception {
		try (var buffer = new Buffer(); var source = Okio.source(file)) {
			buffer.writeAll(source);
			return new MockResponse.Builder().body(buffer).build();
		}
	}

	private MockResponses() {
		throw new UnsupportedOperationException();
	}
}
