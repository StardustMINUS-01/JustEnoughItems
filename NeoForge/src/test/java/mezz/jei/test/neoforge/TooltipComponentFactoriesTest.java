package mezz.jei.test.neoforge;

import mezz.jei.common.gui.IngredientsTooltipComponent;
import mezz.jei.common.gui.CandidateTooltipComponent;
import mezz.jei.gui.overlay.bookmarks.FixedRecipePreviewTooltipComponent;
import mezz.jei.gui.overlay.bookmarks.PreviewTooltipComponent;
import mezz.jei.gui.overlay.bookmarks.RecipeChainPreviewTooltipComponent;
import mezz.jei.library.gui.ingredients.TagContentTooltipComponent;
import mezz.jei.neoforge.TooltipComponentFactories;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TooltipComponentFactoriesTest {
	@Test
	public void registersAllJeiTooltipComponents() {
		Map<Class<? extends TooltipComponent>, Function<TooltipComponent, ClientTooltipComponent>> factories = new HashMap<>();
		TooltipComponentFactories.register(new RegisterClientTooltipComponentFactoriesEvent(factories));

		Set<Class<? extends TooltipComponent>> expected = Set.of(
			IngredientsTooltipComponent.class,
			CandidateTooltipComponent.class,
			FixedRecipePreviewTooltipComponent.class,
			PreviewTooltipComponent.class,
			RecipeChainPreviewTooltipComponent.class,
			TagContentTooltipComponent.class
		);

		assertEquals(expected, factories.keySet());
	}
}
