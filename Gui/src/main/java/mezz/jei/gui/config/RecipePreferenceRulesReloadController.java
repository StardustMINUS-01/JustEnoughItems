package mezz.jei.gui.config;

import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class RecipePreferenceRulesReloadController {
	private final Supplier<RecipePreferenceRules> rulesLoader;
	private final Consumer<Runnable> clientThreadExecutor;
	private final Consumer<RecipePreferenceRules> rulesConsumer;
	private final AtomicBoolean reloadQueued = new AtomicBoolean();

	public RecipePreferenceRulesReloadController(
		Supplier<RecipePreferenceRules> rulesLoader,
		Consumer<Runnable> clientThreadExecutor,
		Consumer<RecipePreferenceRules> rulesConsumer
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
