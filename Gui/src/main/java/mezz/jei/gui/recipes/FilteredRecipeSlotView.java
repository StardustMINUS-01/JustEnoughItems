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
	public Stream<ITypedIngredient<?>> getDisplayedIngredients() {
		return candidates.stream();
	}

	@Override
	public Optional<net.minecraft.tags.TagKey<?>> getTagKey() {
		return candidates.stream().findFirst().flatMap(this::getTagKey);
	}

	private <T> Optional<net.minecraft.tags.TagKey<?>> getTagKey(ITypedIngredient<T> first) {
		List<T> values = candidates.stream()
			.map(candidate -> candidate.getIngredient(first.getType()))
			.flatMap(Optional::stream)
			.toList();
		if (values.size() != candidates.size()) {
			return Optional.empty();
		}
		return mezz.jei.common.Internal.getJeiRuntime().getIngredientManager()
			.getIngredientHelper(first.getType()).getTagKeyEquivalent(values);
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
