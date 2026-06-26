package mezz.jei.test.bookmarks;

import mezz.jei.common.bookmarks.BookmarkPullTarget;
import mezz.jei.common.bookmarks.ServerBookmarkPullTransfer;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.Bootstrap;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ServerBookmarkPullTransferTest {
	@BeforeAll
	public static void setup() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void pullsRequestedItemsFromContainerSlotsIntoPlayerInventory() {
		SimpleContainer container = new SimpleContainer(new ItemStack(Items.DIAMOND, 10));
		SimpleContainer playerInventory = playerInventory(new ItemStack(Items.DIAMOND, 60), ItemStack.EMPTY);
		TestMenu menu = new TestMenu(7);
		menu.addContainerSlots(container);
		menu.addPlayerSlots(playerInventory);

		int moved = ServerBookmarkPullTransfer.pull(
			menu,
			7,
			playerInventory,
			null,
			List.of(new BookmarkPullTarget(new ItemStack(Items.DIAMOND), 8))
		);

		assertEquals(8, moved);
		assertEquals(2, container.getItem(0).getCount());
		assertEquals(64, playerInventory.getItem(0).getCount());
		assertEquals(4, playerInventory.getItem(1).getCount());
	}

	@Test
	public void doesNotUsePlayerInventorySlotsAsSources() {
		SimpleContainer container = new SimpleContainer(ItemStack.EMPTY);
		SimpleContainer playerInventory = playerInventory(new ItemStack(Items.DIAMOND, 10), ItemStack.EMPTY);
		TestMenu menu = new TestMenu(7);
		menu.addContainerSlots(container);
		menu.addPlayerSlots(playerInventory);

		int moved = ServerBookmarkPullTransfer.pull(
			menu,
			7,
			playerInventory,
			null,
			List.of(new BookmarkPullTarget(new ItemStack(Items.DIAMOND), 5))
		);

		assertEquals(0, moved);
		assertEquals(10, playerInventory.getItem(0).getCount());
		assertEquals(true, playerInventory.getItem(1).isEmpty());
	}

	@Test
	public void capsTransferAtPlayerInventoryCapacity() {
		SimpleContainer container = new SimpleContainer(new ItemStack(Items.DIAMOND, 10));
		SimpleContainer playerInventory = playerInventory(new ItemStack(Items.DIAMOND, 63));
		TestMenu menu = new TestMenu(7);
		menu.addContainerSlots(container);
		menu.addPlayerSlots(playerInventory);

		int moved = ServerBookmarkPullTransfer.pull(
			menu,
			7,
			playerInventory,
			null,
			List.of(new BookmarkPullTarget(new ItemStack(Items.DIAMOND), 5))
		);

		assertEquals(1, moved);
		assertEquals(9, container.getItem(0).getCount());
		assertEquals(64, playerInventory.getItem(0).getCount());
	}

	@Test
	public void rejectsMismatchedContainerId() {
		SimpleContainer container = new SimpleContainer(new ItemStack(Items.DIAMOND, 10));
		SimpleContainer playerInventory = playerInventory(ItemStack.EMPTY);
		TestMenu menu = new TestMenu(7);
		menu.addContainerSlots(container);
		menu.addPlayerSlots(playerInventory);

		int moved = ServerBookmarkPullTransfer.pull(
			menu,
			8,
			playerInventory,
			null,
			List.of(new BookmarkPullTarget(new ItemStack(Items.DIAMOND), 5))
		);

		assertEquals(0, moved);
		assertEquals(10, container.getItem(0).getCount());
		assertEquals(true, playerInventory.getItem(0).isEmpty());
	}

	private static SimpleContainer playerInventory(ItemStack... stacks) {
		SimpleContainer inventory = new SimpleContainer(stacks.length);
		for (int i = 0; i < stacks.length; i++) {
			inventory.setItem(i, stacks[i]);
		}
		return inventory;
	}

	private static final class TestMenu extends AbstractContainerMenu {
		private TestMenu(int containerId) {
			super(null, containerId);
		}

		private void addContainerSlots(Container container) {
			for (int i = 0; i < container.getContainerSize(); i++) {
				addSlot(new Slot(container, i, 0, 0));
			}
		}

		private void addPlayerSlots(Container playerInventory) {
			for (int i = 0; i < playerInventory.getContainerSize(); i++) {
				addSlot(new Slot(playerInventory, i, 0, 0));
			}
		}

		@Override
		public ItemStack quickMoveStack(Player player, int index) {
			return ItemStack.EMPTY;
		}

		@Override
		public boolean stillValid(Player player) {
			return true;
		}
	}
}
