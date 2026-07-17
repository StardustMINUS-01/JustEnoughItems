package mezz.jei.gui.favorites.preferences;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.regex.Pattern;

public record RecipePreferenceTarget(
	Optional<RecipePreferenceIngredientInfo.Kind> kind,
	Optional<ResourceLocation> ingredientId,
	Optional<Pattern> ingredientIdPattern,
	Optional<ResourceLocation> tagId
) {
	public RecipePreferenceTarget {
		kind = kind == null ? Optional.empty() : kind;
		ingredientId = ingredientId == null ? Optional.empty() : ingredientId;
		ingredientIdPattern = ingredientIdPattern == null ? Optional.empty() : ingredientIdPattern;
		tagId = tagId == null ? Optional.empty() : tagId;
	}

	public static RecipePreferenceTarget item(ResourceLocation itemId) {
		return exact(Optional.of(RecipePreferenceIngredientInfo.Kind.ITEM), itemId);
	}

	public static RecipePreferenceTarget tag(ResourceLocation tagId) {
		return tag(Optional.of(RecipePreferenceIngredientInfo.Kind.ITEM), tagId);
	}

	public static Optional<RecipePreferenceTarget> parse(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		String trimmed = value.trim();
		try {
			Optional<RecipePreferenceIngredientInfo.Kind> kind = Optional.empty();
			if (trimmed.startsWith("item:")) {
				kind = Optional.of(RecipePreferenceIngredientInfo.Kind.ITEM);
				trimmed = trimmed.substring("item:".length());
			} else if (trimmed.startsWith("fluid:")) {
				kind = Optional.of(RecipePreferenceIngredientInfo.Kind.FLUID);
				trimmed = trimmed.substring("fluid:".length());
			}
			if (trimmed.startsWith("#")) {
				return Optional.of(tag(kind, ResourceLocation.parse(trimmed.substring(1))));
			}
			if (trimmed.contains("*")) {
				return parseWildcard(kind, trimmed);
			}
			return Optional.of(exact(kind, ResourceLocation.parse(trimmed)));
		} catch (RuntimeException e) {
			return Optional.empty();
		}
	}

	public boolean matches(RecipePreferenceIngredientInfo target) {
		if (kind.isPresent() && kind.get() != target.kind()) {
			return false;
		}
		return ingredientId.map(id -> id.equals(target.id())).orElse(false) ||
			ingredientIdPattern.map(pattern -> pattern.matcher(target.id().toString()).matches()).orElse(false) ||
			tagId.map(id -> target.tagIds().contains(id)).orElse(false);
	}

	private static RecipePreferenceTarget exact(Optional<RecipePreferenceIngredientInfo.Kind> kind, ResourceLocation id) {
		return new RecipePreferenceTarget(kind, Optional.of(id), Optional.empty(), Optional.empty());
	}

	private static RecipePreferenceTarget tag(Optional<RecipePreferenceIngredientInfo.Kind> kind, ResourceLocation tagId) {
		return new RecipePreferenceTarget(kind, Optional.empty(), Optional.empty(), Optional.of(tagId));
	}

	private static Optional<RecipePreferenceTarget> parseWildcard(Optional<RecipePreferenceIngredientInfo.Kind> kind, String value) {
		int separator = value.indexOf(':');
		if (separator <= 0 || separator == value.length() - 1) {
			return Optional.empty();
		}
		String namespace = value.substring(0, separator);
		String path = value.substring(separator + 1);
		if (namespace.contains("*")) {
			return Optional.empty();
		}
		ResourceLocation.parse(namespace + ":" + path.replace('*', 'x'));
		return Optional.of(new RecipePreferenceTarget(kind, Optional.empty(), Optional.of(compileWildcard(value)), Optional.empty()));
	}

	private static Pattern compileWildcard(String wildcard) {
		StringBuilder regex = new StringBuilder();
		for (int i = 0; i < wildcard.length(); i++) {
			char c = wildcard.charAt(i);
			if (c == '*') {
				regex.append(".*");
			} else {
				regex.append(Pattern.quote(String.valueOf(c)));
			}
		}
		return Pattern.compile(regex.toString());
	}
}
