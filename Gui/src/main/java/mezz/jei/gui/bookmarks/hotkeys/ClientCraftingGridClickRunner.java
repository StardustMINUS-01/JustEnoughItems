package mezz.jei.gui.bookmarks.hotkeys;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Cross-tick runner for the single-step client click fallback.
 */
public final class ClientCraftingGridClickRunner implements BookmarkAutoCraftingActivator.ClientFallbackStarter {
	private @Nullable ClientCraftingGridClickTask activeTask;
	private @Nullable Boolean lastResult;

	@Override
	public boolean start(AbstractContainerMenu menu, List<ItemStack> targetStacks) {
		return ClientCraftingGridClickEnvironment.create(menu)
			.map(environment -> start(new ClientCraftingGridClickTask(environment, targetStacks)))
			.orElse(false);
	}

	public boolean start(ClientCraftingGridClickTask task) {
		this.activeTask = task;
		this.lastResult = null;
		return true;
	}

	public void tick() {
		if (activeTask != null && !activeTask.tick()) {
			lastResult = activeTask.isComplete();
			activeTask = null;
		}
	}

	public void stop() {
		activeTask = null;
		lastResult = null;
	}

	public boolean hasActiveTask() {
		return activeTask != null;
	}

	public Optional<Boolean> consumeLastResult() {
		Boolean result = lastResult;
		lastResult = null;
		return Optional.ofNullable(result);
	}
}
