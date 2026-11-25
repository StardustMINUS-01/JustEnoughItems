package mezz.jei.gui.compat.ae2;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.List;

public interface Ae2RecipeChainPatternEncodingBridge {
	Ae2RecipeChainPatternEncodingBridge UNAVAILABLE = new Ae2RecipeChainPatternEncodingBridge() {
		@Override
		public boolean isAvailable() {
			return false;
		}

		@Override
		public boolean isPatternEncodingTerminal(AbstractContainerMenu menu) {
			return false;
		}

		@Override
		public SendResult sendRequests(AbstractContainerMenu menu, List<JeiPatternEncodeRequest> requests) {
			return SendResult.UNAVAILABLE;
		}
	};

	enum SendResult { SENT, UNAVAILABLE, INVALID_RECIPES }

	boolean isAvailable();

	boolean isPatternEncodingTerminal(AbstractContainerMenu menu);

	default boolean transferSelectedCraftingRecipe(AbstractContainerMenu menu, Object recipe, RecipeType<?> recipeType, IRecipeSlotsView slotsView) {
		return false;
	}

	SendResult sendRequests(AbstractContainerMenu menu, List<JeiPatternEncodeRequest> requests);
}
