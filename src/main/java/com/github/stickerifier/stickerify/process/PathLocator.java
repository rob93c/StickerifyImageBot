package com.github.stickerifier.stickerify.process;

import static com.github.stickerifier.stickerify.process.ProcessRunner.IS_WINDOWS;
import static java.lang.System.lineSeparator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ws.schild.jave.process.ProcessLocator;

/**
 * Custom locator class to be used by Jave to find the path where FFmpeg is installed at in the system.
 *
 * @see ProcessLocator
 */
public class PathLocator implements ProcessLocator {

	private static final Logger LOGGER = LoggerFactory.getLogger(PathLocator.class);

	private static final String[] FIND_FFMPEG = { IS_WINDOWS ? "where" : "which", "ffmpeg" };

	private String ffmpegLocation = System.getenv("FFMPEG_PATH");

	public PathLocator() {
		try {
			if (ffmpegLocation == null || ffmpegLocation.isBlank()) {
				ffmpegLocation = ProcessRunner.executeCommand(FIND_FFMPEG).split(lineSeparator())[0];
			}

			LOGGER.atInfo().log("FFmpeg is installed at {}", ffmpegLocation);
		} catch (ProcessException e) {
			LOGGER.atError().setCause(e).log("Unable to detect FFmpeg's installation path");
		}
	}

	@Override
	public String getExecutablePath() {
		return ffmpegLocation;
	}
}
