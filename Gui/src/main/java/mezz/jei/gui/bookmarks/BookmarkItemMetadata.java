/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Portions of this file are adapted from GTNewHorizons NotEnoughItems:
 * https://github.com/GTNewHorizons/NotEnoughItems
 *
 * GTNH NEI modifications copyright (c) 2019-2024 mitchej123 and the GTNH Team.
 */
package mezz.jei.gui.bookmarks;

import mezz.jei.common.util.SaturatedMath;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record BookmarkItemMetadata(
	int groupId,
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
		int groupId,
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
		int groupId,
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
		int groupId,
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
		type = type == null ? BookmarkItemType.ITEM : type;
		permutations = permutations == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(permutations));
		containerItemCraftingUses = containerItem == null ? 1 : Math.max(0, containerItemCraftingUses);
	}

	public static BookmarkItemMetadata defaultForGroup(int groupId) {
		return new BookmarkItemMetadata(groupId, BookmarkItemType.ITEM, 1, 1, CHANCE_FULL, null, null, Set.of(), null, 1, null);
	}

	public BookmarkItemMetadata withGroupId(int groupId) {
		return new BookmarkItemMetadata(groupId, type, multiplier, factor, chance, recipeTypeUid, recipeUid, permutations, containerItem, containerItemCraftingUses, brokenContainerItem);
	}

	public BookmarkItemMetadata withMultiplier(long multiplier) {
		return new BookmarkItemMetadata(groupId, type, multiplier, factor, chance, recipeTypeUid, recipeUid, permutations, containerItem, containerItemCraftingUses, brokenContainerItem);
	}

	public BookmarkItemMetadata withFactor(long factor) {
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
		long amount = SaturatedMath.multiply(Math.max(0, factor), Math.max(0, effectiveMultiplier));
		if (chance > 0 && chance != CHANCE_FULL) {
			if (type.isGraphInput()) {
				return SaturatedMath.divideRoundUp(SaturatedMath.multiply(amount, chance), CHANCE_FULL);
			}
			return SaturatedMath.multiply(amount, chance) / CHANCE_FULL;
		}
		return amount;
	}

	public long multiplierFromAmount(long amount) {
		if (factor <= 0 || chance <= 0) {
			return 0;
		}
		if (chance == CHANCE_FULL) {
			return SaturatedMath.divideRoundUp(amount, factor);
		}
		long denominator = SaturatedMath.multiply(factor, chance);
		return SaturatedMath.divideRoundUp(SaturatedMath.multiply(amount, CHANCE_FULL), denominator);
	}

	public boolean equalsRecipe(BookmarkItemMetadata metadata) {
		return equalsRecipe(metadata.recipeUid(), metadata.groupId());
	}

	public boolean equalsRecipe(@Nullable ResourceLocation recipeUid, int groupId) {
		return this.groupId == groupId && recipeUid != null && recipeUid.equals(this.recipeUid);
	}

	public boolean emptyFactor() {
		return factor <= 0 || chance <= 0;
	}

	public boolean isDefault() {
		return groupId == BookmarkGroupManager.DEFAULT_GROUP_ID &&
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

}
