package com.github.stickerifier.stickerify.media;

import static com.github.stickerifier.stickerify.ResourceHelper.loadResource;
import static com.github.stickerifier.stickerify.media.MediaConstraints.MATROSKA_FORMAT;
import static com.github.stickerifier.stickerify.media.MediaConstraints.MAX_IMAGE_FILE_SIZE;
import static com.github.stickerifier.stickerify.media.MediaConstraints.MAX_VIDEO_FILE_SIZE;
import static com.github.stickerifier.stickerify.media.MediaConstraints.VP9_CODEC;
import static com.github.stickerifier.stickerify.media.MediaConverter.FFMPEG_LOCATOR;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.stickerifier.stickerify.junit.ClearTempFiles;
import com.github.stickerifier.stickerify.telegram.exception.TelegramApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ws.schild.jave.EncoderException;
import ws.schild.jave.MultimediaObject;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@ClearTempFiles
class MediaConverterTest {

	@Test
	void resizeImage() throws Exception {
		var jpgImage = loadResource("big.jpg");
		var result = MediaConverter.convert(jpgImage);

		assertImageConsistency(result, 512, 341);
	}

	private static void assertImageConsistency(File result, int expectedWidth, int expectedHeight) throws IOException {
		var image = ImageIO.read(result);
		var actualExtension = getExtension(result);

		assertAll("Image validation failed",
				() -> assertThat("image's extension must be png", actualExtension, is(equalTo(".png"))),
				() -> assertThat("image's width is not correct", image.getWidth(), is(equalTo(expectedWidth))),
				() -> assertThat("image's height is not correct", image.getHeight(), is(equalTo(expectedHeight))),
				() -> assertThat("image size should not exceed 512 KB", Files.size(result.toPath()), is(lessThanOrEqualTo(MAX_IMAGE_FILE_SIZE)))
		);
	}

	private static String getExtension(File file) {
		return file.getName().substring(file.getName().lastIndexOf('.'));
	}

	@Test
	void resizeRectangularImage() throws Exception {
		var jpgImage = loadResource("big.jpg");
		var result = MediaConverter.convert(jpgImage);

		assertImageConsistency(result, 512, 341);
	}

	@Test
	void resizeSmallImage() throws Exception {
		var pngImage = loadResource("small_image.png");
		var result = MediaConverter.convert(pngImage);

		assertImageConsistency(result, 512, 512);
	}

	@Test
	void noImageConversionNeeded() throws Exception {
		var pngImage = loadResource("valid.png");
		var result = MediaConverter.convert(pngImage);

		assertThat(result, is(nullValue()));
	}

	@Test
	void resizeWebpImage() throws Exception {
		var webpImage = loadResource("valid.webp");
		var result = MediaConverter.convert(webpImage);

		assertImageConsistency(result, 256, 512);
	}

	@Test
	void resizeFaviconImage() throws Exception {
		var faviconImage = loadResource("favicon.ico");
		var result = MediaConverter.convert(faviconImage);

		assertImageConsistency(result, 512, 512);
	}

	@Test
	void resizeTiffImage() throws Exception {
		var tiffImage = loadResource("valid.tiff");
		var result = MediaConverter.convert(tiffImage);

		assertImageConsistency(result, 512, 342);
	}

	@Test
	void resizePsdImage() throws Exception {
		var psdImage = loadResource("valid.psd");
		var result = MediaConverter.convert(psdImage);

		assertImageConsistency(result, 512, 384);
	}

	@Test
	void resizeDetailedImage() throws Exception {
		var detailedImage = loadResource("detailed.jpg");
		var result = MediaConverter.convert(detailedImage);

		assertImageConsistency(result, 512, 512);
	}

	@Test
	void resizeSvgImage() throws Exception {
		var svgImage = loadResource("valid.svg");
		var result = MediaConverter.convert(svgImage);

		assertImageConsistency(result, 512, 512);
	}

	@Test
	void convertLongMovVideo() throws Exception {
		var movVideo = loadResource("long.mov");
		var result = MediaConverter.convert(movVideo);

		assertVideoConsistency(result, 512, 288, 29.97F, 3_000L);
	}

	private static void assertVideoConsistency(File result, int expectedWidth, int expectedHeight, float expectedFrameRate, long expectedDuration) throws EncoderException {
		var mediaInfo = new MultimediaObject(result, FFMPEG_LOCATOR).getInfo();
		var videoInfo = mediaInfo.getVideo();
		var videoSize = videoInfo.getSize();

		var actualExtension = getExtension(result);

		assertAll("Video validation failed",
				() -> assertThat("video's extension must be webm", actualExtension, is(equalTo(".webm"))),
				() -> assertThat("video's width is not correct", videoSize.getWidth(), is(equalTo(expectedWidth))),
				() -> assertThat("video's height is not correct", videoSize.getHeight(), is(equalTo(expectedHeight))),
				() -> assertThat("video's frame rate is not correct", videoInfo.getFrameRate(), is(equalTo(expectedFrameRate))),
				() -> assertThat("video must be encoded with the VP9 codec", videoInfo.getDecoder(), startsWith(VP9_CODEC)),
				() -> assertThat("video's duration is not correct", mediaInfo.getDuration(), is(equalTo(expectedDuration))),
				() -> assertThat("video's format must be matroska", mediaInfo.getFormat(), is(equalTo(MATROSKA_FORMAT))),
				() -> assertThat("video must have no audio stream", mediaInfo.getAudio(), is(nullValue())),
				() -> assertThat("video size should not exceed 256 KB", Files.size(result.toPath()), is(lessThanOrEqualTo(MAX_VIDEO_FILE_SIZE)))
		);
	}

	@Test
	void convertMp4WithAudio() throws Exception {
		var mp4Video = loadResource("video_with_audio.mp4");
		var result = MediaConverter.convert(mp4Video);

		assertVideoConsistency(result, 512, 288, 29.97F, 3_000L);
	}

	@Test
	void convertM4vWithAudio() throws Exception {
		var m4vVideo = loadResource("video_with_audio.m4v");
		var result = MediaConverter.convert(m4vVideo);

		assertVideoConsistency(result, 512, 214, 23.98F, 3_000L);
	}

	@Test
	void convertShortAndLowFpsVideo() throws Exception {
		var webmVideo = loadResource("short_low_fps.webm");
		var result = MediaConverter.convert(webmVideo);

		assertVideoConsistency(result, 512, 288, 10F, 1_000L);
	}

	@Test
	void resizeSmallWebmVideo() throws Exception {
		var webmVideo = loadResource("small_video_sticker.webm");
		var result = MediaConverter.convert(webmVideo);

		assertVideoConsistency(result, 512, 212, 30F, 2_000L);
	}

	@Test
	void convertVerticalWebmVideo() throws Exception {
		var webmVideo = loadResource("vertical_video_sticker.webm");
		var result = MediaConverter.convert(webmVideo);

		assertVideoConsistency(result, 288, 512, 30F, 2_000L);
	}

	@Test
	void convertGifVideo() throws Exception {
		var gifVideo = loadResource("valid.gif");
		var result = MediaConverter.convert(gifVideo);

		assertVideoConsistency(result, 512, 274, 10F, 1_000L);
	}

	@Test
	void noVideoConversionNeeded() throws Exception {
		var webmVideo = loadResource("no_conversion_needed.webm");
		var result = MediaConverter.convert(webmVideo);

		assertThat(result, is(nullValue()));
	}

	@Test
	void noAnimatedStickerConversionNeeded() throws Exception {
		var animatedSticker = loadResource("animated_sticker.tgs");
		var result = MediaConverter.convert(animatedSticker);

		assertThat(result, is(nullValue()));
	}

	@Test
	void noLowFpsAnimatedStickerConversionNeeded() throws Exception {
		var animatedSticker = loadResource("low_fps_animated_sticker.tgs");
		var result = MediaConverter.convert(animatedSticker);

		assertThat(result, is(nullValue()));
	}

	@Test
	void unsupportedGzipArchive() {
		var archive = loadResource("unsupported_archive.gz");

		assertThrows(TelegramApiException.class, () -> MediaConverter.convert(archive));
	}

	@Test
	void unsupportedFile() {
		var document = loadResource("document.txt");

		assertThrows(TelegramApiException.class, () -> MediaConverter.convert(document));
	}

	@Nested
	@DisplayName("Concurrently convert")
	@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
	class ConcurrencyTest {

		@Test
		@DisplayName("mov videos")
		void concurrentMovVideoConversions() {
			var movVideo = loadResource("long.mov");

			executeConcurrentConversionsOf(movVideo);
		}

		private static void executeConcurrentConversionsOf(File inputFile) {
			final int concurrentRequests = 50;
			var failedConversions = new AtomicInteger(0);
			var failureReasons = ConcurrentHashMap.newKeySet();

			try (var executor = newFixedThreadPool(5, Thread.ofVirtual().factory())) {
				IntStream.range(0, concurrentRequests).forEach(_ -> executor.execute(() -> {
					try {
						MediaConverter.convert(inputFile);
					} catch (Throwable e) {
						failedConversions.incrementAndGet();
						failureReasons.add(e.getMessage());
					}
				}));
			}

			int failures = failedConversions.get();
			var errorMessage = "Unable to process %d concurrent requests: %d conversions failed: %s".formatted(concurrentRequests, failures, failureReasons);

			assertThat(errorMessage, failures, is(equalTo(0)));
		}

		@Test
		@DisplayName("mp4 videos")
		void concurrentMp4VideoConversions() {
			var mp4Video = loadResource("video_with_audio.mp4");

			executeConcurrentConversionsOf(mp4Video);
		}

		@Test
		@DisplayName("m4v videos")
		void concurrentM4vVideoConversions() {
			var m4vVideo = loadResource("video_with_audio.m4v");

			executeConcurrentConversionsOf(m4vVideo);
		}

		@Test
		@DisplayName("webm videos")
		void concurrentWebmVideoConversions() {
			var webmVideo = loadResource("small_video_sticker.webm");

			executeConcurrentConversionsOf(webmVideo);
		}

		@Test
		@DisplayName("gif videos")
		void concurrentGifVideoConversions() {
			var gifVideo = loadResource("valid.gif");

			executeConcurrentConversionsOf(gifVideo);
		}

		@Test
		@DisplayName("webp images")
		void concurrentWebpImageConversions() {
			var webpImage = loadResource("valid.webp");

			executeConcurrentConversionsOf(webpImage);
		}

		@Test
		@DisplayName("jpg images")
		void concurrentJpgImageConversions() {
			var jpgImage = loadResource("big.jpg");

			executeConcurrentConversionsOf(jpgImage);
		}

		@Test
		@DisplayName("png images")
		void concurrentPngImageConversions() {
			var pngImage = loadResource("big.png");

			executeConcurrentConversionsOf(pngImage);
		}

		@Test
		@DisplayName("ico images")
		void concurrentFaviconImageConversions() {
			var faviconImage = loadResource("favicon.ico");

			executeConcurrentConversionsOf(faviconImage);
		}

		@Test
		@DisplayName("tiff images")
		void concurrentTiffImageConversions() {
			var tiffImage = loadResource("valid.tiff");

			executeConcurrentConversionsOf(tiffImage);
		}

		@Test
		@DisplayName("psd images")
		void concurrentPsdImageConversions() {
			var psdImage = loadResource("valid.psd");

			executeConcurrentConversionsOf(psdImage);
		}

		@Test
		@DisplayName("svg images")
		void concurrentSvgImageConversions() {
			var psdImage = loadResource("valid.svg");

			executeConcurrentConversionsOf(psdImage);
		}
	}
}
