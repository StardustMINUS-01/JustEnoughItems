package mezz.jei.gui.bookmarks;

import mezz.jei.common.bookmarks.CraftingStackMatcher;
import org.jetbrains.annotations.Nullable;

public record BookmarkIngredientKey(
	String ingredientTypeUid,
	String ingredientUid,
	@Nullable String serializedIngredient
) implements Comparable<BookmarkIngredientKey> {
	public static final String LEGACY_TYPE_UID = "legacy";
	public static final String UNKNOWN_TYPE_UID = "unknown";
	private static final String ITEM_STACK_TYPE_UID = "minecraft:item_stack";

	public BookmarkIngredientKey {
		ingredientTypeUid = clean(ingredientTypeUid, UNKNOWN_TYPE_UID);
		ingredientUid = clean(ingredientUid, "fallback:unknown");
		serializedIngredient = serializedIngredient == null || serializedIngredient.isBlank() ? null : serializedIngredient;
	}

	public static BookmarkIngredientKey of(String ingredientTypeUid, String ingredientUid) {
		return new BookmarkIngredientKey(ingredientTypeUid, ingredientUid, null);
	}

	public static BookmarkIngredientKey fallback(String ingredientUid) {
		return new BookmarkIngredientKey(UNKNOWN_TYPE_UID, ingredientUid, null);
	}

	public static BookmarkIngredientKey legacy(String value) {
		int separatorIndex = value.indexOf('|');
		if (separatorIndex > 0 && separatorIndex + 1 < value.length()) {
			return new BookmarkIngredientKey(value.substring(0, separatorIndex), value.substring(separatorIndex + 1), null);
		}
		return new BookmarkIngredientKey(LEGACY_TYPE_UID, value, null);
	}

	public String stableKey() {
		return ingredientTypeUid + "|" + ingredientUid;
	}

	public boolean matchesCraftingAvailable(BookmarkIngredientKey available) {
		if (equals(available)) {
			return true;
		}
		if (!ITEM_STACK_TYPE_UID.equals(ingredientTypeUid) ||
			!ITEM_STACK_TYPE_UID.equals(available.ingredientTypeUid)) {
			return false;
		}
		String requiredBase = itemRegistryUid(ingredientUid);
		if (!requiredBase.equals(itemRegistryUid(available.ingredientUid))) {
			return false;
		}
		return itemNamespace(requiredBase)
			.map(CraftingStackMatcher::isNbtRelaxedCraftingNamespace)
			.orElse(false);
	}

	public BookmarkIngredientKey getCraftingAvailabilityKey() {
		if (!ITEM_STACK_TYPE_UID.equals(ingredientTypeUid)) {
			return this;
		}
		String baseUid = itemRegistryUid(ingredientUid);
		if (itemNamespace(baseUid)
			.map(CraftingStackMatcher::isNbtRelaxedCraftingNamespace)
			.orElse(false)) {
			return new BookmarkIngredientKey(ingredientTypeUid, baseUid, null);
		}
		return this;
	}

	@Override
	public int compareTo(BookmarkIngredientKey other) {
		int type = ingredientTypeUid.compareTo(other.ingredientTypeUid);
		if (type != 0) {
			return type;
		}
		int uid = ingredientUid.compareTo(other.ingredientUid);
		if (uid != 0) {
			return uid;
		}
		if (serializedIngredient == null && other.serializedIngredient == null) {
			return 0;
		}
		if (serializedIngredient == null) {
			return -1;
		}
		if (other.serializedIngredient == null) {
			return 1;
		}
		return serializedIngredient.compareTo(other.serializedIngredient);
	}

	private static String clean(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private static String itemRegistryUid(String uid) {
		int namespaceSeparator = uid.indexOf(':');
		if (namespaceSeparator < 0) {
			return uid;
		}
		int subtypeSeparator = uid.indexOf(':', namespaceSeparator + 1);
		if (subtypeSeparator < 0) {
			return uid;
		}
		return uid.substring(0, subtypeSeparator);
	}

	private static java.util.Optional<String> itemNamespace(String uid) {
		int namespaceSeparator = uid.indexOf(':');
		if (namespaceSeparator <= 0) {
			return java.util.Optional.empty();
		}
		return java.util.Optional.of(uid.substring(0, namespaceSeparator));
	}
}
