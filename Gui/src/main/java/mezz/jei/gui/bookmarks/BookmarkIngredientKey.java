package mezz.jei.gui.bookmarks;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.common.bookmarks.CraftingStackMatcher;
import mezz.jei.common.config.DebugConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

public record BookmarkIngredientKey(
	String ingredientTypeUid,
	String ingredientUid,
	@Nullable String serializedIngredient
) implements Comparable<BookmarkIngredientKey> {
	private static final Logger LOGGER = LogManager.getLogger();
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

	/**
	 * True if both keys identify the same ingredient kind (same type + uid),
	 * ignoring the NBT snapshot. Used to match ingredients across layouts whose
	 * NBT may differ slightly (e.g. tool damage, tconstruct materials, GT tool
	 * attributes) while still keeping each variant's snapshot for permutation.
	 */
	public @Nullable ITypedIngredient<?> typedIngredient() { return null; }

	public boolean matches(BookmarkIngredientKey other) {
		return ingredientTypeUid.equals(other.ingredientTypeUid) &&
			ingredientUid.equals(other.ingredientUid);
	}

	public boolean matchesCraftingAvailable(BookmarkIngredientKey available) {
		if (equals(available)) {
			return true;
		}
		// Accept both the canonical "minecraft:item_stack" and the short "item_stack"
		// type uids: different sources (favorites.json round-trip, live typed
		// ingredients) produce either form.
		boolean requiredIsItem = isItemStackTypeUid(ingredientTypeUid);
		boolean availableIsItem = isItemStackTypeUid(available.ingredientTypeUid);
		if (!requiredIsItem || !availableIsItem) {
			// Bug6: 收藏链根输入(来自书签 round-trip)的 typeUid 可能是 legacy/unknown，
			// 而背包物品是 item_stack；直接拒绝会让已合成的中间产物永远无法被识别为
			// 已满足，导致每次派发都从链底重新穿透 → 过度合成(wp×4/wpa×3/wpc×2)。
			// 放宽为：稳定物品标识(忽略类型标注前缀与 subtype 后缀)相同即视为可满足。
			return itemBaseId(ingredientUid).equals(itemBaseId(available.ingredientUid));
		}
		String requiredUid = ingredientUid;
		String availableUid = available.ingredientUid;
		if (requiredUid.equals(availableUid)) {
			// Recipe-output snapshots keep the recipe output count (e.g. Count:4b for
			// quartz glass) while inventory snapshots are normalized to Count:1b; both
			// identify the same craftable item, so the Count field must not block matching.
			if (itemStackSnapshotsMatchIgnoringCount(serializedIngredient, available.serializedIngredient)) {
				return true;
			}
			if (DebugConfig.isDebugModeEnabled()) {
				LOGGER.info("[Bug6] KEY-SNAPSHOT-MISMATCH requiredType={} requiredUid={} requiredSerialized={} availableType={} availableUid={} availableSerialized={}",
					ingredientTypeUid, ingredientUid, truncate(serializedIngredient),
					available.ingredientTypeUid, available.ingredientUid, truncate(available.serializedIngredient));
			}
			// Same registry item, same uid, but the NBT snapshots differ: only treat
			// them as interchangeable inside namespaces with volatile crafting NBT.
			return itemNamespace(itemBaseId(requiredUid))
				.map(CraftingStackMatcher::isNbtRelaxedCraftingNamespace)
				.orElse(false);
		}
		// Different uid (subtype / NBT suffix): the item base must still agree, but
		// different variants of a vanilla item are NOT interchangeable. Only the
		// nbt-relaxed namespaces (mekanism/gtceu) accept arbitrary subtype variants.
		String requiredBase = itemBaseId(requiredUid);
		if (!requiredBase.equals(itemBaseId(availableUid))) {
			return false;
		}
		return itemNamespace(requiredBase)
			.map(CraftingStackMatcher::isNbtRelaxedCraftingNamespace)
			.orElse(false);
	}

	private static String truncate(String value) {
		if (value == null) {
			return "null";
		}
		return value.length() <= 160 ? value : value.substring(0, 160) + "...";
	}

	/**
	 * Compares two ItemStack NBT snapshots (SNBT) while ignoring the Count field.
	 * Returns true when both snapshots are absent (plain item) or when the NBT is
	 * identical apart from the stack size. The string-level fallback also strips
	 * the Count field so stack-size differences never block matching even when the
	 * NBT parser rejects modded compounds (e.g. GTCEu/AE2 tags).
	 */
	private static boolean itemStackSnapshotsMatchIgnoringCount(@Nullable String snapshotA, @Nullable String snapshotB) {
		if (snapshotA == null && snapshotB == null) {
			return true;
		}
		if (snapshotA == null || snapshotB == null) {
			return false;
		}
		try {
			CompoundTag tagA = NbtUtils.snbtToStructure(snapshotA);
			CompoundTag tagB = NbtUtils.snbtToStructure(snapshotB);
			if (tagA != null && tagB != null) {
				CompoundTag copyA = tagA.copy();
				CompoundTag copyB = tagB.copy();
				copyA.remove("Count");
				copyB.remove("Count");
				return copyA.equals(copyB);
			}
		} catch (CommandSyntaxException | RuntimeException ignored) {
			// fall through to the string-level comparison below
		}
		return stripCountField(snapshotA).equals(stripCountField(snapshotB));
	}

	/**
	 * Removes the Count field from an SNBT string, handling it at the start,
	 * middle or end of the compound (e.g. "{Count:4b,id:...}", "{id:...,Count:4b}",
	 * "{id:...,Count:4b,tag:{...}}"). Only used as a fallback when the NBT parser
	 * fails; the parsed-tag path in itemStackSnapshotsMatchIgnoringCount is the
	 * primary comparison.
	 */
	private static String stripCountField(String snbt) {
		return snbt.replaceAll("Count:\\d+[bsilfd]?,?", "")
			.replaceAll(",,", ",")
			.replaceAll("\\{,", "{")
			.replaceAll(",}", "}")
			.trim();
	}

	public BookmarkIngredientKey getCraftingAvailabilityKey() {
		if (!ITEM_STACK_TYPE_UID.equals(ingredientTypeUid) && !"item_stack".equals(ingredientTypeUid)) {
			return this;
		}
		String baseUid = itemBaseId(ingredientUid);
		return new BookmarkIngredientKey(ingredientTypeUid, baseUid, null);
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

	/**
	 * True for both the canonical "minecraft:item_stack" and the short "item_stack"
	 * type uids. JEI's typed-ingredient type uid is "minecraft:item_stack", but some
	 * keys (e.g. rebuilt from favorites.json or other mods) carry the bare "item_stack"
	 * form; both identify the same ItemStack ingredient.
	 */
	private static boolean isItemStackTypeUid(String typeUid) {
		return ITEM_STACK_TYPE_UID.equals(typeUid) || "item_stack".equals(typeUid);
	}

	/**
	 * True for item ingredient keys that this fork may relax-match across
	 * bookmark round-trip type annotations and recipe-slot key forms:
	 * item-stack typed keys plus the legacy/unknown forms produced by
	 * favorites.json deserialization.
	 */
	public static boolean isItemKey(BookmarkIngredientKey key) {
		String typeUid = key.ingredientTypeUid;
		if (isItemStackTypeUid(typeUid)) {
			return true;
		}
		return switch (typeUid) {
			case "item", "minecraft:item", LEGACY_TYPE_UID, UNKNOWN_TYPE_UID -> true;
			default -> false;
		};
	}

	/**
	 * Returns the stable item identity (namespace:path) carried by this key's
	 * ingredient uid, tolerant of the annotation forms seen across bookmark
	 * round-trips and recipe slots:
	 * "minecraft:item_stack:gtceu:foo", "gtceu:foo" and "gtceu:foo:subtype"
	 * all resolve to "gtceu:foo".
	 */
	public String itemBaseId() {
		return itemBaseId(ingredientUid);
	}

	private static String itemBaseId(String uid) {
		String stripped = stripJeiTypePrefix(uid);
		int firstColon = stripped.indexOf(':');
		if (firstColon < 0) {
			return stripped;
		}
		int secondColon = stripped.indexOf(':', firstColon + 1);
		return secondColon < 0 ? stripped : stripped.substring(0, secondColon);
	}

	private static String stripJeiTypePrefix(String uid) {
		int firstColon = uid.indexOf(':');
		if (firstColon <= 0) {
			return uid;
		}
		int secondColon = uid.indexOf(':', firstColon + 1);
		if (secondColon < 0) {
			return uid;
		}
		String secondSegment = uid.substring(firstColon + 1, secondColon);
		if (isJeiTypeMarker(secondSegment)) {
			return uid.substring(secondColon + 1);
		}
		return uid;
	}

	private static boolean isJeiTypeMarker(String segment) {
		return switch (segment) {
			case "item", "item_stack", "minecraft:item", "minecraft:item_stack" -> true;
			default -> false;
		};
	}

	private static java.util.Optional<String> itemNamespace(String uid) {
		int namespaceSeparator = uid.indexOf(':');
		if (namespaceSeparator <= 0) {
			return java.util.Optional.empty();
		}
		return java.util.Optional.of(uid.substring(0, namespaceSeparator));
	}
}
