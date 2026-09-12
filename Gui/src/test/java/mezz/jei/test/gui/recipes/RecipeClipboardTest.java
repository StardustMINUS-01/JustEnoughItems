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
import mezz.jei.common.ingredients.TypedIngredient;
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
import static org.junit.jupiter.api.Assertions.assertSame;

public class RecipeClipboardTest {
	private static final IIngredientType<String> TYPE = () -> String.class;
	private static final ITypedIngredient<String> INGOT = TypedIngredient.createUnvalidated(TYPE, "ingot");

	@Test
	public void readsLayoutId() {
		IRecipeCategory<String> category = category("test:category", "current", "test:current_recipe");
		IRecipeLayoutDrawable<String> layout = layout(category, "current");

		Optional<String> recipeId = RecipeIdClipboardHandler.getRecipeId(layout);

		assertEquals(Optional.of("test:current_recipe"), recipeId);
	}

	@Test
	public void collectsOutputIds() {
		IRecipeCategory<String> first = category("test:first", "plate", "test:plate");
		IRecipeCategory<String> second = category("test:second", "gear", "test:gear");
		TestRecipeManager manager = new TestRecipeManager(List.of(
			new TestRecipeSet(first, List.of("plate")),
			new TestRecipeSet(second, List.of("gear")),
			new TestRecipeSet(category("test:unknown", "unknown", null), List.of("unknown"))
		));

		List<String> ids = RecipeIdClipboardHandler.getRecipeIdsForCopy(
			null,
			INGOT,
			manager.value(),
			new TestFocusFactory()
		);

		assertEquals(List.of("test:plate", "test:gear"), ids);
		assertTrue(RecipeIdClipboardHandler.toClipboardText(List.of()).isEmpty());
		assertEquals("test:plate\ntest:gear", RecipeIdClipboardHandler.toClipboardText(ids));
	}

	@Test
	public void prefersLayout() {
		IRecipeCategory<String> current = category("test:current", "current", "test:current_recipe");

		List<String> ids = RecipeIdClipboardHandler.getRecipeIdsForCopy(
			layout(current, "current"),
			INGOT,
			unusedManager(),
			new TestFocusFactory()
		);

		assertEquals(List.of("test:current_recipe"), ids);
		assertTrue(RecipeIdClipboardHandler.getRecipeIdsForCopy(
			layout(category("test:unknown", "unknown", null), "unknown"),
			INGOT, unusedManager(), new TestFocusFactory())
			.isEmpty());
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
			return new TestFocus<>(role, TypedIngredient.createUnvalidated(ingredientType, ingredient));
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
		private final List<TestRecipeSet> sets;
		private final IRecipeManager value;

		private TestRecipeManager(List<TestRecipeSet> sets) {
			this.sets = sets;
			this.value = (IRecipeManager) Proxy.newProxyInstance(
				RecipeClipboardTest.class.getClassLoader(),
				new Class<?>[]{IRecipeManager.class},
				(proxy, method, args) -> switch (method.getName()) {
					case "createRecipeLookup" -> createRecipeLookup((RecipeType<?>) args[0]);
					case "createRecipeCategoryLookup" -> createRecipeCategoryLookup();
					default -> throw new UnsupportedOperationException(method.getName());
				}
			);
		}

		private IRecipeManager value() {
			return value;
		}

		private <R> IRecipeLookup<R> createRecipeLookup(RecipeType<R> recipeType) {
			TestRecipeSet recipeSet = sets.stream()
				.filter(set -> set.category().getRecipeType().equals(recipeType))
				.findFirst()
				.orElseThrow();
			@SuppressWarnings("unchecked")
			List<R> recipes = (List<R>) recipeSet.recipes();
			return new TestRecipeLookup<>(recipes, false);
		}

		private IRecipeCategoriesLookup createRecipeCategoryLookup() {
			List<IRecipeCategory<?>> categories = new ArrayList<>();
			for (TestRecipeSet recipeSet : sets) {
				categories.add(recipeSet.category());
			}
			return new TestRecipeCategoriesLookup(categories, false);
		}
	}

	private record TestRecipeCategoriesLookup(List<IRecipeCategory<?>> categories, boolean focused) implements IRecipeCategoriesLookup {

		@Override
		public IRecipeCategoriesLookup limitTypes(Collection<RecipeType<?>> recipeTypes) {
			return this;
		}

		@Override
		public IRecipeCategoriesLookup limitFocus(Collection<? extends IFocus<?>> focuses) {
			assertEquals(1, focuses.size());
			IFocus<?> focus = focuses.iterator().next();
			assertEquals(RecipeIngredientRole.OUTPUT, focus.getRole());
			assertSame(INGOT, focus.getTypedValue());
			return new TestRecipeCategoriesLookup(categories, true);
		}

		@Override
		public IRecipeCategoriesLookup includeHidden() {
			return this;
		}

		@Override
		public Stream<IRecipeCategory<?>> get() {
			assertTrue(focused);
			return categories.stream();
		}
	}

	private record TestRecipeLookup<R>(List<R> recipes, boolean focused) implements IRecipeLookup<R> {

		@Override
		public IRecipeLookup<R> limitFocus(Collection<? extends IFocus<?>> focuses) {
			assertEquals(1, focuses.size());
			IFocus<?> focus = focuses.iterator().next();
			assertEquals(RecipeIngredientRole.OUTPUT, focus.getRole());
			assertSame(INGOT, focus.getTypedValue());
			return new TestRecipeLookup<>(recipes, true);
		}

		@Override
		public IRecipeLookup<R> includeHidden() {
			return this;
		}

		@Override
		public Stream<R> get() {
			assertTrue(focused);
			return recipes.stream();
		}
	}

	private static IRecipeManager unusedManager() {
		return (IRecipeManager) Proxy.newProxyInstance(RecipeClipboardTest.class.getClassLoader(),
			new Class<?>[]{IRecipeManager.class}, (proxy, method, args) -> {
				throw new AssertionError("Layout copying must not query recipes");
			});
	}

	private static IRecipeCategory<String> category(String recipeTypeUid, String recipe, @Nullable String registryName) {
		ResourceLocation uid = ResourceLocation.parse(recipeTypeUid);
		RecipeType<String> recipeType = RecipeType.create(uid.getNamespace(), uid.getPath(), String.class);
		ResourceLocation recipeUid = registryName == null ? null : ResourceLocation.parse(registryName);
		@SuppressWarnings("unchecked")
		IRecipeCategory<String> category = (IRecipeCategory<String>) Proxy.newProxyInstance(
			RecipeClipboardTest.class.getClassLoader(),
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
			RecipeClipboardTest.class.getClassLoader(),
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
