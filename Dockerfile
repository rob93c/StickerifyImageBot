FROM eclipse-temurin AS builder
WORKDIR /app
COPY gradlew ./
COPY gradle/wrapper ./gradle/wrapper
RUN --mount=type=cache,target=/home/gradle/.gradle/caches \
    ./gradlew --no-daemon
COPY settings.gradle build.gradle ./
COPY gradle/libs.versions.toml ./gradle/
RUN --mount=type=cache,target=/home/gradle/.gradle/caches \
    ./gradlew dependencies --no-daemon
COPY . .
RUN ./gradlew runtime --no-daemon

FROM gcr.io/distroless/base-nossl:nonroot AS bot
COPY --from=builder /app/build/jre ./jre
COPY --from=builder /app/build/libs/Stickerify-shadow.jar .
COPY --from=mwader/static-ffmpeg /ffmpeg /usr/local/bin/
ENV FFMPEG_PATH=/usr/local/bin/ffmpeg
CMD ["jre/bin/java", "--enable-preview", "-jar", "Stickerify-shadow.jar"]
