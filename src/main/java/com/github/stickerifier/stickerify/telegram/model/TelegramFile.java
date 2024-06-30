package com.github.stickerifier.stickerify.telegram.model;

public record TelegramFile(String id, Long size) {
	public static final TelegramFile NOT_SUPPORTED = new TelegramFile(null, null);
	public static final TelegramFile TOO_LARGE = new TelegramFile(null, Long.MAX_VALUE);

	private static final long MAX_DOWNLOADABLE_FILE_SIZE_IN_BYTES = 20_000_000L;

	public boolean canBeDownloaded() {
		return size == null || size <= MAX_DOWNLOADABLE_FILE_SIZE_IN_BYTES;
	}

	@Override
	public String toString() {
		return id;
	}
}
