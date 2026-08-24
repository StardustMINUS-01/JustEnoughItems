package mezz.jei.test.neoforge;

import mezz.jei.common.gui.IngredientsTooltipComponent;
import mezz.jei.common.gui.CandidateTooltipComponent;
import mezz.jei.gui.overlay.bookmarks.FixedRecipePreviewTooltipComponent;
import mezz.jei.gui.overlay.bookmarks.PreviewTooltipComponent;
import mezz.jei.gui.overlay.bookmarks.RecipeChainPreviewTooltipComponent;
import mezz.jei.library.gui.ingredients.TagContentTooltipComponent;
import mezz.jei.neoforge.TooltipComponentFactories;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TooltipComponentFactoriesTest {
	@Test
	public void registersAllJeiTooltipComponents() {
		Set<Class<? extends TooltipComponent>> expected = Set.of(
			IngredientsTooltipComponent.class,
			CandidateTooltipComponent.class,
			FixedRecipePreviewTooltipComponent.class,
			PreviewTooltipComponent.class,
			RecipeChainPreviewTooltipComponent.class,
			TagContentTooltipComponent.class
		);

		assertEquals(expected, Set.copyOf(TooltipComponentFactories.getRegisteredTypes()));
	}
}
