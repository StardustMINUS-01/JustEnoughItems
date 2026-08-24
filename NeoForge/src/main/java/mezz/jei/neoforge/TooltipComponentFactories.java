package mezz.jei.neoforge;

import mezz.jei.common.gui.CandidateTooltipComponent;
import mezz.jei.common.gui.IngredientsTooltipComponent;
import mezz.jei.gui.overlay.bookmarks.FixedRecipePreviewTooltipComponent;
import mezz.jei.gui.overlay.bookmarks.PreviewTooltipComponent;
import mezz.jei.gui.overlay.bookmarks.RecipeChainPreviewTooltipComponent;
import mezz.jei.library.gui.ingredients.TagContentTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;

import java.util.List;
import java.util.function.Function;

public final class TooltipComponentFactories {
	private TooltipComponentFactories() {
	}

	public static List<Class<? extends TooltipComponent>> getRegisteredTypes() {
		return List.of(
			CandidateTooltipComponent.class,
			FixedRecipePreviewTooltipComponent.class,
			IngredientsTooltipComponent.class,
			PreviewTooltipComponent.class,
			RecipeChainPreviewTooltipComponent.class,
			TagContentTooltipComponent.class
		);
	}

	public static void register(RegisterClientTooltipComponentFactoriesEvent event) {
		event.register(CandidateTooltipComponent.class, Function.identity());
		event.register(FixedRecipePreviewTooltipComponent.class, Function.identity());
		event.register(IngredientsTooltipComponent.class, Function.identity());
		event.register(PreviewTooltipComponent.class, Function.identity());
		event.register(RecipeChainPreviewTooltipComponent.class, Function.identity());
		event.register(TagContentTooltipComponent.class, Function.identity());
	}
}
