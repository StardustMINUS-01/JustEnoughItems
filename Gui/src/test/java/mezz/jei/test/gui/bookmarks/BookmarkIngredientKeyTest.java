package mezz.jei.test.gui.bookmarks;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 1.20.1 平台适配回归: 与 1.21.1 相比, 实时类型 uid 是短形式 "item_stack",
 * 且序列化快照是包含 Count 的 SNBT(1.21.1 为不含 Count 的 DataComponentPatch)。
 * matchesCraftingAvailable 必须: 忽略 Count 差异、区分原版 subtype、
 * 允许 legacy/unknown round-trip 键按物品身份匹配、并在受信命名空间内放宽 NBT。
 */
public class BookmarkIngredientKeyTest {

	@Test
	public void sameUidCountDifferingSnapshotsMatch() {
		BookmarkIngredientKey output = new BookmarkIngredientKey("item_stack", "minecraft:stick", "{Count:4b,id:\"minecraft:stick\"}");
		BookmarkIngredientKey inventory = new BookmarkIngredientKey("item_stack", "minecraft:stick", "{Count:1b,id:\"minecraft:stick\"}");

		Assertions.assertTrue(output.matchesCraftingAvailable(inventory));
	}

	@Test
	public void differentVanillaSubtypesDoNotMatch() {
		BookmarkIngredientKey empty = new BookmarkIngredientKey("item_stack", "minecraft:furnace:empty", "{Count:1b,id:\"minecraft:furnace\"}");
		BookmarkIngredientKey charged = new BookmarkIngredientKey("item_stack", "minecraft:furnace:charged", "{Count:1b,id:\"minecraft:furnace\"}");

		Assertions.assertFalse(empty.matchesCraftingAvailable(charged));
		Assertions.assertFalse(charged.matchesCraftingAvailable(empty));
	}

	@Test
	public void relaxedNamespaceSubtypesMatch() {
		BookmarkIngredientKey empty = new BookmarkIngredientKey("item_stack", "mekanism:ultimate_injecting_factory:empty", "{Count:1b,id:\"mekanism:ultimate_injecting_factory\"}");
		BookmarkIngredientKey charged = new BookmarkIngredientKey("item_stack", "mekanism:ultimate_injecting_factory:charged", "{Count:1b,id:\"mekanism:ultimate_injecting_factory\"}");

		Assertions.assertTrue(empty.matchesCraftingAvailable(charged));
	}

	@Test
	public void legacyRoundTripKeyMatchesLiveItemStackByItemIdentity() {
		BookmarkIngredientKey legacy = new BookmarkIngredientKey("legacy", "gtceu:wetware_processor", null);
		BookmarkIngredientKey live = new BookmarkIngredientKey("item_stack", "gtceu:wetware_processor", "{Count:1b,id:\"gtceu:wetware_processor\"}");

		Assertions.assertTrue(legacy.matchesCraftingAvailable(live));
		Assertions.assertTrue(live.matchesCraftingAvailable(legacy));
	}

	@Test
	public void differentItemsDoNotMatch() {
		BookmarkIngredientKey stick = new BookmarkIngredientKey("item_stack", "minecraft:stick", "{Count:1b,id:\"minecraft:stick\"}");
		BookmarkIngredientKey stone = new BookmarkIngredientKey("item_stack", "minecraft:stone", "{Count:1b,id:\"minecraft:stone\"}");

		Assertions.assertFalse(stick.matchesCraftingAvailable(stone));
	}

	@Test
	public void canonicalAndShortItemStackTypeUidsAreEquivalent() {
		BookmarkIngredientKey canonical = new BookmarkIngredientKey("minecraft:item_stack", "gtceu:wetware_processor", null);
		BookmarkIngredientKey shortForm = new BookmarkIngredientKey("item_stack", "gtceu:wetware_processor", null);

		Assertions.assertEquals("gtceu:wetware_processor", canonical.itemBaseId());
		Assertions.assertEquals(canonical.itemBaseId(), shortForm.itemBaseId());
		Assertions.assertTrue(BookmarkIngredientKey.isItemKey(canonical));
		Assertions.assertTrue(BookmarkIngredientKey.isItemKey(shortForm));
	}
}
