package mezz.jei.gui.config;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Coalesces config rules file changes and reloads on the client thread.
 */
public final class ConfigRulesReloadController<T> {
	private final Supplier<T> rulesLoader;
	private final Consumer<Runnable> clientThreadExecutor;
	private final Consumer<T> rulesConsumer;
	private final AtomicBoolean reloadQueued = new AtomicBoolean();

	public ConfigRulesReloadController(
		Supplier<T> rulesLoader,
		Consumer<Runnable> clientThreadExecutor,
		Consumer<T> rulesConsumer
	) {
		this.rulesLoader = Objects.requireNonNull(rulesLoader);
		this.clientThreadExecutor = Objects.requireNonNull(clientThreadExecutor);
		this.rulesConsumer = Objects.requireNonNull(rulesConsumer);
	}

	public void onConfigFileChanged() {
		if (reloadQueued.compareAndSet(false, true)) {
			clientThreadExecutor.accept(this::reloadOnClientThread);
		}
	}

	private void reloadOnClientThread() {
		reloadQueued.set(false);
		rulesConsumer.accept(rulesLoader.get());
	}
}
