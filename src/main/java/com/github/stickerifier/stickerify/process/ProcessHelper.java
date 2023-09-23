package com.github.stickerifier.stickerify.process;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.github.stickerifier.stickerify.telegram.exception.TelegramApiException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.Semaphore;

public final class ProcessHelper {

	private static final Semaphore SEMAPHORE = new Semaphore(5);

	/**
	 * Executes passed-in command and ensures it completed successfully.
	 * The method allows at most 5 processes to run concurrently.
	 *
	 * @param command the command to be executed
	 * @return the output of the command
	 * @throws TelegramApiException either if:
	 * <ul>
	 *     <li>the command was unsuccessful
	 *     <li>the waiting time elapsed
	 *     <li>an unexpected failure happened running the command
	 * </ul>
	 */
	public static String executeCommand(final String[] command) throws TelegramApiException {
		Process process = null;

		try {
			SEMAPHORE.acquire();
			process = new ProcessBuilder(command).start();
			var processExited = process.waitFor(1, MINUTES);

			if (!processExited || process.exitValue() != 0) {
				var reason = processExited ? "successfully" : "in time";
				var output = toString(process.getErrorStream());
				throw new TelegramApiException("The command {} couldn't complete {}:\n{}", command[0], reason, output);
			}

			return toString(process.getInputStream());
		} catch (IOException | InterruptedException e) {
			throw new TelegramApiException(e);
		} finally {
			SEMAPHORE.release();
			Objects.requireNonNull(process).destroy();
		}
	}

	private static String toString(InputStream stream) throws IOException {
		return new String(stream.readAllBytes(), UTF_8);
	}

	private ProcessHelper() {
		throw new UnsupportedOperationException();
	}
}
