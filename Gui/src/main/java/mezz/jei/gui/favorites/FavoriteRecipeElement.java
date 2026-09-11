package mezz.jei.gui.favorites;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
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
import mezz.jei.common.config.BookmarkTooltipFeature;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.gui.BookmarkHotkeyTooltipUtil;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.input.keys.IJeiKeyMappingInternal;
import mezz.jei.gui.bookmarks.BookmarkCandidateTooltipHelper;
import mezz.jei.gui.bookmarks.BookmarkCandidateTooltipState;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.input.BookmarkKeyInputs;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.ingredients.IngredientGridTooltipHelper;
import mezz.jei.gui.recipes.FocusedRecipeLayoutResolver;
import mezz.jei.gui.recipes.IIngredientCandidateSource;
import mezz.jei.gui.recipes.InputSlotSelectionState;
import mezz.jei.gui.recipes.RecipesGui;
import mezz.jei.gui.util.FocusUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
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
	private final Optional<FavoriteRecipeStore.FavoriteSlotInput> favoriteSlotInput;
	private final Map<Integer, FavoriteRecipeStore.FavoriteSlotInput> entryInputs;
	private final boolean visible;
	private final FocusedRecipeLayoutResolver recipeLayoutResolver;
	private final FavoriteRecipePreviewState recipePreviewState;
	private final BookmarkCandidateTooltipState permutationTooltipState;

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
		Optional<FavoriteRecipeStore.FavoriteSlotInput> favoriteSlotInput,
		Map<Integer, FavoriteRecipeStore.FavoriteSlotInput> entryInputs,
		boolean visible,
		BookmarkCandidateTooltipState permutationTooltipState
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
		this.favoriteSlotInput = favoriteSlotInput == null ? Optional.empty() : favoriteSlotInput;
		this.entryInputs = entryInputs == null ? Map.of() : Map.copyOf(entryInputs);
		this.visible = visible;
		this.recipeLayoutResolver = new FocusedRecipeLayoutResolver(recipeManager);
		this.recipePreviewState = new FavoriteRecipePreviewState(this::createRecipeLayoutDrawable);
		this.permutationTooltipState = permutationTooltipState;
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
			if (recipesGui instanceof RecipesGui recipesGuiImpl) {
				recipesGuiImpl.showRecipesWithFavoriteInputs(
					recipeCategory,
					List.of(focused.get()),
					focuses,
					entryInputs
				);
			} else {
				recipesGui.showRecipes(recipeCategory, List.of(focused.get()), focuses);
			}
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
		addPermutationTooltip(tooltip);
		addRecipePreviewTooltip(tooltip);
		if (favoriteRecipes != null) {
			BookmarkHotkeyTooltipUtil.addFavoriteRecipeHotkeys(tooltip, Internal.getKeyMappings());
		}
	}

	private void addPermutationTooltip(JeiTooltip tooltip) {
		if (favoriteSlotInput.isEmpty()) {
			return;
		}
		FavoriteRecipeStore.FavoriteSlotInput slotInput = favoriteSlotInput.get();
		BookmarkCandidateTooltipHelper.addTo(tooltip, permutationTooltipState, slotInput.permutations(), () -> new IIngredientCandidateSource() {
			private FavoriteRecipeStore.FavoriteSlotInput current = slotInput;
			private final boolean editable = favoriteRecipes != null && favoriteRecipes.getManualEntry(recipe).isPresent();
			private final Screen screen = Minecraft.getInstance().screen;

			@Override
			public Optional<ITypedIngredient<?>> getSelectedIngredient() {
				return Optional.<ITypedIngredient<?>>ofNullable(current.selected().typedIngredient()).or(() -> Optional.of(tooltipIngredient));
			}
			@Override
			public boolean isValid() {
				return Minecraft.getInstance().screen == screen && (!editable || favoriteRecipes.getManualEntry(recipe).map(entry -> entry.inputs().containsValue(current)).orElse(false));
			}
			@Override
			public boolean canSelect() {
				return editable;
			}
			@Override
			public boolean select(ITypedIngredient<?> ingredient, boolean synchronize) {
				var key = BookmarkItemMetadataFactory.createPermutationKey(ingredient, Internal.getJeiRuntime().getIngredientManager());
				if (!isValid() || !favoriteRecipes.selectFavoriteInputs(recipe, current, key, synchronize)) {
					return false;
				}
				current = new FavoriteRecipeStore.FavoriteSlotInput(key, current.permutations());
				return true;
			}
		});
	}

	private void addRecipePreviewTooltip(JeiTooltip tooltip) {
		IClientConfig clientConfig = Internal.getJeiClientConfigs().getClientConfig();
		if (!clientConfig.bookmarkTooltipFeatures().getValue().contains(BookmarkTooltipFeature.PREVIEW)) {
			return;
		}
		if (clientConfig.holdShiftToShowBookmarkTooltipFeaturesEnabled().getValue()) {
			IJeiKeyMappingInternal showBookmarkTooltipFeatures = Internal.getKeyMappings().getShowBookmarkTooltipFeatures();
			if (!showBookmarkTooltipFeatures.isDown()) {
				tooltip.addKeyUsageComponent("jei.tooltip.bookmarks.tooltips.usage", showBookmarkTooltipFeatures);
				return;
			}
		}
		recipePreviewState.addTo(tooltip);
	}

	private Optional<IRecipeLayoutDrawable<?>> createRecipeLayoutDrawable() {
		return recipeLayoutResolver.resolve(recipe, focusFactory.getEmptyFocusGroup())
			.map(recipeLayout -> {
				InputSlotSelectionState selectionState = new InputSlotSelectionState(Internal.getJeiRuntime().getIngredientManager());
				FavoriteRecipeInputs.apply(recipeLayout, entryInputs, selectionState);
				return recipeLayout;
			});
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

	public boolean isFavoriteTarget() {
		return favoriteTarget;
	}

	public Optional<String> getAmountText() {
		return amountText;
	}

	public Optional<FavoriteRecipePanelState.RecipeInputKey> getRecipeInputKey() {
		return recipeInputKey;
	}

	public Optional<FavoriteRecipeStore.FavoriteSlotInput> getFavoriteSlotInput() {
		return favoriteSlotInput;
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

	@Override
	public void tick() {
		recipePreviewState.tick();
	}


}
