package mezz.jei.common.config.file;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class FileWatcher {
	private static final Logger LOGGER = LogManager.getLogger();

	private final @Nullable FileWatcherThread thread;
	private final Set<Runnable> runtimeCallbacks = new HashSet<>();

	public FileWatcher(String threadName) {
		this.thread = createThread(threadName);
	}

	@Nullable
	private static FileWatcherThread createThread(String threadName) {
		try {
			return new FileWatcherThread(threadName);
		} catch (UnsupportedOperationException | IOException e) {
			LOGGER.error("Unable to create file watcher: ", e);
			return null;
		}
	}

	/**
	 * @param path     a config file to watch
	 * @param callback a callbacks to call when the file changes.
	 *                 Callbacks must be thread-safe, they will be called from this thread.
	 */
	public void addCallback(Path path, Runnable callback) {
		if (thread != null) {
			thread.addCallback(path, callback);
		}
	}

	/**
	 * @param directory     a config directory to watch
	 * @param filenameFilter a filter for file names inside the directory
	 * @param callback      a callback to call when a matching file changes.
	 *                      Callbacks must be thread-safe, they will be called from a watcher callback thread.
	 */
	public void addDirectoryCallback(Path directory, Predicate<Path> filenameFilter, Runnable callback) {
		if (thread != null) {
			thread.addDirectoryCallback(directory, filenameFilter, callback);
		}
	}

	public void start() {
		if (thread != null) {
			thread.start();
		}
	}

	public synchronized void addRuntimeCallback(Path path, Runnable callback) {
		runtimeCallbacks.add(callback);
		addCallback(path, callback);
	}

	public synchronized void addRuntimeDirectoryCallback(Path directory, Predicate<Path> filenameFilter, Runnable callback) {
		runtimeCallbacks.add(callback);
		addDirectoryCallback(directory, filenameFilter, callback);
	}

	public synchronized void clearRuntimeCallbacks() {
		if (thread != null) {
			thread.removeCallbacks(runtimeCallbacks);
		}
		runtimeCallbacks.clear();
	}
}
