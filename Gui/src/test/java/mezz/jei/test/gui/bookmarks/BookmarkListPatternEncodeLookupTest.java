package mezz.jei.test.gui.bookmarks;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeCategoriesLookup;
import mezz.jei.api.recipe.IRecipeLookup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.recipes.FocusedRecipeLayoutResolver;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public class BookmarkListPatternEncodeLookupTest {
	private static final IIngredientType<String> INGREDIENT_TYPE = () -> String.class;
	private static final RecipeType<String> RECIPE_TYPE = RecipeType.create("test", "machine", String.class);
	private static final TestRecipeCategory CATEGORY = new TestRecipeCategory();

	@Test
	public void uniqueOutputRecipeCreatesLayout() {
		BookmarkList bookmarks = bookmarkList(List.of("only_recipe"));

		Optional<IRecipeLayoutDrawable<?>> layout = bookmarks.createUniqueRecipeLayoutDrawable(typed("plate"));

		Assertions.assertTrue(layout.isPresent());
		Assertions.assertEquals("only_recipe", layout.get().getRecipe());
	}

	@Test
	public void multipleOutputRecipesAreAmbiguous() {
		BookmarkList bookmarks = bookmarkList(List.of("first_recipe", "second_recipe"));

		Optional<IRecipeLayoutDrawable<?>> layout = bookmarks.createUniqueRecipeLayoutDrawable(typed("plate"));

		Assertions.assertTrue(layout.isEmpty());
	}

	@Test
	public void missingOutputRecipeIsEmpty() {
		BookmarkList bookmarks = bookmarkList(List.of());

		Optional<IRecipeLayoutDrawable<?>> layout = bookmarks.createUniqueRecipeLayoutDrawable(typed("plate"));

		Assertions.assertTrue(layout.isEmpty());
	}

	@Test
	public void recipeBookmarkForElementUsesPreferredRecipeLookup() {
		FocusedRecipe preferred = focusedRecipe("second_recipe");
		BookmarkList bookmarks = bookmarkList(
			List.of("first_recipe", "second_recipe"),
			key -> key.equals(BookmarkIngredientKey.of(INGREDIENT_TYPE.getUid(), "fallback:plate")) ? Optional.of(preferred) : Optional.empty(),
			ingredientManager()
		);

		boolean added = bookmarks.addRecipeBookmarkForElement(element("plate"));

		Assertions.assertTrue(added);
		Assertions.assertEquals(1, bookmarks.getBookmarks().size());
		BookmarkItemMetadata metadata = bookmarks.getBookmarkMetadata(bookmarks.getBookmarks().getFirst());
		Assertions.assertEquals(ResourceLocation.fromNamespaceAndPath("test", "second_recipe"), metadata.recipeUid());
	}

	@Test
	public void focusedRecipeLayoutLookupMatchesOnlyTheRequestedRecipeUid() {
		FocusedRecipeLayoutResolver resolver = new FocusedRecipeLayoutResolver(recipeManager(List.of("first_recipe", "second_recipe")));

		Optional<IRecipeLayoutDrawable<?>> layout = resolver.resolve(focusedRecipe("second_recipe"), focusFactory().getEmptyFocusGroup());

		Assertions.assertEquals("second_recipe", layout.orElseThrow().getRecipe());
	}

	@Test
	public void focusedRecipeLayoutLookupReturnsEmptyForUnknownRecipeUid() {
		FocusedRecipeLayoutResolver resolver = new FocusedRecipeLayoutResolver(recipeManager(List.of("first_recipe")));

		Assertions.assertTrue(resolver.resolve(focusedRecipe("missing_recipe"), focusFactory().getEmptyFocusGroup()).isEmpty());
	}

	private static BookmarkList bookmarkList(List<String> recipes) {
		return bookmarkList(recipes, key -> Optional.empty(), null);
	}

	private static BookmarkList bookmarkList(
		List<String> recipes,
		Function<BookmarkIngredientKey, Optional<FocusedRecipe>> preferredRecipeLookup,
		@Nullable IIngredientManager ingredientManager
	) {
		return new BookmarkList(
			recipeManager(recipes),
			focusFactory(),
			ingredientManager,
			null,
			null,
			null,
			null,
			preferredRecipeLookup
		);
	}

	private static IRecipeManager recipeManager(List<String> recipes) {
		return (IRecipeManager) Proxy.newProxyInstance(
			BookmarkListPatternEncodeLookupTest.class.getClassLoader(),
			new Class<?>[]{IRecipeManager.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "createRecipeCategoryLookup" -> recipeCategoryLookup();
				case "createRecipeLookup" -> recipeLookup(recipes);
				case "getRecipeType" -> Optional.of(RECIPE_TYPE);
				case "getRecipeCategory" -> CATEGORY;
				case "createRecipeLayoutDrawable" -> Optional.of(new TestRecipeLayout((String) args[1]));
				default -> throw new UnsupportedOperationException(method.getName());
			}
		);
	}

	private static IRecipeCategoriesLookup recipeCategoryLookup() {
		return new IRecipeCategoriesLookup() {
			@Override
			public IRecipeCategoriesLookup limitTypes(Collection<RecipeType<?>> recipeTypes) {
				return this;
			}

			@Override
			public IRecipeCategoriesLookup limitFocus(Collection<? extends IFocus<?>> focuses) {
				return this;
			}

			@Override
			public IRecipeCategoriesLookup includeHidden() {
				return this;
			}

			@Override
			public Stream<IRecipeCategory<?>> get() {
				return Stream.of(CATEGORY);
			}
		};
	}

	private static IRecipeLookup<String> recipeLookup(List<String> recipes) {
		return new IRecipeLookup<>() {
			@Override
			public IRecipeLookup<String> limitFocus(Collection<? extends IFocus<?>> focuses) {
				return this;
			}

			@Override
			public IRecipeLookup<String> includeHidden() {
				return this;
			}

			@Override
			public Stream<String> get() {
				return recipes.stream();
			}
		};
	}

	private static IFocusFactory focusFactory() {
		return new IFocusFactory() {
			@Override
			public <V> IFocus<V> createFocus(RecipeIngredientRole role, IIngredientType<V> ingredientType, V ingredient) {
				return createFocus(role, new TestTypedIngredient<>(ingredientType, ingredient));
			}

			@Override
			public <V> IFocus<V> createFocus(RecipeIngredientRole role, ITypedIngredient<V> typedIngredient) {
				return new TestFocus<>(role, typedIngredient);
			}

			@Override
			public IFocusGroup createFocusGroup(Collection<? extends IFocus<?>> focuses) {
				return focusGroup(new ArrayList<>(focuses));
			}

			@Override
			public IFocusGroup getEmptyFocusGroup() {
				return focusGroup(List.of());
			}
		};
	}

	private static IFocusGroup focusGroup(List<IFocus<?>> focuses) {
		return new IFocusGroup() {
			@Override
			public boolean isEmpty() {
				return focuses.isEmpty();
			}

			@Override
			public List<IFocus<?>> getAllFocuses() {
				return focuses;
			}

			@Override
			public Stream<IFocus<?>> getFocuses(RecipeIngredientRole role) {
				return focuses.stream().filter(focus -> focus.getRole() == role);
			}

			@Override
			@SuppressWarnings("unchecked")
			public <T> Stream<IFocus<T>> getFocuses(IIngredientType<T> ingredientType) {
				return focuses.stream()
					.map(focus -> focus.checkedCast(ingredientType))
					.flatMap(Optional::stream)
					.map(focus -> (IFocus<T>) focus);
			}

			@Override
			public <T> Stream<IFocus<T>> getFocuses(IIngredientType<T> ingredientType, RecipeIngredientRole role) {
				return getFocuses(ingredientType)
					.filter(focus -> focus.getRole() == role);
			}
		};
	}

	private static ITypedIngredient<String> typed(String ingredient) {
		return new TestTypedIngredient<>(INGREDIENT_TYPE, ingredient);
	}

	private static FocusedRecipe focusedRecipe(String recipeUid) {
		return new FocusedRecipe(RECIPE_TYPE.getUid(), ResourceLocation.fromNamespaceAndPath("test", recipeUid));
	}

	private static IIngredientManager ingredientManager() {
		return (IIngredientManager) Proxy.newProxyInstance(
			BookmarkListPatternEncodeLookupTest.class.getClassLoader(),
			new Class<?>[]{IIngredientManager.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "normalizeTypedIngredient" -> args[0];
				default -> throw new UnsupportedOperationException(method.getName());
			}
		);
	}

	private static IElement<String> element(String ingredient) {
		return new IElement<>() {
			@Override
			public ITypedIngredient<String> getTypedIngredient() {
				return typed(ingredient);
			}

			@Override
			public Optional<mezz.jei.gui.bookmarks.IBookmark> getBookmark() {
				return Optional.empty();
			}

			@Override
			public @Nullable mezz.jei.api.gui.drawable.IDrawable createRenderOverlay() {
				return null;
			}

			@Override
			public void show(mezz.jei.api.runtime.IRecipesGui recipesGui, mezz.jei.gui.util.FocusUtil focusUtil, List<RecipeIngredientRole> roles) {
			}

			@Override
			public void getTooltip(
				mezz.jei.common.gui.JeiTooltip tooltip,
				mezz.jei.gui.overlay.IngredientGridTooltipHelper tooltipHelper,
				mezz.jei.api.ingredients.IIngredientRenderer<String> ingredientRenderer,
				mezz.jei.api.ingredients.IIngredientHelper<String> ingredientHelper
			) {
			}

			@Override
			public boolean isVisible() {
				return true;
			}
		};
	}

	private record TestTypedIngredient<T>(IIngredientType<T> type, T ingredient) implements ITypedIngredient<T> {
		@Override
		public IIngredientType<T> getType() {
			return type;
		}

		@Override
		public T getIngredient() {
			return ingredient;
		}
	}

	private record TestFocus<T>(RecipeIngredientRole role, ITypedIngredient<T> typedIngredient) implements IFocus<T> {
		@Override
		public ITypedIngredient<T> getTypedValue() {
			return typedIngredient;
		}

		@Override
		public RecipeIngredientRole getRole() {
			return role;
		}

		@Override
		public <V> Optional<IFocus<V>> checkedCast(IIngredientType<V> ingredientType) {
			if (typedIngredient.getType() == ingredientType) {
				@SuppressWarnings("unchecked")
				IFocus<V> result = (IFocus<V>) this;
				return Optional.of(result);
			}
			return Optional.empty();
		}
	}

	private static class TestRecipeCategory implements IRecipeCategory<String> {
		@Override
		public RecipeType<String> getRecipeType() {
			return RECIPE_TYPE;
		}

		@Override
		public Component getTitle() {
			return Component.literal("Test");
		}

		@Override
		public @Nullable mezz.jei.api.gui.drawable.IDrawable getIcon() {
			return null;
		}

		@Override
		public void setRecipe(mezz.jei.api.gui.builder.IRecipeLayoutBuilder builder, String recipe, IFocusGroup focuses) {
		}

		@Override
		public @Nullable ResourceLocation getRegistryName(String recipe) {
			return ResourceLocation.fromNamespaceAndPath("test", recipe);
		}
	}

	private record TestRecipeLayout(String recipe) implements IRecipeLayoutDrawable<String> {
		@Override
		public void setPosition(int posX, int posY) {
		}

		@Override
		public void drawRecipe(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY) {
		}

		@Override
		public void drawOverlays(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY) {
		}

		@Override
		public boolean isMouseOver(double mouseX, double mouseY) {
			return false;
		}

		@Override
		public <T> Optional<T> getIngredientUnderMouse(int mouseX, int mouseY, IIngredientType<T> ingredientType) {
			return Optional.empty();
		}

		@Override
		public Optional<IRecipeSlotDrawable> getRecipeSlotUnderMouse(double mouseX, double mouseY) {
			return Optional.empty();
		}

		@Override
		public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
			return Optional.empty();
		}

		@Override
		public Rect2i getRect() {
			return new Rect2i(0, 0, 1, 1);
		}

		@Override
		public Rect2i getRectWithBorder() {
			return getRect();
		}

		@Override
		public Rect2i getSideButtonArea(int buttonIndex) {
			return getRect();
		}

		@Override
		public IRecipeSlotsView getRecipeSlotsView() {
			return () -> List.of(new TestRecipeSlot(RecipeIngredientRole.OUTPUT, typed("plate")));
		}

		@Override
		public IRecipeCategory<String> getRecipeCategory() {
			return CATEGORY;
		}

		@Override
		public String getRecipe() {
			return recipe;
		}

		@Override
		public IJeiInputHandler getInputHandler() {
			return () -> ScreenRectangle.empty();
		}

		@Override
		public void tick() {
		}
	}

	private record TestRecipeSlot(RecipeIngredientRole role, ITypedIngredient<?> ingredient) implements IRecipeSlotView {
		@Override
		public Stream<ITypedIngredient<?>> getAllIngredients() {
			return Stream.of(ingredient);
		}

		@Override
		public List<@Nullable ITypedIngredient<?>> getAllIngredientsList() {
			return List.of(ingredient);
		}

		@Override
		public Optional<ITypedIngredient<?>> getDisplayedIngredient() {
			return Optional.of(ingredient);
		}

		@Override
		public RecipeIngredientRole getRole() {
			return role;
		}

		@Override
		public void drawHighlight(net.minecraft.client.gui.GuiGraphics guiGraphics, int color) {
		}

		@Override
		public Optional<String> getSlotName() {
			return Optional.empty();
		}
	}
}
