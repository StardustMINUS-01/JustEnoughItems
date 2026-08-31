package mezz.jei.gui.recipes;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@ApiStatus.Internal
public record FilteredRecipeSlotView(
	IRecipeSlotView delegate,
	List<ITypedIngredient<?>> candidates
) implements IRecipeSlotView {
	public FilteredRecipeSlotView {
		candidates = List.copyOf(candidates);
	}

	@Override
	public Stream<ITypedIngredient<?>> getAllIngredients() {
		return candidates.stream();
	}

	@Override
	public List<ITypedIngredient<?>> getAllIngredientsList() {
		return candidates;
	}

	@Override
	public Optional<ITypedIngredient<?>> getDisplayedIngredient() {
		return delegate.getDisplayedIngredient()
			.filter(candidates::contains)
			.or(() -> candidates.stream().findFirst());
	}

	@Override
	public RecipeIngredientRole getRole() {
		return delegate.getRole();
	}

	@Override
	public void drawHighlight(GuiGraphics guiGraphics, int color) {
		delegate.drawHighlight(guiGraphics, color);
	}

	@Override
	public Optional<String> getSlotName() {
		return delegate.getSlotName();
	}
}
