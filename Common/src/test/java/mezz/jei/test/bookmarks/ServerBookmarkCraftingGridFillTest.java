package mezz.jei.test.bookmarks;

import mezz.jei.common.bookmarks.ServerBookmarkCraftingGridFill;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServerBookmarkCraftingGridFillTest {
	@BeforeAll
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void countsReusableToolAlreadyInCraftingGridAsAvailable() {
		Inventory inventory = new Inventory(null);
		inventory.setItem(0, new ItemStack(Items.DIAMOND, 3));
		CraftingMenu menu = new CraftingMenu(7, inventory);
		menu.getSlot(1).set(new ItemStack(Items.NETHERITE_PICKAXE));

		int filled = ServerBookmarkCraftingGridFill.fill(
			menu,
			7,
			inventory,
			null,
			List.of(new ItemStack(Items.NETHERITE_PICKAXE), new ItemStack(Items.DIAMOND)),
			1
		);

		assertEquals(1, filled);
		assertTrue(ItemStack.isSameItemSameComponents(new ItemStack(Items.NETHERITE_PICKAXE), menu.getSlot(1).getItem()));
		assertTrue(ItemStack.isSameItemSameComponents(new ItemStack(Items.DIAMOND), menu.getSlot(2).getItem()));
		assertEquals(2, inventory.getItem(0).getCount());
	}
}
