package mezz.jei.test.bookmarks;

import mezz.jei.common.bookmarks.VanillaCraftingGridSlots;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VanillaCraftingGridSlotsTest {
	@BeforeAll
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void findsTheVanillaTwoByTwoAndThreeByThreeCraftingSlots() {
		Inventory inventory = new Inventory(null);

		assertEquals(4, VanillaCraftingGridSlots.getCraftingSlots(new InventoryMenu(inventory, false, null)).size());
		assertEquals(9, VanillaCraftingGridSlots.getCraftingSlots(new CraftingMenu(1, inventory)).size());
	}

	@Test
	public void ignoresOtherMenus() {
		AbstractContainerMenu menu = new AbstractContainerMenu(MenuType.GENERIC_9x1, 1) {
			@Override
			public boolean stillValid(Player player) {
				return true;
			}

			@Override
			public ItemStack quickMoveStack(Player player, int index) {
				return ItemStack.EMPTY;
			}
		};

		assertEquals(0, VanillaCraftingGridSlots.getCraftingSlots(menu).size());
	}
}
