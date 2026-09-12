package mezz.jei.test.gui.overlay.bookmarks;

import mezz.jei.gui.overlay.bookmarks.PlayerInventoryRecipeChainTooltipInventoryProvider;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.chain.BookmarkContainerStorageScanner;
import mezz.jei.gui.bookmarks.chain.BookmarkExternalStorageSnapshots;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkAvailableStacksProviders;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TooltipInventoryTest {
	@Test
	public void readsSourceStorage() throws Exception {
		Inventory inventory = new Inventory(null);
		inventory.setItem(0, new ItemStack(Items.DIAMOND, 3));
		var menu = ChestMenu.threeRows(7, inventory);
		List<BookmarkExternalStorageSnapshots.Entry> entries = new ArrayList<>();
		entries.add(new BookmarkExternalStorageSnapshots.Entry(new ItemStack(Items.IRON_INGOT), 500));
		BookmarkAvailableStacksProviders.registerProvider(candidate -> candidate == menu ? Optional.of(List.of(new ItemStack(Items.IRON_INGOT, 500))) : Optional.empty());
		try (var registration = BookmarkExternalStorageSnapshots.registerProvider(new BookmarkExternalStorageSnapshots.Provider() {
			@Override
			public Optional<BookmarkContainerStorageScanner.StorageSnapshot> scan(Object menu, Object screen,
			Function<ItemStack, Optional<BookmarkIngredientKey>> keyFactory) { return Optional.empty(); }
			@Override
			public Optional<List<BookmarkExternalStorageSnapshots.Entry>> readEntries(Object candidate) {
			return candidate == menu ? Optional.of(List.copyOf(entries)) : Optional.empty();
			}
			})
		) {
			var stacks = PlayerInventoryRecipeChainTooltipInventoryProvider.getTreeAvailableStacks(inventory, menu, menu);
			assertEquals(500, amount(stacks, Items.IRON_INGOT));
			assertEquals(3, amount(stacks, Items.DIAMOND));
			entries.set(0, new BookmarkExternalStorageSnapshots.Entry(new ItemStack(Items.IRON_INGOT), 700));
			assertEquals(700, amount(PlayerInventoryRecipeChainTooltipInventoryProvider.getTreeAvailableStacks(inventory, menu, menu), Items.IRON_INGOT));
			var unrelated = ChestMenu.threeRows(8, inventory);
			assertEquals(3, PlayerInventoryRecipeChainTooltipInventoryProvider.getTreeAvailableStacks(inventory, unrelated, menu).stream().mapToInt(ItemStack::getCount).sum());
		}
	}

	@Test
	public void includesTreeStorage() {
		Inventory inventory = new Inventory(null);
		inventory.setItem(0, new ItemStack(Items.DIAMOND, 3));
		CraftingMenu menu = new CraftingMenu(7, inventory);
		menu.getSlot(1).set(new ItemStack(Items.STICK, 2));
		BookmarkAvailableStacksProviders.registerProvider(candidate -> candidate == menu ? Optional.of(List.of(new ItemStack(Items.IRON_INGOT, 64))) : Optional.empty());
		var stacks = PlayerInventoryRecipeChainTooltipInventoryProvider.getTreeAvailableStacks(inventory, menu, menu);
		assertEquals(3, amount(stacks, Items.DIAMOND));
		assertEquals(2, amount(stacks, Items.STICK));
		assertEquals(64, amount(stacks, Items.IRON_INGOT));
	}

	private static int amount(List<ItemStack> stacks, Item item) {
		ItemStack expected = new ItemStack(item);
		return stacks.stream().filter(stack -> ItemStack.isSameItemSameComponents(stack, expected)).mapToInt(ItemStack::getCount).sum();
	}

	@BeforeAll
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void includesCraftingGrid() {
		Inventory inventory = new Inventory(null);
		inventory.setItem(0, new ItemStack(Items.DIAMOND, 3));
		CraftingMenu menu = new CraftingMenu(7, inventory);
		menu.getSlot(1).set(new ItemStack(Items.NETHERITE_PICKAXE));

		List<ItemStack> stacks = PlayerInventoryRecipeChainTooltipInventoryProvider.getAvailableStacks(inventory, menu);

		assertEquals(2, stacks.size());
		assertEquals(3, amount(stacks, Items.DIAMOND));
		assertEquals(1, amount(stacks, Items.NETHERITE_PICKAXE));
	}
}
