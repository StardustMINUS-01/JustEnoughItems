package mezz.jei.test.gui.recipes;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.drawable.IScalableDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeCatalystLookup;
import mezz.jei.api.recipe.IRecipeCategoriesLookup;
import mezz.jei.api.recipe.IRecipeLookup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.recipes.RecipeIdClipboardHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RecipeIdClipboardHandlerTest {
	private static final TestIngredientType INGREDIENT_TYPE = new TestIngredientType();
	private static final ITypedIngredient<String> INGOT = new TestTypedIngredient<>(INGREDIENT_TYPE, "ingot");

	@Test
	public void recipeLayoutCopiesCurrentRecipeId() {
		TestRecipeCategory category = new TestRecipeCategory("test:category", "current", "test:current_recipe");
		TestRecipeLayout layout = new TestRecipeLayout(category, "current");

		Optional<String> recipeId = RecipeIdClipboardHandler.getRecipeId(layout);

		assertEquals(Optional.of("test:current_recipe"), recipeId);
	}

	@Test
	public void ingredientCopiesAllOutputRecipeIds() {
		TestRecipeCategory first = new TestRecipeCategory("test:first", "plate", "test:plate");
		TestRecipeCategory second = new TestRecipeCategory("test:second", "gear", "test:gear");
		TestRecipeManager recipeManager = new TestRecipeManager(List.of(
			new TestRecipeSet(first, List.of("plate")),
			new TestRecipeSet(second, List.of("gear"))
		));

		List<String> recipeIds = RecipeIdClipboardHandler.getOutputRecipeIds(
			INGOT,
			recipeManager,
			new TestFocusFactory()
		);

		assertEquals(List.of("test:plate", "test:gear"), recipeIds);
		assertEquals(RecipeIngredientRole.OUTPUT, recipeManager.lastFocusRole);
	}

	@Test
	public void recipeLayoutTakesPriorityOverIngredientOutputRecipeIds() {
		TestRecipeCategory current = new TestRecipeCategory("test:current", "current", "test:current_recipe");
		TestRecipeCategory other = new TestRecipeCategory("test:other", "other", "test:other_recipe");
		TestRecipeManager recipeManager = new TestRecipeManager(List.of(
			new TestRecipeSet(current, List.of("current")),
			new TestRecipeSet(other, List.of("other"))
		));

		List<String> recipeIds = RecipeIdClipboardHandler.getRecipeIdsForCopy(
			new TestRecipeLayout(current, "current"),
			INGOT,
			recipeManager,
			new TestFocusFactory()
		);

		assertEquals(List.of("test:current_recipe"), recipeIds);
	}

	@Test
	public void unknownRecipeIdsAreSkipped() {
		TestRecipeCategory known = new TestRecipeCategory("test:known", "known", "test:known");
		TestRecipeCategory unknown = new TestRecipeCategory("test:unknown", "unknown", null);
		TestRecipeManager recipeManager = new TestRecipeManager(List.of(
			new TestRecipeSet(known, List.of("known")),
			new TestRecipeSet(unknown, List.of("unknown"))
		));

		List<String> recipeIds = RecipeIdClipboardHandler.getOutputRecipeIds(
			INGOT,
			recipeManager,
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

	private record TestRecipeSet(TestRecipeCategory category, List<String> recipes) {
	}

	private static final class TestRecipeManager implements IRecipeManager {
		private final List<TestRecipeSet> recipeSets;
		private RecipeIngredientRole lastFocusRole;

		private TestRecipeManager(List<TestRecipeSet> recipeSets) {
			this.recipeSets = recipeSets;
		}

		@Override
		public <R> IRecipeLookup<R> createRecipeLookup(RecipeType<R> recipeType) {
			TestRecipeSet recipeSet = recipeSets.stream()
				.filter(set -> set.category().getRecipeType().equals(recipeType))
				.findFirst()
				.orElseThrow();
			@SuppressWarnings("unchecked")
			List<R> recipes = (List<R>) recipeSet.recipes();
			return new TestRecipeLookup<>(recipes, this);
		}

		@Override
		public IRecipeCategoriesLookup createRecipeCategoryLookup() {
			List<IRecipeCategory<?>> categories = new ArrayList<>();
			for (TestRecipeSet recipeSet : recipeSets) {
				categories.add(recipeSet.category());
			}
			return new TestRecipeCategoriesLookup(categories, this);
		}

		@Override
		public <T> IRecipeCategory<T> getRecipeCategory(RecipeType<T> recipeType) {
			throw new UnsupportedOperationException();
		}

		@Override
		public IRecipeCatalystLookup createRecipeCatalystLookup(RecipeType<?> recipeType) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> void hideRecipes(RecipeType<T> recipeType, Collection<T> recipes) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> void unhideRecipes(RecipeType<T> recipeType, Collection<T> recipes) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> void addRecipes(RecipeType<T> recipeType, List<T> recipes) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void hideRecipeCategory(RecipeType<?> recipeType) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void unhideRecipeCategory(RecipeType<?> recipeType) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> IRecipeLayoutDrawable<T> createRecipeLayoutDrawableOrShowError(IRecipeCategory<T> recipeCategory, T recipe, IFocusGroup focusGroup) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> Optional<IRecipeLayoutDrawable<T>> createRecipeLayoutDrawable(IRecipeCategory<T> recipeCategory, T recipe, IFocusGroup focusGroup) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> Optional<IRecipeLayoutDrawable<T>> createRecipeLayoutDrawable(IRecipeCategory<T> recipeCategory, T recipe, IFocusGroup focusGroup, IScalableDrawable background, int borderSize) {
			throw new UnsupportedOperationException();
		}

		@Override
		public IRecipeSlotDrawable createRecipeSlotDrawable(RecipeIngredientRole role, List<Optional<ITypedIngredient<?>>> ingredients, Set<Integer> focusedIngredients, int ingredientCycleOffset) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> IIngredientSupplier getRecipeIngredients(IRecipeCategory<T> recipeCategory, T recipe) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> Optional<RecipeType<T>> getRecipeType(ResourceLocation recipeUid, Class<? extends T> recipeClass) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Optional<RecipeType<?>> getRecipeType(ResourceLocation recipeUid) {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<IRecipeButtonControllerFactory> getRecipeButtonControllerFactories() {
			return List.of();
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

	private static final class TestRecipeCategory implements IRecipeCategory<String> {
		private final RecipeType<String> recipeType;
		private final String recipe;
		private final @Nullable ResourceLocation registryName;

		private TestRecipeCategory(String recipeTypeUid, String recipe, @Nullable String registryName) {
			ResourceLocation uid = ResourceLocation.parse(recipeTypeUid);
			this.recipeType = RecipeType.create(uid.getNamespace(), uid.getPath(), String.class);
			this.recipe = recipe;
			this.registryName = registryName == null ? null : ResourceLocation.parse(registryName);
		}

		@Override
		public RecipeType<String> getRecipeType() {
			return recipeType;
		}

		@Override
		public Component getTitle() {
			return Component.literal(recipeType.getUid().toString());
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
			return this.recipe.equals(recipe) ? registryName : null;
		}
	}

	private record TestRecipeLayout(TestRecipeCategory category, String recipe) implements IRecipeLayoutDrawable<String> {
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
			return true;
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
		public Optional<mezz.jei.api.gui.inputs.RecipeSlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
			return Optional.empty();
		}

		@Override
		public net.minecraft.client.renderer.Rect2i getRect() {
			return new net.minecraft.client.renderer.Rect2i(0, 0, 1, 1);
		}

		@Override
		public net.minecraft.client.renderer.Rect2i getRectWithBorder() {
			return getRect();
		}

		@Override
		public net.minecraft.client.renderer.Rect2i getSideButtonArea(int buttonIndex) {
			return getRect();
		}

		@Override
		public mezz.jei.api.gui.ingredient.IRecipeSlotsView getRecipeSlotsView() {
			throw new UnsupportedOperationException();
		}

		@Override
		public IRecipeCategory<String> getRecipeCategory() {
			return category;
		}

		@Override
		public String getRecipe() {
			return recipe;
		}

		@Override
		public mezz.jei.api.gui.inputs.IJeiInputHandler getInputHandler() {
			return new mezz.jei.api.gui.inputs.IJeiInputHandler() {
				@Override
				public ScreenRectangle getArea() {
					return ScreenRectangle.empty();
				}
			};
		}

		@Override
		public void tick() {
		}
	}
}
