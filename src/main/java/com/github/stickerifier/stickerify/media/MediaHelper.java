package com.github.stickerifier.stickerify.media;

import static com.github.stickerifier.stickerify.media.MediaConstraints.ANIMATION_FRAMERATE;
import static com.github.stickerifier.stickerify.media.MediaConstraints.MATROSKA_FORMAT;
import static com.github.stickerifier.stickerify.media.MediaConstraints.MAX_ANIMATION_DURATION_SECONDS;
import static com.github.stickerifier.stickerify.media.MediaConstraints.MAX_ANIMATION_FILE_SIZE;
import static com.github.stickerifier.stickerify.media.MediaConstraints.MAX_SIZE;
import static com.github.stickerifier.stickerify.media.MediaConstraints.MAX_VIDEO_DURATION_MILLIS;
import static com.github.stickerifier.stickerify.media.MediaConstraints.MAX_VIDEO_FILE_SIZE;
import static com.github.stickerifier.stickerify.media.MediaConstraints.MAX_VIDEO_FRAMES;
import static com.github.stickerifier.stickerify.media.MediaConstraints.VP9_CODEC;

import com.github.stickerifier.stickerify.process.PathLocator;
import com.github.stickerifier.stickerify.process.ProcessHelper;
import com.github.stickerifier.stickerify.telegram.exception.TelegramApiException;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import org.apache.tika.Tika;
import org.imgscalr.Scalr;
import org.imgscalr.Scalr.Mode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ws.schild.jave.EncoderException;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.info.MultimediaInfo;
import ws.schild.jave.process.ProcessLocator;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.GZIPInputStream;

public final class MediaHelper {

	static {
		System.setProperty("java.awt.headless", "true");
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(MediaHelper.class);

	private static final Gson GSON = new Gson();
	static final ProcessLocator FFMPEG_LOCATOR = new PathLocator();
	private static final int PRESERVE_ASPECT_RATIO = -2;
	private static final List<String> SUPPORTED_VIDEOS = List.of("image/gif", "video/quicktime", "video/webm", "video/mp4", "application/x-matroska");

	/**
	 * Based on the type of passed-in file, it converts it into the proper media.
	 * If no conversion was needed, {@code null} is returned.
	 *
	 * @param inputFile the file to convert
	 * @return a resized and converted file
	 * @throws TelegramApiException if the file is not supported or if the conversion failed
	 */
	public static File convert(File inputFile) throws TelegramApiException {
		var mimeType = detectMimeType(inputFile);

		try {
			if (isSupportedVideo(mimeType)) {
				return convertToWebm(inputFile);
			}

			if (isAnimatedStickerCompliant(inputFile, mimeType)) {
				LOGGER.atInfo().log("The animated sticker doesn't need conversion");

				return null;
			}

			var image = toImage(inputFile);
			if (image != null) {
				return convertToPng(image, mimeType);
			}
		} catch (TelegramApiException e) {
			LOGGER.atWarn().setCause(e).log("The file with {} MIME type could not be converted", mimeType);
			throw e;
		}

		throw new TelegramApiException("The file with {} MIME type is not supported", mimeType);
	}

	/**
	 * Analyses the file in order to detect its media type.
	 *
	 * @param file the file sent to the bot
	 * @return the MIME type of the passed-in file
	 */
	private static String detectMimeType(File file) {
		String mimeType = null;

		try {
			mimeType = new Tika().detect(file);

			LOGGER.atDebug().log("The file has {} MIME type", mimeType);
		} catch (IOException e) {
			LOGGER.atError().log("Unable to retrieve MIME type for file {}", file.getName());
		}

		return mimeType;
	}

	/**
	 * Checks if the file is a {@code gzip} archive, then it reads its content and verifies if it's a valid JSON.
	 * Once JSON information are retrieved, they are validated against Telegram's requirements.
	 *
	 * @param file the file to check
	 * @param mimeType the MIME type of the file
	 * @return {@code true} if the file is compliant
	 */
	private static boolean isAnimatedStickerCompliant(File file, String mimeType) throws TelegramApiException {
		if ("application/gzip".equals(mimeType)) {
			String uncompressedContent = "";

			try (var gzipInputStream = new GZIPInputStream(new FileInputStream(file))) {
				uncompressedContent = new String(gzipInputStream.readAllBytes(), StandardCharsets.UTF_8);
			} catch (IOException e) {
				LOGGER.atError().log("Unable to retrieve gzip content from file {}", file.getName());
			}

			try {
				var sticker = GSON.fromJson(uncompressedContent, AnimationDetails.class);

				return isAnimationCompliant(sticker) && isFileSizeLowerThan(file, MAX_ANIMATION_FILE_SIZE);
			} catch (JsonSyntaxException e) {
				LOGGER.atInfo().log("The archive isn't an animated sticker");
			}
		}

		return false;
	}

	private record AnimationDetails(
			@SerializedName("w") int width,
			@SerializedName("h") int height,
			@SerializedName("fr") int frameRate,
			@SerializedName("ip") int start,
			@SerializedName("op") int end
	) {}

	/**
	 * Checks if passed-in animation is already compliant with Telegram's requisites.
	 * If so, conversion won't take place and no file will be returned to the user.
	 *
	 * @param animation the animation to check
	 * @return {@code true} if the animation is compliant
	 */
	private static boolean isAnimationCompliant(AnimationDetails animation) {
		return animation != null && animation.frameRate() == ANIMATION_FRAMERATE
				&& animation.end() - animation.start() <= MAX_ANIMATION_DURATION_SECONDS
				&& animation.width() == MAX_SIZE && animation.width() == animation.height();
	}

	/**
	 * Checks that passed-in file's size does not exceed specified threshold.
	 *
	 * @param file the file to check
	 * @param threshold max allowed file size
	 * @return {@code true} if file's size is compliant
	 * @throws TelegramApiException if an error occurred retrieving the size of the file
	 */
	private static boolean isFileSizeLowerThan(File file, long threshold) throws TelegramApiException {
		try {
			return Files.size(file.toPath()) <= threshold;
		} catch (IOException e) {
			throw new TelegramApiException(e);
		}
	}

	/**
	 * Retrieve the image from the passed-in file.
	 * If the file isn't a supported image, {@code null} is returned.
	 *
	 * @param file the file to read
	 * @return the image, if supported by {@link ImageIO}
	 * @throws TelegramApiException if an error occurred processing passed-in file
	 */
	private static BufferedImage toImage(File file) throws TelegramApiException {
		try {
			return ImageIO.read(file);
		} catch (IOException e) {
			throw new TelegramApiException("Unable to retrieve the image from passed-in file", e);
		}
	}

	/**
	 * Checks if the MIME type corresponds to one of the supported video formats.
	 *
	 * @param mimeType the MIME type to check
	 * @return {@code true} if the MIME type is supported
	 */
	private static boolean isSupportedVideo(String mimeType) {
		return SUPPORTED_VIDEOS.stream().anyMatch(format -> format.equals(mimeType));
	}

	/**
	 * Given an image file, it converts it to a png file of the proper dimension (max 512 x 512).
	 *
	 * @param image the image to convert to png
	 * @param mimeType the MIME type of the file
	 * @return converted image, {@code null} if no conversion was required
	 * @throws TelegramApiException if an error occurred processing passed-in image
	 */
	private static File convertToPng(BufferedImage image, String mimeType) throws TelegramApiException {
		if (isImageCompliant(image, mimeType)) {
			LOGGER.atInfo().log("The image doesn't need conversion");

			return null;
		}

		try {
			return createPngFile(resizeImage(image));
		} finally {
			image.flush();
		}
	}

	/**
	 * Checks if passed-in image is already compliant with Telegram's requisites.
	 * If so, conversion won't take place and no file will be returned to the user.
	 *
	 * @param image the image to check
	 * @param mimeType the MIME type of the file
	 * @return {@code true} if the file is compliant
	 */
	private static boolean isImageCompliant(BufferedImage image, String mimeType) {
		return ("image/png".equals(mimeType) || "image/webp".equals(mimeType)) && isSizeCompliant(image.getWidth(), image.getHeight());
	}

	/**
	 * Checks that either width or height is 512 pixels
	 * and the other is 512 pixels or fewer.
	 *
	 * @param width the width to be checked
	 * @param height the width to be checked
	 * @return {@code true} if the video has valid dimensions
	 */
	private static boolean isSizeCompliant(int width, int height) {
		return (width == MAX_SIZE && height <= MAX_SIZE) || (height == MAX_SIZE && width <= MAX_SIZE);
	}

	/**
	 * Given an image, it returns its resized version with sides of max 512 pixels each.
	 *
	 * @param image the image to be resized
	 * @return resized image
	 */
	private static BufferedImage resizeImage(BufferedImage image) {
		return Scalr.resize(image, Mode.AUTOMATIC, MAX_SIZE);
	}

	/**
	 * Creates a new <i>.png</i> file from passed-in {@code image}.
	 *
	 * @param image the image to convert to png
	 * @return png image
	 * @throws TelegramApiException if an error occurs creating the temp file
	 */
	private static File createPngFile(BufferedImage image) throws TelegramApiException {
		var pngImage = createTempFile("png");

		try {
			ImageIO.write(image, "png", pngImage);
		} catch (IOException e) {
			throw new TelegramApiException("An unexpected error occurred trying to create resulting image", e);
		} finally {
			image.flush();
		}

		return pngImage;
	}

	/**
	 * Creates a new temp file of the desired type.
	 *
	 * @param fileType the extension of the new file
	 * @return a new temp file
	 * @throws TelegramApiException if an error occurs creating the temp file
	 */
	private static File createTempFile(String fileType) throws TelegramApiException {
		try {
			return File.createTempFile("Stickerify-", "." + fileType);
		} catch (IOException e) {
			throw new TelegramApiException("An error occurred creating a new temp file", e);
		}
	}

	/**
	 * Given a video file, it converts it to a webm file of the proper dimension (max 512 x 512),
	 * based on the requirements specified by <a href="https://core.telegram.org/stickers/webm-vp9-encoding">Telegram documentation</a>.
	 *
	 * @param file the file to convert
	 * @return converted video, {@code null} if no conversion was required
	 * @throws TelegramApiException if file conversion is not successful
	 */
	private static File convertToWebm(File file) throws TelegramApiException {
		var mediaInfo = retrieveMultimediaInfo(file);

		if (isVideoCompliant(file, mediaInfo)) {
			LOGGER.atInfo().log("The video doesn't need conversion");

			return null;
		}

		return convertWithFfmpeg(file, mediaInfo);
	}

	/**
	 * Convenience method to retrieve multimedia information of a file.
	 *
	 * @param file the video to check
	 * @return passed-in video's multimedia information
	 * @throws TelegramApiException if an error occurred encoding the video
	 */
	private static MultimediaInfo retrieveMultimediaInfo(File file) throws TelegramApiException {
		try {
			return new MultimediaObject(file, FFMPEG_LOCATOR).getInfo();
		} catch (EncoderException e) {
			throw new TelegramApiException(e);
		}
	}

	/**
	 * Checks if passed-in file is already compliant with Telegram's requisites.
	 * If so, conversion won't take place and no file will be returned to the user.
	 *
	 * @param file the file to check
	 * @param mediaInfo video's multimedia information
	 * @return {@code true} if the file is compliant
	 * @throws TelegramApiException if an error occurred retrieving the size of the file
	 */
	private static boolean isVideoCompliant(File file, MultimediaInfo mediaInfo) throws TelegramApiException {
		var videoInfo = mediaInfo.getVideo();
		var videoSize = videoInfo.getSize();

		return isSizeCompliant(videoSize.getWidth(), videoSize.getHeight())
				&& videoInfo.getFrameRate() <= MAX_VIDEO_FRAMES
				&& videoInfo.getDecoder().startsWith(VP9_CODEC)
				&& mediaInfo.getDuration() <= MAX_VIDEO_DURATION_MILLIS
				&& mediaInfo.getAudio() == null
				&& MATROSKA_FORMAT.equals(mediaInfo.getFormat())
				&& isFileSizeLowerThan(file, MAX_VIDEO_FILE_SIZE);
	}

	/**
	 * Converts the passed-in file using FFmpeg applying Telegram's video stickers' constraints.
	 *
	 * @param file the file to convert
	 * @param mediaInfo video's multimedia information
	 * @return converted video
	 * @throws TelegramApiException if file conversion is not successful
	 */
	private static File convertWithFfmpeg(File file, MultimediaInfo mediaInfo) throws TelegramApiException {
		var webmVideo = createTempFile("webm");
		var videoDetails = getResultingVideoDetails(mediaInfo);

		var ffmpegCommand = new String[] {
				"ffmpeg",
				"-v", "error",
				"-i", file.getAbsolutePath(),
				"-vf", "scale = " + videoDetails.width() + ":" + videoDetails.height() + ", fps = " + videoDetails.frameRate(),
				"-c:v", "libvpx-" + VP9_CODEC,
				"-b:v", "256k",
				"-crf", "32",
				"-g", "60",
				"-an",
				"-t", videoDetails.duration(),
				"-y", webmVideo.getAbsolutePath()
		};

		ProcessHelper.executeCommand(ffmpegCommand);

		return webmVideo;
	}

	/**
	 * Convenience method to group resulting video's details,
	 * calculated checking passed-in media info against Telegram's constraints.
	 *
	 * @param mediaInfo video's multimedia information
	 * @return resulting video's details
	 */
	private static ResultingVideoDetails getResultingVideoDetails(MultimediaInfo mediaInfo) {
		var videoInfo = mediaInfo.getVideo();
		float frameRate = Math.min(videoInfo.getFrameRate(), MAX_VIDEO_FRAMES);
		long duration = Math.min(mediaInfo.getDuration(), MAX_VIDEO_DURATION_MILLIS) / 1_000L;

		boolean isWidthBigger = videoInfo.getSize().getWidth() >= videoInfo.getSize().getHeight();
		int width = isWidthBigger ? MAX_SIZE : PRESERVE_ASPECT_RATIO;
		int height = isWidthBigger ? PRESERVE_ASPECT_RATIO : MAX_SIZE;

		return new ResultingVideoDetails(width, height, frameRate, String.valueOf(duration));
	}

	private record ResultingVideoDetails(int width, int height, float frameRate, String duration) {}

	private MediaHelper() {
		throw new UnsupportedOperationException();
	}
}
