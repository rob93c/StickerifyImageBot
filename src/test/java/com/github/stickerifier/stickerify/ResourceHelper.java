package com.github.stickerifier.stickerify;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;

public final class ResourceHelper {

	public static File loadResource(String filename) {
		var resource = ResourceHelper.class.getClassLoader().getResource(filename);
		assumeTrue(resource != null, STR."Test resource [\{filename}] not found.");

		return new File(resource.getFile());
	}

	private ResourceHelper() {
		throw new UnsupportedOperationException();
	}
}
