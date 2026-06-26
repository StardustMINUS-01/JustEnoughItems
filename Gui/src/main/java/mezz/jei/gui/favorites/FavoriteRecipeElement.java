package mezz.jei.gui.favorites;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IRecipesGui;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.BookmarkHotkeyTooltipUtil;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.input.BookmarkKeyInputs;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.IngredientGridTooltipHelper;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.util.FocusUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class FavoriteRecipeElement<T> implements IElement<T> {
	private final ITypedIngredient<T> ingredient;
	private final ITypedIngredient<T> tooltipIngredient;
	private final FocusedRecipe recipe;
	private final IRecipeManager recipeManager;
	private final IFocusFactory focusFactory;
	private final @Nullable FavoriteRecipeStore favoriteRecipes;
	private final boolean favoriteTarget;
	private final Optional<String> amountText;
	private final Optional<FavoriteRecipePanelState.RecipeInputKey> recipeInputKey;
	private final boolean visible;

	public FavoriteRecipeElement(
		ITypedIngredient<T> ingredient,
		FocusedRecipe recipe,
		IRecipeManager recipeManager,
		IFocusFactory focusFactory
	) {
		this(ingredient, recipe, recipeManager, focusFactory, null, true);
	}

	public FavoriteRecipeElement(
		ITypedIngredient<T> ingredient,
		FocusedRecipe recipe,
		IRecipeManager recipeManager,
		IFocusFactory focusFactory,
		FavoriteRecipeStore favoriteRecipes,
		boolean favoriteTarget
	) {
		this(ingredient, ingredient, Optional.empty(), recipe, recipeManager, focusFactory, favoriteRecipes, favoriteTarget);
	}

	public FavoriteRecipeElement(
		ITypedIngredient<T> ingredient,
		ITypedIngredient<T> tooltipIngredient,
		Optional<String> amountText,
		FocusedRecipe recipe,
		IRecipeManager recipeManager,
		IFocusFactory focusFactory,
		FavoriteRecipeStore favoriteRecipes,
		boolean favoriteTarget
	) {
		this(
			ingredient,
			tooltipIngredient,
			amountText,
			recipe,
			recipeManager,
			focusFactory,
			favoriteRecipes,
			favoriteTarget,
			Optional.empty()
		);
	}

	public FavoriteRecipeElement(
		ITypedIngredient<T> ingredient,
		ITypedIngredient<T> tooltipIngredient,
		Optional<String> amountText,
		FocusedRecipe recipe,
		IRecipeManager recipeManager,
		IFocusFactory focusFactory,
		FavoriteRecipeStore favoriteRecipes,
		boolean favoriteTarget,
		Optional<FavoriteRecipePanelState.RecipeInputKey> recipeInputKey
	) {
		this(
			ingredient,
			tooltipIngredient,
			amountText,
			recipe,
			recipeManager,
			focusFactory,
			favoriteRecipes,
			favoriteTarget,
			recipeInputKey,
			true
		);
	}

	public FavoriteRecipeElement(
		ITypedIngredient<T> ingredient,
		ITypedIngredient<T> tooltipIngredient,
		Optional<String> amountText,
		FocusedRecipe recipe,
		IRecipeManager recipeManager,
		IFocusFactory focusFactory,
		FavoriteRecipeStore favoriteRecipes,
		boolean favoriteTarget,
		Optional<FavoriteRecipePanelState.RecipeInputKey> recipeInputKey,
		boolean visible
	) {
		this.ingredient = ingredient;
		this.tooltipIngredient = tooltipIngredient;
		this.recipe = recipe;
		this.recipeManager = recipeManager;
		this.focusFactory = focusFactory;
		this.favoriteRecipes = favoriteRecipes;
		this.favoriteTarget = favoriteTarget;
		this.amountText = amountText == null ? Optional.empty() : amountText;
		this.recipeInputKey = recipeInputKey == null ? Optional.empty() : recipeInputKey;
		this.visible = visible;
	}

	@Override
	public ITypedIngredient<T> getTypedIngredient() {
		return ingredient;
	}

	@Override
	public Optional<IBookmark> getBookmark() {
		return Optional.empty();
	}

	@Override
	public @Nullable mezz.jei.api.gui.drawable.IDrawable createRenderOverlay() {
		return null;
	}

	@Override
	public void show(IRecipesGui recipesGui, FocusUtil focusUtil, List<RecipeIngredientRole> roles) {
		List<IFocus<?>> focuses = focusUtil.createFocuses(ingredient, List.of(RecipeIngredientRole.OUTPUT));
		showFocusedRecipe(recipesGui, focuses);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void showFocusedRecipe(IRecipesGui recipesGui, List<IFocus<?>> focuses) {
		Optional<RecipeType<?>> recipeType = recipeManager.getRecipeType(recipe.recipeTypeUid());
		if (recipeType.isEmpty()) {
			recipesGui.show(focuses);
			return;
		}
		IRecipeCategory recipeCategory = recipeManager.getRecipeCategory(recipeType.get());
		Optional<?> focused = recipeManager.createRecipeLookup(recipeCategory.getRecipeType())
			.limitFocus(focuses)
			.get()
			.filter(candidate -> Objects.equals(recipeCategory.getRegistryName(candidate), recipe.recipeUid()))
			.findFirst();
		if (focused.isPresent()) {
			recipesGui.showRecipes(recipeCategory, List.of(focused.get()), focuses);
		} else {
			recipesGui.show(focuses);
		}
	}

	@Override
	public void getTooltip(
		JeiTooltip tooltip,
		IngredientGridTooltipHelper tooltipHelper,
		IIngredientRenderer<T> ingredientRenderer,
		IIngredientHelper<T> ingredientHelper
	) {
		tooltip.add(Component.translatable("jei.tooltip.favoriteRecipes.recipe").withStyle(ChatFormatting.GREEN));
		tooltip.add(Component.translatable("jei.tooltip.favoriteRecipes.clickRecipe").withStyle(ChatFormatting.GRAY));
		tooltipHelper.getIngredientTooltip(tooltip, tooltipIngredient, ingredientRenderer, ingredientHelper, favoriteRecipes == null);
		if (favoriteRecipes != null) {
			BookmarkHotkeyTooltipUtil.addFavoriteRecipeHotkeys(tooltip, Internal.getKeyMappings());
		}
	}

	@Override
	public boolean isVisible() {
		return visible;
	}

	@Override
	public boolean reservesInvisibleSpace() {
		return true;
	}

	public FocusedRecipe getFocusedRecipe() {
		return recipe;
	}

	public String getRecipeRowGroupId() {
		return recipe.recipeTypeUid() + "|" + recipe.recipeUid();
	}

	public boolean isFavoriteTarget() {
		return favoriteTarget;
	}

	public Optional<String> getAmountText() {
		return amountText;
	}

	public Optional<FavoriteRecipePanelState.RecipeInputKey> getRecipeInputKey() {
		return recipeInputKey;
	}

	@Override
	public boolean handleClick(UserInput input, IInternalKeyMappings keyBindings) {
		if (favoriteRecipes == null || !BookmarkKeyInputs.isPlainBookmarkKey(input, keyBindings)) {
			return false;
		}
		if (!input.isSimulate()) {
			favoriteRecipes.removeFavorite(recipe);
		}
		return true;
	}

}
