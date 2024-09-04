package com.github.stickerifier.stickerify.media;

import static com.github.stickerifier.stickerify.media.MediaConstraints.MATROSKA_FORMAT;
import static com.github.stickerifier.stickerify.media.MediaConstraints.MAX_ANIMATION_DURATION_SECONDS;
import static com.github.stickerifier.stickerify.media.MediaConstraints.MAX_ANIMATION_FILE_SIZE;
import static com.github.stickerifier.stickerify.media.MediaConstraints.MAX_ANIMATION_FRAME_RATE;
import static com.github.stickerifier.stickerify.media.MediaConstraints.MAX_IMAGE_FILE_SIZE;
import static com.github.stickerifier.stickerify.media.MediaConstraints.MAX_SIDE_LENGTH;
import static com.github.stickerifier.stickerify.media.MediaConstraints.MAX_VIDEO_DURATION_MILLIS;
import static com.github.stickerifier.stickerify.media.MediaConstraints.MAX_VIDEO_FILE_SIZE;
import static com.github.stickerifier.stickerify.media.MediaConstraints.MAX_VIDEO_FRAMES;
import static com.github.stickerifier.stickerify.media.MediaConstraints.VP9_CODEC;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.github.stickerifier.stickerify.exception.CorruptedVideoException;
import com.github.stickerifier.stickerify.exception.FileOperationException;
import com.github.stickerifier.stickerify.exception.MediaException;
import com.github.stickerifier.stickerify.exception.MediaOptimizationException;
import com.github.stickerifier.stickerify.exception.ProcessException;
import com.github.stickerifier.stickerify.process.PathLocator;
import com.github.stickerifier.stickerify.process.ProcessHelper;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.nio.ImageIOReader;
import com.sksamuel.scrimage.nio.ImmutableImageLoader;
import com.sksamuel.scrimage.webp.WebpWriter;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ws.schild.jave.EncoderException;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.info.MultimediaInfo;
import ws.schild.jave.process.ProcessLocator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.zip.GZIPInputStream;

public final class MediaHelper {

	static {
		System.setProperty("java.awt.headless", "true");
		ImageIO.setUseCache(false);
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(MediaHelper.class);

	private static final Gson GSON = new Gson();
	static final ProcessLocator FFMPEG_LOCATOR = new PathLocator();
	private static final int PRESERVE_ASPECT_RATIO = -2;
	private static final List<String> SUPPORTED_VIDEOS = List.of("image/gif", "video/quicktime", "video/webm",
			"video/mp4", "video/x-m4v", "application/x-matroska");

	private static ConcurrentMap<String, List<ImageReader>> imageReadersCache = loadImageReaders();

	/**
	 * Based on the type of passed-in file, it converts it into the proper media.
	 * If no conversion was needed, {@code null} is returned.
	 *
	 * @param inputFile the file to convert
	 * @return a resized and converted file
	 * @throws MediaException if the file is not supported or if the conversion failed
	 */
	public static File convert(File inputFile) throws MediaException {
		var mimeType = detectMimeType(inputFile);

		try {
			if (isSupportedVideo(mimeType)) {
				return convertToWebm(inputFile);
			}

			if (isAnimatedStickerCompliant(inputFile, mimeType)) {
				LOGGER.atInfo().log("The animated sticker doesn't need conversion");

				return null;
			}

			var image = toImage(inputFile, mimeType);
			if (image != null) {
				boolean isFileSizeCompliant = isFileSizeLowerThan(inputFile, MAX_IMAGE_FILE_SIZE);

				return convertToWebp(image, mimeType, isFileSizeCompliant);
			}
		} catch (MediaException e) {
			LOGGER.atWarn().setCause(e).log("The file with {} MIME type could not be converted", mimeType);
			throw e;
		}

		throw new MediaException("The file with {} MIME type is not supported", mimeType);
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
		} catch (IOException _) {
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
	 * @throws FileOperationException if an error occurred retrieving the size of the file
	 */
	private static boolean isAnimatedStickerCompliant(File file, String mimeType) throws FileOperationException {
		if ("application/gzip".equals(mimeType)) {
			var uncompressedContent = "";

			try (var gzipInputStream = new GZIPInputStream(new FileInputStream(file))) {
				uncompressedContent = new String(gzipInputStream.readAllBytes(), UTF_8);
			} catch (IOException _) {
				LOGGER.atError().log("Unable to retrieve gzip content from file {}", file.getName());
			}

			try {
				var sticker = GSON.fromJson(uncompressedContent, AnimationDetails.class);

				return isAnimationCompliant(sticker) && isFileSizeLowerThan(file, MAX_ANIMATION_FILE_SIZE);
			} catch (JsonSyntaxException _) {
				LOGGER.atInfo().log("The archive isn't an animated sticker");
			}
		}

		return false;
	}

	private record AnimationDetails(@SerializedName("w") int width, @SerializedName("h") int height, @SerializedName("fr") int frameRate, @SerializedName("ip") float start, @SerializedName("op") float end) {
		private float duration() {
			return (end - start) / frameRate;
		}
	}

	/**
	 * Checks if passed-in animation is already compliant with Telegram's requisites.
	 * If so, conversion won't take place and no file will be returned to the user.
	 *
	 * @param animation the animation to check
	 * @return {@code true} if the animation is compliant
	 */
	private static boolean isAnimationCompliant(AnimationDetails animation) {
		return animation != null
				&& animation.frameRate() <= MAX_ANIMATION_FRAME_RATE
				&& animation.duration() <= MAX_ANIMATION_DURATION_SECONDS
				&& animation.width() == MAX_SIDE_LENGTH
				&& animation.height() == MAX_SIDE_LENGTH;
	}

	/**
	 * Checks that passed-in file's size does not exceed specified threshold.
	 *
	 * @param file the file to check
	 * @param threshold max allowed file size
	 * @return {@code true} if file's size is compliant
	 * @throws FileOperationException if an error occurred retrieving the size of the file
	 */
	private static boolean isFileSizeLowerThan(File file, long threshold) throws FileOperationException {
		try {
			return Files.size(file.toPath()) <= threshold;
		} catch (IOException e) {
			throw new FileOperationException(e);
		}
	}

	/**
	 * Retrieve the image from the passed-in file.
	 * If the file isn't a supported image, {@code null} is returned.
	 *
	 * @param file the file to read
	 * @param mimeType the MIME type of the file
	 * @return the image, if supported by {@link ImageIO}
	 * @throws FileOperationException if an error occurred processing passed-in file
	 */
	private static ImmutableImage toImage(File file, String mimeType) throws FileOperationException {
		try {
			if ("image/webp".equals(mimeType)) {
				return ImmutableImage.loader().fromFile(file);
			}

			var imageReader = new ImageIOReader(getImageReaders(mimeType));

			return ImmutableImageLoader.create()
					.withImageReaders(List.of(imageReader))
					.fromFile(file);
		} catch (IOException _) {
			return null;
		}
	}

	private static List<ImageReader> getImageReaders(String mimeType) {
		var imageReaders = imageReadersCache.get(mimeType);

		if (imageReaders == null) {
			var readers = ImageIO.getImageReadersByMIMEType(mimeType);
			imageReaders = new ArrayList<>();
			readers.forEachRemaining(imageReaders::add);
			imageReadersCache.put(mimeType, imageReaders);

			if (imageReaders.isEmpty()) {
				LOGGER.atInfo().log("No image readers found for {} MIME type", mimeType);
			}
		}

		return imageReaders;
	}

	private static ConcurrentMap<String, List<ImageReader>> loadImageReaders() {
		imageReadersCache = new ConcurrentHashMap<>();

		var imageMimeTypes = List.of("image/gif", "image/png",
				"image/jpeg", "image/svg+xml", "image/tiff",
				"image/vnd.adobe.photoshop", "image/vnd.microsoft.icon");

		imageMimeTypes.forEach(MediaHelper::getImageReaders);

		return imageReadersCache;
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
	 * Given an image file, it converts it to a WebP file of the proper dimension (max 512 x 512).
	 *
	 * @param image the image to convert to WebP
	 * @param mimeType the MIME type of the file
	 * @param isFileSizeCompliant {@code true} if the file does not exceed Telegram's limit
	 * @return converted image, {@code null} if no conversion was needed
	 * @throws MediaException if an error occurred processing passed-in image
	 */
	private static File convertToWebp(ImmutableImage image, String mimeType, boolean isFileSizeCompliant) throws MediaException {
		if (isImageCompliant(image, mimeType) && isFileSizeCompliant) {
			LOGGER.atInfo().log("The image doesn't need conversion");

			return null;
		}

		return createWebpFile(image);
	}

	/**
	 * Checks if passed-in image is already compliant with Telegram's requisites.
	 * If so, conversion won't take place and no file will be returned to the user.
	 *
	 * @param image the image to check
	 * @param mimeType the MIME type of the file
	 * @return {@code true} if the file is compliant
	 */
	private static boolean isImageCompliant(ImmutableImage image, String mimeType) {
		return ("image/png".equals(mimeType) || "image/webp".equals(mimeType)) && isSizeCompliant(image.width, image.height);
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
		return (width == MAX_SIDE_LENGTH && height <= MAX_SIDE_LENGTH) || (height == MAX_SIDE_LENGTH && width <= MAX_SIDE_LENGTH);
	}

	/**
	 * Creates a new <i>.webp</i> file from passed-in {@code image}, resizing it with sides of max 512 pixels each.
	 *
	 * @param image the image to convert to WebP
	 * @return converted image
	 * @throws MediaException if an error occurs creating the temp file or
	 * if the image size could not be reduced enough to meet Telegram's requirements
	 */
	private static File createWebpFile(ImmutableImage image) throws MediaException {
		var webpImage = createTempFile("webp");
		var deleteTempFile = false;

		try {
			image.max(MAX_SIDE_LENGTH, MAX_SIDE_LENGTH).output(WebpWriter.MAX_LOSSLESS_COMPRESSION, webpImage);

			if (!isFileSizeLowerThan(webpImage, MAX_IMAGE_FILE_SIZE)) {
				deleteTempFile = true;
				throw new MediaOptimizationException("The image size could not be reduced enough to meet Telegram's requirements");
			}
		} catch (IOException e) {
			deleteTempFile = true;
			throw new FileOperationException("An unexpected error occurred trying to create resulting image", e);
		} finally {
			if (deleteTempFile) {
				deleteFile(webpImage);
			}
		}

		return webpImage;
	}

	/**
	 * Creates a new temp file with desired extension.
	 *
	 * @param fileExtension the extension of the new file
	 * @return a new temp file
	 * @throws FileOperationException if an error occurs creating the temp file
	 */
	private static File createTempFile(String fileExtension) throws FileOperationException {
		try {
			return File.createTempFile("Stickerify-", "." + fileExtension);
		} catch (IOException e) {
			throw new FileOperationException("An error occurred creating a new temp file", e);
		}
	}

	/**
	 * Deletes passed-in {@code file}.
	 *
	 * @param file the file to be removed
	 * @throws FileOperationException if an error occurs deleting the file
	 */
	private static void deleteFile(File file) throws FileOperationException {
		try {
			Files.deleteIfExists(file.toPath());
		} catch (IOException e) {
			throw new FileOperationException("An error occurred deleting the file", e);
		}
	}

	/**
	 * Given a video file, it converts it to a WebM file of the proper dimension (max 512 x 512),
	 * based on the requirements specified by <a href="https://core.telegram.org/stickers/webm-vp9-encoding">Telegram documentation</a>.
	 *
	 * @param file the file to convert
	 * @return converted video, {@code null} if no conversion was needed
	 * @throws MediaException if file conversion is not successful
	 */
	private static File convertToWebm(File file) throws MediaException {
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
	 * @throws CorruptedVideoException if an error occurred retrieving video information
	 */
	private static MultimediaInfo retrieveMultimediaInfo(File file) throws CorruptedVideoException {
		try {
			return new MultimediaObject(file, FFMPEG_LOCATOR).getInfo();
		} catch (EncoderException e) {
			throw new CorruptedVideoException("The video could not be processed successfully", e);
		}
	}

	/**
	 * Checks if passed-in file is already compliant with Telegram's requisites.
	 * If so, conversion won't take place and no file will be returned to the user.
	 *
	 * @param file the file to check
	 * @param mediaInfo video's multimedia information
	 * @return {@code true} if the file is compliant
	 * @throws FileOperationException if an error occurred retrieving the size of the file
	 */
	private static boolean isVideoCompliant(File file, MultimediaInfo mediaInfo) throws FileOperationException {
		var videoInfo = mediaInfo.getVideo();
		var videoSize = videoInfo.getSize();

		return isSizeCompliant(videoSize.getWidth(), videoSize.getHeight())
				&& videoInfo.getFrameRate() <= MAX_VIDEO_FRAMES
				&& videoInfo.getDecoder().startsWith(VP9_CODEC)
				&& mediaInfo.getDuration() > 0L
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
	 * @throws MediaException if file conversion is not successful
	 */
	private static File convertWithFfmpeg(File file, MultimediaInfo mediaInfo) throws MediaException {
		var webmVideo = createTempFile("webm");
		var videoDetails = getResultingVideoDetails(mediaInfo);

		var ffmpegCommand = new String[] {
				"ffmpeg",
				"-v", "error",
				"-i", file.getAbsolutePath(),
				"-vf", "scale=" + videoDetails.width() + ":" + videoDetails.height() + ",fps=" + videoDetails.frameRate(),
				"-c:v", "libvpx-" + VP9_CODEC,
				"-b:v", "256k",
				"-crf", "32",
				"-g", "60",
				"-an",
				"-t", videoDetails.duration(),
				"-y", webmVideo.getAbsolutePath()
		};

		try {
			ProcessHelper.executeCommand(ffmpegCommand);
		} catch (ProcessException e) {
			deleteFile(webmVideo);
			throw new MediaException(e.getMessage());
		}

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
		int width = isWidthBigger ? MAX_SIDE_LENGTH : PRESERVE_ASPECT_RATIO;
		int height = isWidthBigger ? PRESERVE_ASPECT_RATIO : MAX_SIDE_LENGTH;

		return new ResultingVideoDetails(width, height, frameRate, String.valueOf(duration));
	}

	private record ResultingVideoDetails(int width, int height, float frameRate, String duration) {}

	private MediaHelper() {
		throw new UnsupportedOperationException();
	}
}
