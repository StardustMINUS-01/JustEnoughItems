package mezz.jei.gui.input.handlers;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IRecipesGui;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.util.JeiClientSoundUtil;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.util.FocusUtil;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

public class WorldInputHandler {
	private final IBookmarkOverlay bookmarkOverlay;
	private final IRecipesGui recipesGui;
	private final FocusUtil focusUtil;
	private final IIngredientManager ingredientManager;

	public WorldInputHandler(
		IBookmarkOverlay bookmarkOverlay,
		IRecipesGui recipesGui,
		FocusUtil focusUtil,
		IIngredientManager ingredientManager
	) {
		this.bookmarkOverlay = bookmarkOverlay;
		this.recipesGui = recipesGui;
		this.focusUtil = focusUtil;
		this.ingredientManager = ingredientManager;
	}

	public boolean handleUserInput(UserInput input, IInternalKeyMappings keyMappings, Supplier<ItemStack> pickedStackSupplier) {
		Action action;
		if (input.is(keyMappings.getBookmarkWorldTarget())) {
			action = Action.BOOKMARK;
		} else if (input.is(keyMappings.getShowWorldTargetRecipe())) {
			action = Action.SHOW_RECIPE;
		} else if (input.is(keyMappings.getShowWorldTargetUses())) {
			action = Action.SHOW_USES;
		} else {
			return false;
		}
		ItemStack pickedStack = pickedStackSupplier.get();
		return ingredientManager.createTypedIngredient(VanillaTypes.ITEM_STACK, pickedStack, true)
			.map(ingredient -> handleAction(action, ingredient))
			.orElse(false);
	}

	private boolean handleAction(Action action, ITypedIngredient<ItemStack> ingredient) {
		switch (action) {
			case BOOKMARK -> {
				if (bookmarkOverlay.addBookmark(ingredient)) {
					JeiClientSoundUtil.playClickSound();
				}
			}
			case SHOW_RECIPE -> recipesGui.show(focusUtil.createFocuses(ingredient, List.of(RecipeIngredientRole.OUTPUT)));
			case SHOW_USES -> recipesGui.show(focusUtil.createFocuses(
				ingredient,
				List.of(RecipeIngredientRole.INPUT, RecipeIngredientRole.CATALYST)
			));
		}
		return true;
	}

	private enum Action {
		BOOKMARK,
		SHOW_RECIPE,
		SHOW_USES
	}
}
