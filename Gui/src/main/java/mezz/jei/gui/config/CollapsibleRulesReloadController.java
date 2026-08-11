package mezz.jei.gui.config;

import mezz.jei.gui.collapsible.CollapsibleRules;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Coalesces collapsible rules file changes and reloads on the client thread.
 */
public final class CollapsibleRulesReloadController {
	private final Supplier<CollapsibleRules> rulesLoader;
	private final Consumer<Runnable> clientThreadExecutor;
	private final Consumer<CollapsibleRules> rulesConsumer;
	private final AtomicBoolean reloadQueued = new AtomicBoolean();

	public CollapsibleRulesReloadController(
		Supplier<CollapsibleRules> rulesLoader,
		Consumer<Runnable> clientThreadExecutor,
		Consumer<CollapsibleRules> rulesConsumer
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
