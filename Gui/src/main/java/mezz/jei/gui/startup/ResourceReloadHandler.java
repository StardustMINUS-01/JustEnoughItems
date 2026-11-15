package mezz.jei.gui.startup;

import mezz.jei.common.util.LoggedTimer;
import mezz.jei.gui.ingredients.IngredientFilter;
import mezz.jei.gui.overlay.IngredientListOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public class ResourceReloadHandler implements ResourceManagerReloadListener {
	private final IngredientListOverlay ingredientListOverlay;
	private final IngredientFilter ingredientFilter;
	private final Runnable beforeReload;

	public ResourceReloadHandler(IngredientListOverlay ingredientListOverlay, IngredientFilter ingredientFilter) {
		this(ingredientListOverlay, ingredientFilter, () -> {});
	}

	public ResourceReloadHandler(
		IngredientListOverlay ingredientListOverlay,
		IngredientFilter ingredientFilter,
		Runnable beforeReload
	) {
		this.ingredientListOverlay = ingredientListOverlay;
		this.ingredientFilter = ingredientFilter;
		this.beforeReload = beforeReload;
	}

	@Override
	public void onResourceManagerReload(ResourceManager resourceManager) {
		beforeReload.run();
		LoggedTimer timer = new LoggedTimer();
		timer.start("Rebuilding ingredient filter");
		ingredientFilter.rebuildItemFilter();
		timer.stop();

		Minecraft minecraft = Minecraft.getInstance();
		ingredientListOverlay.getScreenPropertiesUpdater()
			.updateScreen(minecraft.screen)
			.forceUpdate();
	}
}
