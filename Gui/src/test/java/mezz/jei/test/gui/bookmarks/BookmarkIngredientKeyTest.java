package mezz.jei.test.gui.bookmarks;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BookmarkIngredientKeyTest {

	@Test
	public void sameUidCountDifferingSnapshotsMatch() {
		BookmarkIngredientKey output = new BookmarkIngredientKey("item_stack", "minecraft:stick", "{Count:4b,id:\"minecraft:stick\"}");
		BookmarkIngredientKey inventory = new BookmarkIngredientKey("item_stack", "minecraft:stick", "{Count:1b,id:\"minecraft:stick\"}");

		Assertions.assertTrue(output.matchesCraftingAvailable(inventory));
		Assertions.assertEquals(output, inventory);
		Assertions.assertEquals(output.hashCode(), inventory.hashCode());
		Assertions.assertEquals(0, output.compareTo(inventory));
	}

	@Test
	public void differentVanillaSubtypesDoNotMatch() {
		BookmarkIngredientKey empty = new BookmarkIngredientKey("item_stack", "minecraft:furnace:empty", "{Count:1b,id:\"minecraft:furnace\"}");
		BookmarkIngredientKey charged = new BookmarkIngredientKey("item_stack", "minecraft:furnace:charged", "{Count:1b,id:\"minecraft:furnace\"}");

		Assertions.assertFalse(empty.matchesCraftingAvailable(charged));
		Assertions.assertFalse(charged.matchesCraftingAvailable(empty));
		Assertions.assertNotEquals(empty.getCraftingAvailabilityKey(), charged.getCraftingAvailabilityKey());
	}

	@Test
	public void relaxedNamespaceSubtypesMatch() {
		BookmarkIngredientKey empty = new BookmarkIngredientKey("item_stack", "mekanism:ultimate_injecting_factory:empty", "{Count:1b,id:\"mekanism:ultimate_injecting_factory\"}");
		BookmarkIngredientKey charged = new BookmarkIngredientKey("item_stack", "mekanism:ultimate_injecting_factory:charged", "{Count:1b,id:\"mekanism:ultimate_injecting_factory\"}");

		Assertions.assertTrue(empty.matchesCraftingAvailable(charged));
		Assertions.assertEquals(empty.getCraftingAvailabilityKey(), charged.getCraftingAvailabilityKey());
	}

	@Test
	public void unknownTypesDoNotMatchLiveItemStacks() {
		BookmarkIngredientKey legacy = new BookmarkIngredientKey("legacy", "gtceu:wetware_processor", null);
		BookmarkIngredientKey live = new BookmarkIngredientKey("item_stack", "gtceu:wetware_processor", "{Count:1b,id:\"gtceu:wetware_processor\"}");

		Assertions.assertFalse(legacy.matchesCraftingAvailable(live));
		Assertions.assertFalse(live.matchesCraftingAvailable(legacy));
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

		Assertions.assertEquals(canonical, shortForm);
		Assertions.assertEquals(canonical.hashCode(), shortForm.hashCode());
		Assertions.assertEquals(0, canonical.compareTo(shortForm));
	}
}
