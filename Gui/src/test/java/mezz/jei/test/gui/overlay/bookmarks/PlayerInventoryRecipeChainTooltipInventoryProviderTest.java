package mezz.jei.test.gui.overlay.bookmarks;

import mezz.jei.gui.overlay.bookmarks.PlayerInventoryRecipeChainTooltipInventoryProvider;
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

public class PlayerInventoryRecipeChainTooltipInventoryProviderTest {
	@BeforeAll
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void availableStacksIncludeCurrentCraftingGrid() {
		Inventory inventory = new Inventory(null);
		inventory.setItem(0, new ItemStack(Items.DIAMOND, 3));
		CraftingMenu menu = new CraftingMenu(7, inventory);
		menu.getSlot(1).set(new ItemStack(Items.NETHERITE_PICKAXE));

		List<ItemStack> stacks = PlayerInventoryRecipeChainTooltipInventoryProvider.getAvailableStacks(inventory, menu);

		assertEquals(2, stacks.size());
		assertTrue(stacks.stream().anyMatch(stack -> ItemStack.isSameItemSameComponents(stack, new ItemStack(Items.DIAMOND)) && stack.getCount() == 3));
		assertTrue(stacks.stream().anyMatch(stack -> ItemStack.isSameItemSameComponents(stack, new ItemStack(Items.NETHERITE_PICKAXE)) && stack.getCount() == 1));
	}
}
