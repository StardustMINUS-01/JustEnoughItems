package mezz.jei.gui.bookmarks;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record BookmarkItemMetadata(
	String groupId,
	BookmarkItemType type,
	long multiplier,
	long factor,
	long chance,
	@Nullable ResourceLocation recipeTypeUid,
	@Nullable ResourceLocation recipeUid,
	Set<BookmarkIngredientKey> permutations,
	@Nullable BookmarkIngredientKey containerItem,
	long containerItemCraftingUses,
	@Nullable BookmarkIngredientKey brokenContainerItem
) {
	public static final long CHANCE_FULL = 10_000L;

	public BookmarkItemMetadata(
		String groupId,
		BookmarkItemType type,
		long multiplier,
		long factor,
		long chance,
		@Nullable ResourceLocation recipeTypeUid,
		@Nullable ResourceLocation recipeUid,
		Set<BookmarkIngredientKey> permutations
	) {
		this(groupId, type, multiplier, factor, chance, recipeTypeUid, recipeUid, permutations, null);
	}

	public BookmarkItemMetadata(
		String groupId,
		BookmarkItemType type,
		long multiplier,
		long factor,
		long chance,
		@Nullable ResourceLocation recipeTypeUid,
		@Nullable ResourceLocation recipeUid,
		Set<BookmarkIngredientKey> permutations,
		@Nullable BookmarkIngredientKey containerItem
	) {
		this(groupId, type, multiplier, factor, chance, recipeTypeUid, recipeUid, permutations, containerItem, 1);
	}

	public BookmarkItemMetadata(
		String groupId,
		BookmarkItemType type,
		long multiplier,
		long factor,
		long chance,
		@Nullable ResourceLocation recipeTypeUid,
		@Nullable ResourceLocation recipeUid,
		Set<BookmarkIngredientKey> permutations,
		@Nullable BookmarkIngredientKey containerItem,
		long containerItemCraftingUses
	) {
		this(groupId, type, multiplier, factor, chance, recipeTypeUid, recipeUid, permutations, containerItem, containerItemCraftingUses, null);
	}

	public BookmarkItemMetadata {
		groupId = groupId == null ? BookmarkGroupManager.DEFAULT_GROUP_ID : groupId;
		type = type == null ? BookmarkItemType.ITEM : type;
		permutations = permutations == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(permutations));
		containerItemCraftingUses = containerItem == null ? 1 : Math.max(0, containerItemCraftingUses);
	}

	public static BookmarkItemMetadata defaultForGroup(String groupId) {
		return new BookmarkItemMetadata(groupId, BookmarkItemType.ITEM, 1, 1, CHANCE_FULL, null, null, Set.of(), null, 1, null);
	}

	public BookmarkItemMetadata withGroupId(String groupId) {
		return new BookmarkItemMetadata(groupId, type, multiplier, factor, chance, recipeTypeUid, recipeUid, permutations, containerItem, containerItemCraftingUses, brokenContainerItem);
	}

	public BookmarkItemMetadata withMultiplier(long multiplier) {
		return new BookmarkItemMetadata(groupId, type, multiplier, factor, chance, recipeTypeUid, recipeUid, permutations, containerItem, containerItemCraftingUses, brokenContainerItem);
	}

	public BookmarkItemMetadata withPermutations(Set<BookmarkIngredientKey> permutations) {
		return new BookmarkItemMetadata(groupId, type, multiplier, factor, chance, recipeTypeUid, recipeUid, permutations, containerItem, containerItemCraftingUses, brokenContainerItem);
	}

	public BookmarkItemMetadata withType(BookmarkItemType type) {
		return new BookmarkItemMetadata(groupId, type, multiplier, factor, chance, recipeTypeUid, recipeUid, permutations, containerItem, containerItemCraftingUses, brokenContainerItem);
	}

	public boolean containsItems(BookmarkItemMetadata item) {
		return permutations.stream().anyMatch(item.permutations()::contains);
	}

	public boolean isSatisfiedBy(BookmarkItemMetadata available) {
		return permutations.stream()
			.anyMatch(requiredKey -> available.permutations().stream()
				.anyMatch(requiredKey::matchesCraftingAvailable));
	}

	public long amount() {
		return amount(multiplier);
	}

	public long amount(long multiplier) {
		long effectiveMultiplier = type.scalesWithMultiplier() ? multiplier : 1;
		long amount = saturatedMultiply(Math.max(0, factor), Math.max(0, effectiveMultiplier));
		if (chance > 0 && chance != CHANCE_FULL) {
			if (type.isGraphInput()) {
				return saturatedDivideRoundUp(saturatedMultiply(amount, chance), CHANCE_FULL);
			}
			return saturatedMultiply(amount, chance) / CHANCE_FULL;
		}
		return amount;
	}

	public long multiplierFromAmount(long amount) {
		if (factor <= 0 || chance <= 0) {
			return 0;
		}
		if (chance == CHANCE_FULL) {
			return saturatedDivideRoundUp(amount, factor);
		}
		long denominator = saturatedMultiply(factor, chance);
		return saturatedDivideRoundUp(saturatedMultiply(amount, CHANCE_FULL), denominator);
	}

	public boolean equalsRecipe(BookmarkItemMetadata metadata) {
		return equalsRecipe(metadata.recipeUid(), metadata.groupId());
	}

	public boolean equalsRecipe(@Nullable ResourceLocation recipeUid, String groupId) {
		return this.groupId.equals(groupId) && recipeUid != null && recipeUid.equals(this.recipeUid);
	}

	public boolean emptyFactor() {
		return factor <= 0 || chance <= 0;
	}

	public boolean isDefault() {
		return BookmarkGroupManager.DEFAULT_GROUP_ID.equals(groupId) &&
			type == BookmarkItemType.ITEM &&
			multiplier == 1 &&
			factor == 1 &&
			chance == CHANCE_FULL &&
			recipeTypeUid == null &&
			recipeUid == null &&
			permutations.isEmpty() &&
			containerItem == null &&
			containerItemCraftingUses == 1 &&
			brokenContainerItem == null;
	}

	private static long saturatedMultiply(long first, long second) {
		try {
			return Math.multiplyExact(first, second);
		} catch (ArithmeticException e) {
			return Long.MAX_VALUE;
		}
	}

	private static long saturatedDivideRoundUp(long numerator, long denominator) {
		if (denominator <= 0 || numerator <= 0) {
			return 0;
		}
		return 1 + (numerator - 1) / denominator;
	}
}
