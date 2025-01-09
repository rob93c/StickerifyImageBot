FROM eclipse-temurin AS builder

# bump: libwebp /LIBWEBP_VERSION=([\d.]+)/ git:https://chromium.googlesource.com/webm/libwebp.git|^1
# bump: libwebp after ./hashupdate Dockerfile LIBWEBP $LATEST
ARG LIBWEBP_VERSION=1.5.0
ARG LIBWEBP_URL="https://storage.googleapis.com/downloads.webmproject.org/releases/webp/libwebp-$LIBWEBP_VERSION-linux-x86-64.tar.gz"
ARG LIBWEBP_SHA256=f4bf49f85991f50e86a5404d16f15b72a053bb66768ed5cc0f6d042277cc2bb8

WORKDIR /app
RUN curl -L --fail --retry 3 --retry-delay 5 "$LIBWEBP_URL" -o libwebp.tar.gz && \
    echo "$LIBWEBP_SHA256 libwebp.tar.gz" | sha256sum -c - && \
    tar -xzf libwebp.tar.gz --one-top-level=libwebp --strip-components=1
COPY settings.gradle build.gradle gradlew ./
COPY gradle ./gradle
RUN --mount=type=cache,target=/home/gradle/.gradle/caches \
    ./gradlew dependencies --no-daemon
COPY . .
RUN ./gradlew runtime --no-daemon

FROM gcr.io/distroless/base-nossl:nonroot AS bot

COPY --from=mwader/static-ffmpeg:latest /ffmpeg /usr/local/bin/
ENV FFMPEG_PATH=/usr/local/bin/ffmpeg

COPY --from=builder /app/build/jre ./jre
COPY --from=builder /app/build/libs/Stickerify-shadow.jar .
COPY --from=builder /app/libwebp/bin/cwebp /usr/local/bin/
COPY --from=builder /app/libwebp/bin/dwebp /usr/local/bin/

CMD ["jre/bin/java", "-XX:+UseZGC", "-Dcom.sksamuel.scrimage.webp.binary.dir=/usr/local/bin/", "-jar", "Stickerify-shadow.jar"]
