package mezz.jei.gui.chat;

import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.gui.input.IPinnedTooltipHolder;
import mezz.jei.gui.input.PinnedTooltipManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import org.joml.Vector2ic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatRecipeTooltipTest {
	@Test
	void replacingChatOwnerLeavesTheReplacementOpenAndClearReleasesOwnership() {
		ChatRecipeTooltip tooltip = new ChatRecipeTooltip();
		IPinnedTooltipHolder replacement = () -> { throw new AssertionError("The replacement must stay open"); };
		PinnedTooltipManager.opened(tooltip);
		try {
			assertTrue(PinnedTooltipManager.shouldSuppressExternalTooltip());
			PinnedTooltipManager.draw(tooltip, () -> assertFalse(PinnedTooltipManager.shouldSuppressExternalTooltip()));
			PinnedTooltipManager.opened(replacement);
			assertSame(replacement, PinnedTooltipManager.getActiveHolder());
			tooltip.hide();
			assertSame(replacement, PinnedTooltipManager.getActiveHolder());
		} finally {
			PinnedTooltipManager.closed(replacement);
			tooltip.clear();
		}
		PinnedTooltipManager.opened(tooltip);
		tooltip.clear();
		assertNull(PinnedTooltipManager.getActiveHolder());
		assertFalse(PinnedTooltipManager.shouldSuppressExternalTooltip());
	}

	private static final IIngredientRenderer<String> RENDERER = new IIngredientRenderer<>() {
		@Override
		public void render(GuiGraphics graphics, String ingredient) {
			throw new AssertionError("Positioning must not render the ingredient");
		}

		@Override
		@SuppressWarnings("removal")
		public List<Component> getTooltip(String ingredient, TooltipFlag tooltipFlag) {
			return List.of();
		}
	};

	@ParameterizedTest
	@CsvSource({
		"320, 240, 40, 20, 16, 8",
		"60, 40, 40, 20, 12, 8",
		"40, 20, 40, 20, 8, 8"
	})
	void ingredientTooltipKeepsBorderPaddingWhenClamped(int screenWidth, int screenHeight, int width, int height, int expectedX, int expectedY) {
		ChatRecipeTooltip tooltip = new ChatRecipeTooltip();
		ClientTooltipPositioner positioner = tooltip.createIngredientPositioner(RENDERER);
		Vector2ic position = positioner.positionTooltip(screenWidth, screenHeight, 0, 0, width, height);
		assertEquals(expectedX, position.x());
		assertEquals(expectedY, position.y());
	}

	@Test
	void ingredientTooltipRepositionsWhenItsContentShrinks() {
		ChatRecipeTooltip tooltip = new ChatRecipeTooltip();
		ClientTooltipPositioner positioner = tooltip.createIngredientPositioner(RENDERER);
		assertEquals(8, positioner.positionTooltip(60, 40, 0, 0, 100, 20).x());
		assertEquals(16, positioner.positionTooltip(60, 40, 0, 0, 20, 10).x());
	}
}
