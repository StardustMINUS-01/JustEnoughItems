package mezz.jei.gui.recipes.filtering;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public record RecipeSearchIngredient(
	String displayName,
	String resourceLocation,
	String modId,
	Set<String> tags
) {
	public RecipeSearchIngredient(
		String displayName,
		ResourceLocation resourceLocation,
		String modId,
		Set<String> tags
	) {
		this(displayName, resourceLocation.toString(), modId, tags);
	}

	public RecipeSearchIngredient {
		displayName = normalize(displayName);
		resourceLocation = normalize(resourceLocation);
		modId = normalize(modId);
		tags = tags.stream()
			.map(RecipeSearchIngredient::normalize)
			.collect(Collectors.toUnmodifiableSet());
	}

	public boolean matchesText(String value) {
		return matchesText(value, IRecipeSearchTextMatcher.DEFAULT);
	}

	boolean matchesText(String value, IRecipeSearchTextMatcher matcher) {
		return matcher.contains(displayName, value) ||
			matcher.contains(resourceLocation, value) ||
			matcher.contains(modId, value) ||
			tags.stream().anyMatch(tag -> matcher.contains(tag, value));
	}

	public boolean matchesTag(String value) {
		return matchesTag(value, IRecipeSearchTextMatcher.DEFAULT);
	}

	boolean matchesTag(String value, IRecipeSearchTextMatcher matcher) {
		return tags.stream().anyMatch(tag -> matcher.contains(tag, value));
	}

	public boolean matchesMod(String value) {
		return matchesMod(value, IRecipeSearchTextMatcher.DEFAULT);
	}

	boolean matchesMod(String value, IRecipeSearchTextMatcher matcher) {
		return matcher.contains(modId, value);
	}

	public boolean matchesResourceLocation(String value) {
		return matchesResourceLocation(value, IRecipeSearchTextMatcher.DEFAULT);
	}

	boolean matchesResourceLocation(String value, IRecipeSearchTextMatcher matcher) {
		return matcher.contains(resourceLocation, value);
	}

	private static String normalize(String value) {
		return value.toLowerCase(Locale.ROOT);
	}
}
