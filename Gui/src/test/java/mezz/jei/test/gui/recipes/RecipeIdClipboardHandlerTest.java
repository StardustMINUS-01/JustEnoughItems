package mezz.jei.test.gui.recipes;

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
import mezz.jei.gui.recipes.RecipeIdClipboardHandler;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.ArrayList;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RecipeIdClipboardHandlerTest {
	private static final TestIngredientType INGREDIENT_TYPE = new TestIngredientType();
	private static final ITypedIngredient<String> INGOT = new TestTypedIngredient<>(INGREDIENT_TYPE, "ingot");

	@Test
	public void recipeLayoutCopiesCurrentRecipeId() {
		IRecipeCategory<String> category = category("test:category", "current", "test:current_recipe");
		IRecipeLayoutDrawable<String> layout = layout(category, "current");

		Optional<String> recipeId = RecipeIdClipboardHandler.getRecipeId(layout);

		assertEquals(Optional.of("test:current_recipe"), recipeId);
	}

	@Test
	public void ingredientCopiesAllOutputRecipeIds() {
		IRecipeCategory<String> first = category("test:first", "plate", "test:plate");
		IRecipeCategory<String> second = category("test:second", "gear", "test:gear");
		TestRecipeManager recipeManager = new TestRecipeManager(List.of(
			new TestRecipeSet(first, List.of("plate")),
			new TestRecipeSet(second, List.of("gear"))
		));

		List<String> recipeIds = RecipeIdClipboardHandler.getOutputRecipeIds(
			INGOT,
			recipeManager.value(),
			new TestFocusFactory()
		);

		assertEquals(List.of("test:plate", "test:gear"), recipeIds);
		assertEquals(RecipeIngredientRole.OUTPUT, recipeManager.lastFocusRole);
	}

	@Test
	public void recipeLayoutTakesPriorityOverIngredientOutputRecipeIds() {
		IRecipeCategory<String> current = category("test:current", "current", "test:current_recipe");
		IRecipeCategory<String> other = category("test:other", "other", "test:other_recipe");
		TestRecipeManager recipeManager = new TestRecipeManager(List.of(
			new TestRecipeSet(current, List.of("current")),
			new TestRecipeSet(other, List.of("other"))
		));

		List<String> recipeIds = RecipeIdClipboardHandler.getRecipeIdsForCopy(
			layout(current, "current"),
			INGOT,
			recipeManager.value(),
			new TestFocusFactory()
		);

		assertEquals(List.of("test:current_recipe"), recipeIds);
	}

	@Test
	public void unknownRecipeIdsAreSkipped() {
		IRecipeCategory<String> known = category("test:known", "known", "test:known");
		IRecipeCategory<String> unknown = category("test:unknown", "unknown", null);
		TestRecipeManager recipeManager = new TestRecipeManager(List.of(
			new TestRecipeSet(known, List.of("known")),
			new TestRecipeSet(unknown, List.of("unknown"))
		));

		List<String> recipeIds = RecipeIdClipboardHandler.getOutputRecipeIds(
			INGOT,
			recipeManager.value(),
			new TestFocusFactory()
		);

		assertEquals(List.of("test:known"), recipeIds);
	}

	@Test
	public void clipboardTextUsesNewLines() {
		assertEquals("test:first\ntest:second", RecipeIdClipboardHandler.toClipboardText(List.of("test:first", "test:second")));
		assertTrue(RecipeIdClipboardHandler.toClipboardText(List.of()).isEmpty());
	}

	private record TestTypedIngredient<T>(IIngredientType<T> type, T ingredient) implements ITypedIngredient<T> {
		@Override
		public ITypedIngredient<T> normalize(mezz.jei.api.ingredients.IIngredientHelper<T> helper) {
			return mezz.jei.common.ingredients.TypedIngredient.createUnvalidated(getType(), helper.normalizeIngredient(getIngredient()));
		}

		@Override
		public IIngredientType<T> getType() {
			return type;
		}

		@Override
		public T getIngredient() {
			return ingredient;
		}
	}

	private record TestIngredientType() implements IIngredientType<String> {
		@Override
		public Class<? extends String> getIngredientClass() {
			return String.class;
		}
	}

	private record TestFocus<V>(RecipeIngredientRole role, ITypedIngredient<V> typedValue) implements IFocus<V> {
		@Override
		public ITypedIngredient<V> getTypedValue() {
			return typedValue;
		}

		@Override
		public RecipeIngredientRole getRole() {
			return role;
		}

		@Override
		public <T> Optional<IFocus<T>> checkedCast(IIngredientType<T> ingredientType) {
			ITypedIngredient<T> cast = typedValue.cast(ingredientType);
			return cast == null ? Optional.empty() : Optional.of(new TestFocus<>(role, cast));
		}
	}

	private static final class TestFocusFactory implements IFocusFactory {
		@Override
		public <V> IFocus<V> createFocus(RecipeIngredientRole role, IIngredientType<V> ingredientType, V ingredient) {
			return new TestFocus<>(role, new TestTypedIngredient<>(ingredientType, ingredient));
		}

		@Override
		public <V> IFocus<V> createFocus(RecipeIngredientRole role, ITypedIngredient<V> typedIngredient) {
			return new TestFocus<>(role, typedIngredient);
		}

		@Override
		public IFocusGroup createFocusGroup(Collection<? extends IFocus<?>> focuses) {
			throw new UnsupportedOperationException();
		}

		@Override
		public IFocusGroup getEmptyFocusGroup() {
			throw new UnsupportedOperationException();
		}
	}

	private record TestRecipeSet(IRecipeCategory<String> category, List<String> recipes) {
	}

	private static final class TestRecipeManager {
		private final List<TestRecipeSet> recipeSets;
		private final IRecipeManager value;
		private RecipeIngredientRole lastFocusRole;

		private TestRecipeManager(List<TestRecipeSet> recipeSets) {
			this.recipeSets = recipeSets;
			this.value = (IRecipeManager) Proxy.newProxyInstance(
				RecipeIdClipboardHandlerTest.class.getClassLoader(),
				new Class<?>[]{IRecipeManager.class},
				(proxy, method, args) -> switch (method.getName()) {
					case "createRecipeLookup" -> createRecipeLookup((RecipeType<?>) args[0]);
					case "createRecipeCategoryLookup" -> createRecipeCategoryLookup();
					case "getRecipeButtonControllerFactories" -> List.of();
					default -> throw new UnsupportedOperationException(method.getName());
				}
			);
		}

		private IRecipeManager value() {
			return value;
		}

		private <R> IRecipeLookup<R> createRecipeLookup(RecipeType<R> recipeType) {
			TestRecipeSet recipeSet = recipeSets.stream()
				.filter(set -> set.category().getRecipeType().equals(recipeType))
				.findFirst()
				.orElseThrow();
			@SuppressWarnings("unchecked")
			List<R> recipes = (List<R>) recipeSet.recipes();
			return new TestRecipeLookup<>(recipes, this);
		}

		private IRecipeCategoriesLookup createRecipeCategoryLookup() {
			List<IRecipeCategory<?>> categories = new ArrayList<>();
			for (TestRecipeSet recipeSet : recipeSets) {
				categories.add(recipeSet.category());
			}
			return new TestRecipeCategoriesLookup(categories, this);
		}
	}

	private static final class TestRecipeCategoriesLookup implements IRecipeCategoriesLookup {
		private final List<IRecipeCategory<?>> categories;
		private final TestRecipeManager recipeManager;

		private TestRecipeCategoriesLookup(List<IRecipeCategory<?>> categories, TestRecipeManager recipeManager) {
			this.categories = categories;
			this.recipeManager = recipeManager;
		}

		@Override
		public IRecipeCategoriesLookup limitTypes(Collection<RecipeType<?>> recipeTypes) {
			return this;
		}

		@Override
		public IRecipeCategoriesLookup limitFocus(Collection<? extends IFocus<?>> focuses) {
			recipeManager.lastFocusRole = focuses.stream()
				.findFirst()
				.map(IFocus::getRole)
				.orElse(null);
			return this;
		}

		@Override
		public IRecipeCategoriesLookup includeHidden() {
			return this;
		}

		@Override
		public Stream<IRecipeCategory<?>> get() {
			return categories.stream();
		}
	}

	private static final class TestRecipeLookup<R> implements IRecipeLookup<R> {
		private final List<R> recipes;
		private final TestRecipeManager recipeManager;

		private TestRecipeLookup(List<R> recipes, TestRecipeManager recipeManager) {
			this.recipes = recipes;
			this.recipeManager = recipeManager;
		}

		@Override
		public IRecipeLookup<R> limitFocus(Collection<? extends IFocus<?>> focuses) {
			recipeManager.lastFocusRole = focuses.stream()
				.findFirst()
				.map(IFocus::getRole)
				.orElse(null);
			return this;
		}

		@Override
		public IRecipeLookup<R> includeHidden() {
			return this;
		}

		@Override
		public Stream<R> get() {
			return recipes.stream();
		}
	}

	private static IRecipeCategory<String> category(String recipeTypeUid, String recipe, @Nullable String registryName) {
			ResourceLocation uid = ResourceLocation.parse(recipeTypeUid);
		RecipeType<String> recipeType = RecipeType.create(uid.getNamespace(), uid.getPath(), String.class);
		ResourceLocation recipeUid = registryName == null ? null : ResourceLocation.parse(registryName);
		@SuppressWarnings("unchecked")
		IRecipeCategory<String> category = (IRecipeCategory<String>) Proxy.newProxyInstance(
			RecipeIdClipboardHandlerTest.class.getClassLoader(),
			new Class<?>[]{IRecipeCategory.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "getRecipeType" -> recipeType;
				case "getRegistryName" -> recipe.equals(args[0]) ? recipeUid : null;
				default -> throw new UnsupportedOperationException(method.getName());
			}
		);
		return category;
	}

	private static IRecipeLayoutDrawable<String> layout(IRecipeCategory<String> category, String recipe) {
		@SuppressWarnings("unchecked")
		IRecipeLayoutDrawable<String> layout = (IRecipeLayoutDrawable<String>) Proxy.newProxyInstance(
			RecipeIdClipboardHandlerTest.class.getClassLoader(),
			new Class<?>[]{IRecipeLayoutDrawable.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "getRecipeCategory" -> category;
				case "getRecipe" -> recipe;
				default -> throw new UnsupportedOperationException(method.getName());
			}
		);
		return layout;
	}
}
