package mezz.jei.test.library.gui.ingredients;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.common.gui.IRecipeSlotCandidateView;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.library.gui.ingredients.ICycler;
import mezz.jei.library.gui.ingredients.RecipeSlot;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class RecipeSlotCandidateViewTest {
	private static final IIngredientType<String> TYPE = () -> String.class;

	@Test
	public void filteredCandidateViewControlsRotationWithoutChangingAllIngredients() {
		List<ITypedIngredient<?>> all = List.of(typed("oak"), typed("supreme_crimson"), typed("supreme_blazing"));
		RecipeSlot slot = new RecipeSlot(
			RecipeIngredientRole.INPUT,
			new ImmutableRect2i(0, 0, 16, 16),
			new ICycler() {
				@Override
				public <T> java.util.Optional<T> getCycled(List<@Nullable T> list) {
					return list.stream().findFirst();
				}
			},
			new java.util.ArrayList<>(),
			all,
			all,
			null,
			null,
			null,
			null
		);

		((IRecipeSlotCandidateView) slot).setDisplayedCandidates(all.subList(1, 3));

		Assertions.assertEquals("supreme_crimson", slot.getDisplayedIngredient().orElseThrow().getIngredient());
		Assertions.assertEquals(3, slot.getAllIngredients().count());
	}

	private static ITypedIngredient<String> typed(String value) {
		return new ITypedIngredient<>() {
			@Override
			public IIngredientType<String> getType() {
				return TYPE;
			}

			@Override
			public String getIngredient() {
				return value;
			}
		};
	}
}
