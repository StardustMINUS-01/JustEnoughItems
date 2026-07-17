package mezz.jei.gui.favorites.preferences;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

public record RecipePreferenceRule(
	String name,
	RecipePreferenceTarget target,
	Optional<ResourceLocation> recipeType,
	List<List<RecipePreferenceTarget>> inputTiers,
	List<List<String>> recipeTiers
) {
	public RecipePreferenceRule {
		name = name == null || name.isBlank() ? "unnamed" : name;
		recipeType = recipeType == null ? Optional.empty() : recipeType;
		inputTiers = copyTiers(inputTiers);
		recipeTiers = copyTiers(recipeTiers);
	}

	private static <T> List<List<T>> copyTiers(List<List<T>> tiers) {
		return tiers == null ? List.of() : tiers.stream().map(List::copyOf).toList();
	}
}
