package mezz.jei.test.gui.bookmarks;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
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
import mezz.jei.gui.overlay.elements.IngredientElement;
import mezz.jei.common.ingredients.TypedIngredient;
import mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures;
import mezz.jei.gui.recipes.FocusedRecipeLayoutResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static mezz.jei.test.gui.fixtures.RecipeLayoutTestFixtures.layout;

public class BookmarkLookupTest {
	private static final IIngredientType<String> INGREDIENT_TYPE = () -> String.class;
	private static final RecipeType<String> RECIPE_TYPE = RecipeType.create("test", "machine", String.class);
	private static final TestRecipeCategory CATEGORY = new TestRecipeCategory();

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 2})
	public void requiresUniqueRecipe(int count) {
		var recipes = List.of("first_recipe", "second_recipe").subList(0, count);
		var bookmarks = createBookmarks(recipes);
		var layout = bookmarks.createUniqueRecipeLayoutDrawable(typed("plate"));

		Assertions.assertEquals(count == 1 ? Optional.of("first_recipe") : Optional.empty(),
			layout.map(IRecipeLayoutDrawable::getRecipe));
	}

	@Test
	public void usesPreferredRecipe() {
		FocusedRecipe preferred = focusedRecipe("second_recipe");
		BookmarkList bookmarks = createBookmarks(
			List.of("first_recipe", "second_recipe"),
			key -> key.equals(BookmarkIngredientKey.of(INGREDIENT_TYPE.getUid(), "fallback:plate")) ? Optional.of(preferred) : Optional.empty(),
			ItemStackIngredientTestFixtures.ingredientManager()
		);

		boolean added = bookmarks.addRecipeBookmarkForElement(new IngredientElement<>(typed("plate")));

		Assertions.assertTrue(added);
		Assertions.assertEquals(1, bookmarks.getBookmarks().size());
		BookmarkItemMetadata metadata = bookmarks.getBookmarkMetadata(bookmarks.getBookmarks().getFirst());
		Assertions.assertEquals(ResourceLocation.fromNamespaceAndPath("test", "second_recipe"), metadata.recipeUid());
	}

	@ParameterizedTest
	@ValueSource(strings = {"second_recipe", "missing_recipe"})
	public void resolvesRecipeId(String recipe) {
		var resolver = new FocusedRecipeLayoutResolver(createManager(List.of("first_recipe", "second_recipe")));
		var layout = resolver.resolve(focusedRecipe(recipe), createFocusFactory().getEmptyFocusGroup());

		Assertions.assertEquals(recipe.equals("second_recipe") ? Optional.of(recipe) : Optional.empty(),
			layout.map(IRecipeLayoutDrawable::getRecipe));
	}

	private static BookmarkList createBookmarks(List<String> recipes) {
		return createBookmarks(recipes, key -> Optional.empty(), null);
	}

	private static BookmarkList createBookmarks(
		List<String> recipes,
		Function<BookmarkIngredientKey, Optional<FocusedRecipe>> preferences,
		@Nullable IIngredientManager ingredients
	) {
		return new BookmarkList(
			createManager(recipes),
			createFocusFactory(),
			ingredients,
			null,
			null,
			null,
			null,
			preferences
		);
	}

	private static IRecipeManager createManager(List<String> recipes) {
		return (IRecipeManager) Proxy.newProxyInstance(
			BookmarkLookupTest.class.getClassLoader(),
			new Class<?>[]{IRecipeManager.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "createRecipeCategoryLookup" -> recipeCategoryLookup();
				case "createRecipeLookup" -> recipeLookup(recipes);
				case "getRecipeType" -> Optional.of(RECIPE_TYPE);
				case "getRecipeCategory" -> CATEGORY;
				case "createRecipeLayoutDrawable" -> Optional.of(layout(
					RECIPE_TYPE,
					args[1],
					ResourceLocation.fromNamespaceAndPath("test", (String) args[1]),
					List.of(),
					List.of(List.of(typed("plate")))
				));
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

	private static IFocusFactory createFocusFactory() {
		return new IFocusFactory() {
			@Override
			public <V> IFocus<V> createFocus(RecipeIngredientRole role, IIngredientType<V> ingredientType, V ingredient) {
				return createFocus(role, TypedIngredient.createUnvalidated(ingredientType, ingredient));
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
		return TypedIngredient.createUnvalidated(INGREDIENT_TYPE, ingredient);
	}

	private static FocusedRecipe focusedRecipe(String recipeUid) {
		return new FocusedRecipe(RECIPE_TYPE.getUid(), ResourceLocation.fromNamespaceAndPath("test", recipeUid));
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

}
