import com.github.stickerifier.stickerify.bot.Stickerify;

void main() throws InterruptedException {
	try (var bot = new Stickerify()) {
		bot.start();
		Thread.currentThread().join();
	}
}
