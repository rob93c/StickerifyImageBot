package com.github.stickerifier.stickerify.media;

/**
 * Class containing the constraints for Telegram stickers as documented in the documentation:
 *
 * @see <a href="https://core.telegram.org/stickers#static-stickers-and-emoji">Static stickers' constraints</a>
 * @see <a href="https://core.telegram.org/stickers#video-stickers-and-emoji">Video stickers' constraints</a>
 * @see <a href="https://core.telegram.org/stickers#creating-animations">Animated stickers' constraints</a>
 */
public final class MediaConstraints {

	static final int MAX_SIZE = 512;
	static final float MAX_VIDEO_FRAMES = 30F;
	static final long MAX_VIDEO_DURATION_MILLIS = 3_000L;
	static final String VP9_CODEC = "vp9";
	static final String MATROSKA_FORMAT = "matroska";
	static final long MAX_IMAGE_FILE_SIZE = 512_000L;
	static final long MAX_VIDEO_FILE_SIZE = 256_000L;
	static final long MAX_ANIMATION_FILE_SIZE = 64_000L;
	static final int MAX_ANIMATION_FRAME_RATE = 60;
	static final float MAX_ANIMATION_DURATION_SECONDS = 3F;

	private MediaConstraints() {
		throw new UnsupportedOperationException();
	}
}
