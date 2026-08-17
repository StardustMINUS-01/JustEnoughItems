package mezz.jei.gui.match;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.regex.Pattern;

public record IngredientSelector(
	Optional<IngredientMatchInfo.Kind> kind,
	Optional<ResourceLocation> ingredientId,
	Optional<Pattern> ingredientIdPattern,
	Optional<ResourceLocation> tagId,
	Optional<Pattern> tagIdPattern
) {
	public IngredientSelector {
		kind = kind == null ? Optional.empty() : kind;
		ingredientId = ingredientId == null ? Optional.empty() : ingredientId;
		ingredientIdPattern = ingredientIdPattern == null ? Optional.empty() : ingredientIdPattern;
		tagId = tagId == null ? Optional.empty() : tagId;
		tagIdPattern = tagIdPattern == null ? Optional.empty() : tagIdPattern;
	}

	public static IngredientSelector item(ResourceLocation itemId) {
		return exact(Optional.of(IngredientMatchInfo.Kind.ITEM), itemId);
	}

	public static IngredientSelector tag(ResourceLocation tagId) {
		return tag(Optional.of(IngredientMatchInfo.Kind.ITEM), tagId);
	}

	public static Optional<IngredientSelector> parse(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		String trimmed = value.trim();
		try {
			Optional<IngredientMatchInfo.Kind> kind = Optional.empty();
			if (trimmed.startsWith("item:")) {
				kind = Optional.of(IngredientMatchInfo.Kind.ITEM);
				trimmed = trimmed.substring("item:".length());
			} else if (trimmed.startsWith("fluid:")) {
				kind = Optional.of(IngredientMatchInfo.Kind.FLUID);
				trimmed = trimmed.substring("fluid:".length());
			}
			if (trimmed.startsWith("#")) {
				String tagId = trimmed.substring(1);
				if (tagId.contains("*")) {
					return parseTagWildcard(kind, tagId);
				}
				return Optional.of(tag(kind, new ResourceLocation(tagId)));
			}
			if (trimmed.contains("*")) {
				return parseWildcard(kind, trimmed);
			}
			return Optional.of(exact(kind, new ResourceLocation(trimmed)));
		} catch (RuntimeException e) {
			return Optional.empty();
		}
	}

	public boolean matches(IngredientMatchInfo target) {
		if (kind.isPresent() && kind.get() != target.kind()) {
			return false;
		}
		return ingredientId.map(id -> id.equals(target.id())).orElse(false) ||
			ingredientIdPattern.map(pattern -> pattern.matcher(target.id().toString()).matches()).orElse(false) ||
			tagId.map(id -> target.tagIds().contains(id)).orElse(false) ||
			tagIdPattern.map(pattern -> target.tagIds().stream()
				.anyMatch(id -> pattern.matcher(id.toString()).matches()))
				.orElse(false);
	}

	private static IngredientSelector exact(Optional<IngredientMatchInfo.Kind> kind, ResourceLocation id) {
		return new IngredientSelector(kind, Optional.of(id), Optional.empty(), Optional.empty(), Optional.empty());
	}

	private static IngredientSelector tag(Optional<IngredientMatchInfo.Kind> kind, ResourceLocation tagId) {
		return new IngredientSelector(kind, Optional.empty(), Optional.empty(), Optional.of(tagId), Optional.empty());
	}

	private static Optional<IngredientSelector> parseWildcard(
		Optional<IngredientMatchInfo.Kind> kind,
		String value
	) {
		int separator = value.indexOf(':');
		if (separator <= 0 || separator == value.length() - 1) {
			return Optional.empty();
		}
		String namespace = value.substring(0, separator);
		String validationNamespace = namespace.replace('*', 'x');
		new ResourceLocation(validationNamespace + ":" + value.substring(separator + 1).replace('*', 'x'));
		return Optional.of(new IngredientSelector(
			kind,
			Optional.empty(),
			Optional.of(compileWildcard(value)),
			Optional.empty(),
			Optional.empty()
		));
	}

	private static Optional<IngredientSelector> parseTagWildcard(
		Optional<IngredientMatchInfo.Kind> kind,
		String tagId
	) {
		int separator = tagId.indexOf(':');
		if (separator <= 0 || separator == tagId.length() - 1) {
			return Optional.empty();
		}
		String namespace = tagId.substring(0, separator);
		String validationNamespace = namespace.replace('*', 'x');
		new ResourceLocation(validationNamespace + ":" + tagId.substring(separator + 1).replace('*', 'x'));
		return Optional.of(new IngredientSelector(
			kind,
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			Optional.of(compileWildcard(tagId))
		));
	}

	public static Pattern compileWildcard(String wildcard) {
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
