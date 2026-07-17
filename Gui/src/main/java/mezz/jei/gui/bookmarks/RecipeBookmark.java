package mezz.jei.gui.bookmarks;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.elements.RecipeBookmarkElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

public class RecipeBookmark<R, I> implements IBookmark {
	private final IElement<I> element;
	private final IRecipeCategory<R> recipeCategory;
	private final R recipe;
	private final ResourceLocation recipeUid;
	private final ITypedIngredient<I> recipeOutput;
	private final RecipeIngredientRole displayRole;
	@Nullable
	private final Object equalityScope;
	private boolean visible = true;

	@Nullable
	public static <T> RecipeBookmark<T, ?> create(
		IRecipeLayoutDrawable<T> recipeLayoutDrawable,
		IIngredientManager ingredientManager
	) {
		return create(recipeLayoutDrawable, ingredientManager, false);
	}

	@Nullable
	public static <T> RecipeBookmark<T, ?> create(
		IRecipeLayoutDrawable<T> recipeLayoutDrawable,
		IIngredientManager ingredientManager,
		boolean preserveAmount
	) {
		T recipe = recipeLayoutDrawable.getRecipe();
		IRecipeCategory<T> recipeCategory = recipeLayoutDrawable.getRecipeCategory();
		ResourceLocation recipeUid = recipeCategory.getRegistryName(recipe);
		if (recipeUid == null) {
			return null;
		}

		IRecipeSlotsView recipeSlotsView = recipeLayoutDrawable.getRecipeSlotsView();
		{
			ITypedIngredient<?> output = findFirst(recipeSlotsView, RecipeIngredientRole.OUTPUT);
			if (output != null) {
				if (!preserveAmount) {
					output = ingredientManager.normalizeTypedIngredient(output);
				}
				return new RecipeBookmark<>(recipeCategory, recipe, recipeUid, output, RecipeIngredientRole.OUTPUT);
			}
		}
		{
			ITypedIngredient<?> input = findFirst(recipeSlotsView, RecipeIngredientRole.INPUT);
			if (input != null) {
				if (!preserveAmount) {
					input = ingredientManager.normalizeTypedIngredient(input);
				}
				return new RecipeBookmark<>(recipeCategory, recipe, recipeUid, input, RecipeIngredientRole.INPUT);
			}
		}

		return null;
	}

	@Nullable
	private static ITypedIngredient<?> findFirst(IRecipeSlotsView slotsView, RecipeIngredientRole role) {
		for (IRecipeSlotView slotView : slotsView.getSlotViews()) {
			if (slotView.getRole() != role) {
				continue;
			}
			Optional<ITypedIngredient<?>> outputOptional = slotView.getAllIngredients().findFirst();
			if (outputOptional.isPresent()) {
				return outputOptional.get();
			}
		}
		return null;
	}

	public RecipeBookmark(
		IRecipeCategory<R> recipeCategory,
		R recipe,
		ResourceLocation recipeUid,
		ITypedIngredient<I> recipeOutput,
		RecipeIngredientRole displayRole
	) {
		this(recipeCategory, recipe, recipeUid, recipeOutput, displayRole, null);
	}

	public RecipeBookmark(
		IRecipeCategory<R> recipeCategory,
		R recipe,
		ResourceLocation recipeUid,
		ITypedIngredient<I> recipeOutput,
		RecipeIngredientRole displayRole,
		@Nullable Object equalityScope
	) {
		this.recipeCategory = recipeCategory;
		this.recipe = recipe;
		this.recipeUid = recipeUid;
		this.recipeOutput = recipeOutput;
		this.element = new RecipeBookmarkElement<>(this);
		this.displayRole = displayRole;
		this.equalityScope = equalityScope;
	}

	public RecipeBookmark(
		IRecipeCategory<R> recipeCategory,
		R recipe,
		ResourceLocation recipeUid,
		ITypedIngredient<I> recipeOutput,
		boolean displayIsOutput
	) {
		this(recipeCategory, recipe, recipeUid, recipeOutput, displayIsOutput ? RecipeIngredientRole.OUTPUT : RecipeIngredientRole.INPUT);
	}

	@Override
	public BookmarkType getType() {
		return BookmarkType.RECIPE;
	}

	public IRecipeCategory<R> getRecipeCategory() {
		return recipeCategory;
	}

	public ResourceLocation getRecipeUid() {
		return recipeUid;
	}

	public R getRecipe() {
		return recipe;
	}

	public ITypedIngredient<I> getRecipeOutput() {
		return recipeOutput;
	}

	@Override
	public IElement<?> getElement() {
		return element;
	}

	@Override
	public boolean isVisible() {
		return visible;
	}

	@Override
	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	public RecipeIngredientRole getDisplayRole() {
		return displayRole;
	}

	public BookmarkItemMetadata createDefaultMetadata(String groupId) {
		BookmarkItemType type = BookmarkItemType.fromRecipeRole(displayRole);
		return new BookmarkItemMetadata(
			groupId,
			type,
			1,
			getIngredientFactor(),
			BookmarkItemMetadata.CHANCE_FULL,
			recipeCategory.getRecipeType().getUid(),
			recipeUid,
			java.util.Set.of()
		);
	}

	private long getIngredientFactor() {
		if (recipeOutput.getIngredient() instanceof ItemStack stack) {
			return Math.max(1, stack.getCount());
		}
		return 1;
	}

	@Override
	public int hashCode() {
		return Objects.hash(equalityScope, recipeUid, displayRole, recipeOutput.getType(), getIngredientHash(recipeOutput.getIngredient()));
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof RecipeBookmark<?, ?> recipeBookmark) {
			return Objects.equals(recipeBookmark.equalityScope, equalityScope) &&
				recipeBookmark.recipeUid.equals(recipeUid) &&
				recipeBookmark.displayRole == displayRole &&
				recipeBookmark.recipeOutput.getType().equals(recipeOutput.getType()) &&
				ingredientsEqual(recipeBookmark.recipeOutput.getIngredient(), recipeOutput.getIngredient());
		}
		return false;
	}

	public RecipeBookmark<R, I> withEqualityScope(@Nullable Object equalityScope) {
		return new RecipeBookmark<>(recipeCategory, recipe, recipeUid, recipeOutput, displayRole, equalityScope);
	}

	private static boolean ingredientsEqual(Object first, Object second) {
		if (first instanceof ItemStack firstStack && second instanceof ItemStack secondStack) {
			return ItemStack.matches(firstStack, secondStack);
		}
		return Objects.equals(first, second);
	}

	private static int getIngredientHash(Object ingredient) {
		if (ingredient instanceof ItemStack stack) {
			return ItemStack.hashItemAndComponents(stack);
		}
		return Objects.hashCode(ingredient);
	}
}
