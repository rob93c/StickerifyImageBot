package com.github.stickerifier.stickerify.junit;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation used to clear temp files generated by unit tests during their execution.
 * <p>
 * When a class is annotated with {@code @ClearTempFiles}, the files starting
 * either with <i>Stickerify-</i> or <i>OriginalFile-</i> inside the temp folder
 * will be deleted <b>after all</b> its tests completed.
 *
 * @see TempFilesCleanerExtension
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@ExtendWith(TempFilesCleanerExtension.class)
public @interface ClearTempFiles {}
