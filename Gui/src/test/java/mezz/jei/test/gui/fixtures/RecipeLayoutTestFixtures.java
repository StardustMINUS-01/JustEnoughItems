package mezz.jei.test.gui.fixtures;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public final class RecipeLayoutTestFixtures {
	private RecipeLayoutTestFixtures() {
	}

	public static TestRecipeLayout layout(
		RecipeType<?> recipeType,
		Object recipe,
		ResourceLocation recipeUid,
		List<? extends List<@Nullable ITypedIngredient<?>>> inputs,
		List<? extends List<@Nullable ITypedIngredient<?>>> outputs
	) {
		List<TestRecipeSlotView> inputSlots = inputs.stream()
			.map(ingredients -> slot(RecipeIngredientRole.INPUT, ingredients))
			.toList();
		List<TestRecipeSlotView> outputSlots = outputs.stream()
			.map(ingredients -> slot(RecipeIngredientRole.OUTPUT, ingredients))
			.toList();
		return new TestRecipeLayout(
			new TestRecipeCategory(recipeType, recipeUid),
			recipe,
			inputSlots,
			outputSlots
		);
	}

	public static TestRecipeLayout singleIngredientLayout(
		RecipeType<?> recipeType,
		Object recipe,
		ResourceLocation recipeUid,
		List<@Nullable ITypedIngredient<?>> inputs,
		List<@Nullable ITypedIngredient<?>> outputs
	) {
		List<TestRecipeSlotView> inputSlots = inputs.stream()
			.map(ingredient -> new TestRecipeSlotView(
				RecipeIngredientRole.INPUT,
				Collections.singletonList(ingredient),
				ingredient
			))
			.toList();
		List<TestRecipeSlotView> outputSlots = outputs.stream()
			.map(ingredient -> new TestRecipeSlotView(
				RecipeIngredientRole.OUTPUT,
				Collections.singletonList(ingredient),
				ingredient
			))
			.toList();
		return new TestRecipeLayout(
			new TestRecipeCategory(recipeType, recipeUid),
			recipe,
			inputSlots,
			outputSlots
		);
	}

	public static TestRecipeSlotView slot(
		RecipeIngredientRole role,
		List<@Nullable ITypedIngredient<?>> ingredients
	) {
		ITypedIngredient<?> displayed = ingredients.stream()
			.filter(Objects::nonNull)
			.findFirst()
			.orElse(null);
		return new TestRecipeSlotView(role, ingredients, displayed);
	}

	public static TestRecipeSlotView slot(
		RecipeIngredientRole role,
		List<@Nullable ITypedIngredient<?>> ingredients,
		@Nullable ITypedIngredient<?> displayed
	) {
		return new TestRecipeSlotView(role, ingredients, displayed);
	}

	public record TestRecipeCategory(
		RecipeType<?> recipeType,
		ResourceLocation recipeUid
	) implements IRecipeCategory<Object> {
		@Override
		@SuppressWarnings("unchecked")
		public RecipeType<Object> getRecipeType() {
			return (RecipeType<Object>) recipeType;
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
		public void setRecipe(mezz.jei.api.gui.builder.IRecipeLayoutBuilder builder, Object recipe, IFocusGroup focuses) {
		}

		@Override
		public @Nullable ResourceLocation getRegistryName(Object recipe) {
			return recipeUid;
		}
	}

	public record TestRecipeLayout(
		TestRecipeCategory category,
		Object recipe,
		List<TestRecipeSlotView> inputs,
		List<TestRecipeSlotView> outputs
	) implements IRecipeLayoutDrawable<Object> {
		public TestRecipeLayout withRecipeUid(ResourceLocation recipeUid) {
			return new TestRecipeLayout(
				new TestRecipeCategory(category.recipeType(), recipeUid),
				recipe,
				inputs,
				outputs
			);
		}

		@Override
		public void setPosition(int posX, int posY) {
		}

		@Override
		public void drawRecipe(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		}

		@Override
		public void drawOverlays(GuiGraphics guiGraphics, int mouseX, int mouseY) {
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
			return () -> Stream.concat(inputs.stream(), outputs.stream())
				.map(IRecipeSlotView.class::cast)
				.toList();
		}

		@Override
		public IRecipeCategory<Object> getRecipeCategory() {
			return category;
		}

		@Override
		public Object getRecipe() {
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

	public record TestRecipeSlotView(
		RecipeIngredientRole role,
		List<@Nullable ITypedIngredient<?>> ingredients,
		@Nullable ITypedIngredient<?> displayed
	) implements IRecipeSlotView {
		public TestRecipeSlotView(RecipeIngredientRole role, @Nullable ITypedIngredient<?> ingredient) {
			this(role, ingredient == null ? List.of() : List.of(ingredient), ingredient);
		}

		@Override
		public Stream<ITypedIngredient<?>> getAllIngredients() {
			return ingredients.stream().filter(Objects::nonNull);
		}

		@Override
		public List<@Nullable ITypedIngredient<?>> getAllIngredientsList() {
			return ingredients;
		}

		@Override
		public Optional<ITypedIngredient<?>> getDisplayedIngredient() {
			return Optional.ofNullable(displayed);
		}

		@Override
		public RecipeIngredientRole getRole() {
			return role;
		}

		@Override
		public void drawHighlight(GuiGraphics guiGraphics, int color) {
		}

		@Override
		public Optional<String> getSlotName() {
			return Optional.empty();
		}
	}
}
