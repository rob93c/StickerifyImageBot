package com.github.stickerifier.stickerify.process;

import org.jetbrains.annotations.NotNull;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MINUTES;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Semaphore;

public final class ProcessRunner {

	static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("windows");
	private static final int MAX_CONCURRENT_PROCESSES = IS_WINDOWS ? 4 : 5;
	private static final Semaphore SEMAPHORE = new Semaphore(MAX_CONCURRENT_PROCESSES);

	/**
	 * Executes passed-in command and ensures it completed successfully.
	 * Based on the operating system, the method limits the number of processes running concurrently:
	 * on Windows they will be at most 4, on every other system they will be at most 5.
	 *
	 * @param command the command to be executed
	 * @return the output of the command
	 * @throws ProcessException either if:
	 * <ul>
	 *     <li>the command was unsuccessful
	 *     <li>the waiting time elapsed
	 *     <li>an unexpected failure happened running the command
	 * </ul>
	 */
	@NotNull
	public static String executeCommand(final String[] command) throws ProcessException {
		Process process = null;

		try {
			SEMAPHORE.acquire();
			process = new ProcessBuilder(command).start();
			var processExited = process.waitFor(1, MINUTES);

			if (!processExited || process.exitValue() != 0) {
				var reason = processExited ? "successfully" : "in time";
				var output = readStream(process.getErrorStream());
				throw new ProcessException(STR."The command \{command[0]} couldn't complete \{reason}: \{output}");
			}

			return readProcessOutput(process);
		} catch (IOException e) {
			throw new ProcessException(e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ProcessException(e);
		} finally {
			SEMAPHORE.release();
			if (process != null) {
				process.destroy();
			}
		}
	}

	/**
	 * Processes the content of the stream and retrieves its UTF-8 string representation.
	 *
	 * @param stream the stream to be decoded
	 * @return the UTF-8 representation of passed-in stream
	 * @throws IOException if an error occurs reading stream's bytes
	 */
	private static String readStream(InputStream stream) throws IOException {
		return new String(stream.readAllBytes(), UTF_8);
	}

	/**
	 * Reads the content of the input stream of the process.
	 * If the input stream is empty, the content of the error stream is returned.
	 *
	 * @param process the process
	 * @return the output of the process
	 * @throws IOException if an error occurs reading stream's bytes
	 */
	private static String readProcessOutput(Process process) throws IOException {
		var inputStream = readStream(process.getInputStream());
		var output = inputStream.isEmpty() ? readStream(process.getErrorStream()) : inputStream;

		return output.trim();
	}

	private ProcessRunner() {
		throw new UnsupportedOperationException();
	}
}
