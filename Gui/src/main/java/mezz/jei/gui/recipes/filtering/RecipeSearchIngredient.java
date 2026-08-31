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
		return displayName.contains(value) ||
			resourceLocation.contains(value) ||
			modId.contains(value) ||
			tags.stream().anyMatch(tag -> tag.contains(value));
	}

	public boolean matchesTag(String value) {
		return tags.stream().anyMatch(tag -> tag.contains(value));
	}

	public boolean matchesMod(String value) {
		return modId.contains(value);
	}

	public boolean matchesResourceLocation(String value) {
		return resourceLocation.contains(value);
	}

	private static String normalize(String value) {
		return value.toLowerCase(Locale.ROOT);
	}
}
