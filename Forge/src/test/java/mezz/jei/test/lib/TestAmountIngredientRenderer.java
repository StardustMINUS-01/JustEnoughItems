package mezz.jei.test.lib;

import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class TestAmountIngredientRenderer implements IIngredientRenderer<TestAmountIngredient> {
	@Override
	public void render(GuiGraphics guiGraphics, TestAmountIngredient ingredient) {

	}

	@Override
	public List<Component> getTooltip(TestAmountIngredient ingredient, TooltipFlag tooltipFlag) {
		return List.of(
			Component.literal("Test Amount Ingredient Tooltip " + ingredient),
			Component.literal("Test amount ingredient tooltip " + ingredient + " line 2")
		);
	}
}
