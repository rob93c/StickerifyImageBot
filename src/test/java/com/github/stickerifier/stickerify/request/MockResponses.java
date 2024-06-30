package com.github.stickerifier.stickerify.request;

import mockwebserver3.MockResponse;
import okio.Buffer;
import okio.Okio;

import java.io.File;

public final class MockResponses {

	static final MockResponse OK = new MockResponse.Builder().body("""
			{
				ok: true
			}
			""").build();

	static MockResponse fileInfo(String id) {
		return new MockResponse.Builder().body(STR."""
				{
					ok: true,
					result: {
						file_id: "\{id}",
						file_path: "\{id}"
					}
				}
				""").build();
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
