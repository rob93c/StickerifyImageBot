FROM eclipse-temurin AS builder
# To be removed
COPY libs/imgscalr /root/.m2/repository/org/imgscalr/imgscalr-lib/4.3-SNAPSHOT
WORKDIR /app
COPY settings.gradle build.gradle gradlew ./
COPY gradle ./gradle
RUN --mount=type=cache,target=/home/gradle/.gradle/caches \
    ./gradlew dependencies --no-daemon
COPY . .
RUN ./gradlew shadowJar jlink --no-daemon

FROM gcr.io/distroless/base-nossl AS bot
COPY --from=builder /app/build/image ./jre
COPY --from=builder /app/build/libs/Stickerify-shadow.jar .
COPY --from=mwader/static-ffmpeg /ffmpeg /usr/local/bin/
ENV FFMPEG_PATH=/usr/local/bin/ffmpeg
CMD ["jre/bin/java", "-jar", "Stickerify-shadow.jar"]
