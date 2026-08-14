package mezz.jei.gui.input.handlers;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.file.serializers.TypedIngredientSerializer;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import mezz.jei.gui.input.CombinedRecipeFocusSource;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import mezz.jei.gui.recipes.IRecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public class BookmarkInputHandler implements IUserInputHandler {
	private final CombinedRecipeFocusSource focusSource;
	private final BookmarkList bookmarkList;
	private final BookmarkOverlay bookmarkOverlay;
	private final IClientConfig clientConfig;
	private final RecipesGui recipesGui;
	private final IIngredientManager ingredientManager;
	private final Function<BookmarkIngredientKey, Optional<FocusedRecipe>> favoriteRecipeLookup;
	private final Function<FocusedRecipe, Optional<String>> favoriteTreeSaver;

	public BookmarkInputHandler(
		CombinedRecipeFocusSource focusSource,
		BookmarkList bookmarkList,
		BookmarkOverlay bookmarkOverlay,
		IClientConfig clientConfig,
		RecipesGui recipesGui,
		IIngredientManager ingredientManager,
		Function<BookmarkIngredientKey, Optional<FocusedRecipe>> favoriteRecipeLookup,
		Function<FocusedRecipe, Optional<String>> favoriteTreeSaver
	) {
		this.focusSource = focusSource;
		this.bookmarkList = bookmarkList;
		this.bookmarkOverlay = bookmarkOverlay;
		this.clientConfig = clientConfig;
		this.recipesGui = recipesGui;
		this.ingredientManager = ingredientManager;
		this.favoriteRecipeLookup = favoriteRecipeLookup;
		this.favoriteTreeSaver = favoriteTreeSaver;
	}

	@Override
	public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
		if (input.is(keyBindings.getBookmark())) {
			Optional<IUserInputHandler> recipeHandler = handleRecipeBookmark(input);
			if (recipeHandler.isPresent()) {
				return recipeHandler;
			}
			return handleIngredientBookmark(input, keyBindings);
		}
		if (input.is(keyBindings.getFavoriteRecipe())) {
			Optional<IUserInputHandler> favoriteTreeHandler = handleFavoriteTree(input, keyBindings);
			if (favoriteTreeHandler.isPresent()) {
				return favoriteTreeHandler;
			}
		}
		return Optional.empty();
	}

	/**
	 * 1.20.1 port of the 1.21.1 favorite-tree hotkey (F key). The 1.21.1
	 * implementation resolves the hovered ingredient ({@code FavoriteRecipeElement})
	 * from the favorite-recipes panel, which does not exist in 1.20.1. Instead we
	 * resolve the ingredient under the mouse (which works in any JEI GUI, including
	 * the recipe GUI, ingredient list and container screens) via the
	 * {@link mezz.jei.gui.favorites.GeneratedFavoriteResolver}, then fall back to
	 * the recipe layout under the mouse in the recipe GUI.
	 */
	private Optional<IUserInputHandler> handleFavoriteTree(UserInput input, IInternalKeyMappings keyBindings) {
		Optional<IUserInputHandler> ingredientHandler = focusSource.getIngredientUnderMouse(input, keyBindings)
			.findFirst()
			.flatMap(clicked -> {
				Optional<FocusedRecipe> recipe = resolveFavoriteTreeRecipe(clicked);
				if (recipe.isEmpty()) {
					return Optional.empty();
				}
				if (!input.isSimulate()) {
					if (favoriteTreeSaver.apply(recipe.get()).isEmpty()) {
						return Optional.empty();
					}
					bookmarkOverlay.showBookmarkPanel();
				}
				IUserInputHandler handler = new SameElementInputHandler(this, clicked::isMouseOver);
				return Optional.of(handler);
			});
		if (ingredientHandler.isPresent()) {
			return ingredientHandler;
		}

		double mouseX = input.getMouseX();
		double mouseY = input.getMouseY();
		Optional<IRecipeLayoutWithButtons<?>> layoutWithButtons = recipesGui.getRecipeLayoutUnderMouse(mouseX, mouseY);
		if (layoutWithButtons.isEmpty()) {
			return Optional.empty();
		}

		IRecipeLayoutWithButtons<?> recipeLayoutWithButtons = layoutWithButtons.get();
		IRecipeLayoutDrawable<?> layout = recipeLayoutWithButtons.getRecipeLayout();
		FocusedRecipe focusedRecipe = createFocusedRecipe(layout);
		if (focusedRecipe == null) {
			return Optional.empty();
		}

		if (!input.isSimulate()) {
			if (favoriteTreeSaver.apply(focusedRecipe).isEmpty()) {
				return Optional.empty();
			}
			bookmarkOverlay.showBookmarkPanel();
		}
		return Optional.of(new SameElementInputHandler(this, layout::isMouseOver));
	}

	/**
	 * Resolves a hovered ingredient to a recipe via the generated-favorite resolver,
	 * mirroring the 1.21.1 {@code resolveFavoriteIngredientTreeRecipe} flow.
	 */
	private Optional<FocusedRecipe> resolveFavoriteTreeRecipe(IClickableIngredientInternal<?> clicked) {
		ITypedIngredient<?> typedIngredient = clicked.getTypedIngredient();
		BookmarkIngredientKey key = createKey(typedIngredient);
		return favoriteRecipeLookup.apply(key);
	}

	private BookmarkIngredientKey createKey(ITypedIngredient<?> ingredient) {
		try {
			String typeUid = ingredient.getType().getUid();
			@SuppressWarnings("unchecked")
			IIngredientHelper<Object> ingredientHelper = (IIngredientHelper<Object>) ingredientManager.getIngredientHelper(ingredient.getType());
			@SuppressWarnings("unchecked")
			ITypedIngredient<Object> typedIngredient = (ITypedIngredient<Object>) ingredient;
			String ingredientUid = Objects.toString(ingredientHelper.getUid(typedIngredient, UidContext.Ingredient));
			try {
				// Store the serialized ingredient so that the GeneratedFavoriteResolver
				// can deserialize it back and look up a recipe that outputs it.
				TypedIngredientSerializer ingredientSerializer = new TypedIngredientSerializer(ingredientManager);
				String serializedIngredient = ingredientSerializer.serialize(ingredient);
				return new BookmarkIngredientKey(typeUid, ingredientUid, serializedIngredient);
			} catch (RuntimeException e) {
				return BookmarkIngredientKey.of(typeUid, ingredientUid);
			}
		} catch (RuntimeException e) {
			return BookmarkIngredientKey.fallback("fallback:" + Objects.toString(ingredient.getIngredient()));
		}
	}

	private static <R> @Nullable FocusedRecipe createFocusedRecipe(IRecipeLayoutDrawable<R> layout) {
		ResourceLocation recipeUid = layout.getRecipeCategory().getRegistryName(layout.getRecipe());
		if (recipeUid == null) {
			return null;
		}
		return new FocusedRecipe(layout.getRecipeCategory().getRecipeType().getUid(), recipeUid);
	}

	private Optional<IUserInputHandler> handleRecipeBookmark(UserInput input) {
		double mouseX = input.getMouseX();
		double mouseY = input.getMouseY();
		Optional<IRecipeLayoutWithButtons<?>> layoutWithButtons = recipesGui.getRecipeLayoutUnderMouse(mouseX, mouseY);
		if (layoutWithButtons.isEmpty()) {
			return Optional.empty();
		}

		IRecipeLayoutWithButtons<?> recipeLayoutWithButtons = layoutWithButtons.get();
		RecipeBookmark<?, ?> recipeBookmark = recipeLayoutWithButtons.getRecipeBookmark();
		if (recipeBookmark == null) {
			return Optional.empty();
		}

		IRecipeLayoutDrawable<?> layout = recipeLayoutWithButtons.getRecipeLayout();
		Optional<RecipeSlotUnderMouse> slotUnderMouse = layout.getSlotUnderMouse(mouseX, mouseY);
		if (!shouldBookmarkRecipe(slotUnderMouse, clientConfig.isBookmarkOutputAsRecipeEnabled())) {
			return Optional.empty();
		}

		if (!input.isSimulate()) {
			bookmarkList.toggleBookmark(recipeBookmark);
		}
		return Optional.of(new SameElementInputHandler(this, layout::isMouseOver));
	}

	static boolean shouldBookmarkRecipe(Optional<RecipeSlotUnderMouse> slotUnderMouse, boolean bookmarkOutputAsRecipeEnabled) {
		return slotUnderMouse
			.map(slot -> shouldBookmarkRecipe(slot.slot().getRole(), bookmarkOutputAsRecipeEnabled))
			.orElse(true);
	}

	static boolean shouldBookmarkRecipe(RecipeIngredientRole role, boolean bookmarkOutputAsRecipeEnabled) {
		return role == RecipeIngredientRole.OUTPUT && bookmarkOutputAsRecipeEnabled;
	}

	private Optional<IUserInputHandler> handleIngredientBookmark(UserInput input, IInternalKeyMappings keyBindings) {
		return focusSource.getIngredientUnderMouse(input, keyBindings)
			.findFirst()
			.flatMap(clicked -> {
				if (input.isSimulate() ||
					bookmarkList.onElementBookmarked(clicked.getElement(), input, bookmarkOverlay)
				) {
					IUserInputHandler handler = new SameElementInputHandler(this, clicked::isMouseOver);
					return Optional.of(handler);
				}
				return Optional.empty();
			});
	}
}
