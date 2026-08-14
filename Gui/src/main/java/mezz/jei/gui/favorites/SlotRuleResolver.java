package mezz.jei.gui.favorites;

import mezz.jei.gui.input.FocusedRecipe;

import java.util.List;
import java.util.Optional;

@FunctionalInterface
public interface SlotRuleResolver {
	Optional<FocusedRecipe> resolveSlot(List<SlotVariant> variants);

	default Optional<FocusedRecipe> resolveSlot(
		List<SlotVariant> variants,
		RecipeLayoutBuildCache layoutCache
	) {
		return resolveSlot(variants);
	}
}
