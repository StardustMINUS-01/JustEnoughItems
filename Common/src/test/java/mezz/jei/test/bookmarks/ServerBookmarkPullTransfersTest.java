package mezz.jei.test.bookmarks;

import mezz.jei.common.bookmarks.BookmarkPullTarget;
import mezz.jei.common.bookmarks.ServerBookmarkExternalStoragePull;
import mezz.jei.common.bookmarks.ServerBookmarkPullTransfers;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ServerBookmarkPullTransfersTest {
	@BeforeAll
	public static void setup() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void externalHandlerConsumesCurrentMenuBeforeContainerFallback() throws Exception {
		SimpleContainer container = new SimpleContainer(new ItemStack(Items.DIAMOND, 10));
		SimpleContainer playerInventory = playerInventory(ItemStack.EMPTY);
		TestMenu menu = new TestMenu(7);
		menu.addContainerSlots(container);
		menu.addPlayerSlots(playerInventory);

		try (AutoCloseable ignored = ServerBookmarkPullTransfers.registerHandler(
			(currentMenu, containerId, currentPlayerInventory, player, targets) -> {
				if (currentMenu == menu && containerId == 7 && targets.size() == 1) {
					currentPlayerInventory.setItem(0, new ItemStack(Items.EMERALD, 3));
					return OptionalInt.of(3);
				}
				return OptionalInt.empty();
			}
		)) {
			int moved = ServerBookmarkPullTransfers.pull(
				menu,
				7,
				playerInventory,
				null,
				List.of(new BookmarkPullTarget(new ItemStack(Items.DIAMOND), 5))
			);

			assertEquals(3, moved);
			assertEquals(10, container.getItem(0).getCount());
			assertEquals(3, playerInventory.getItem(0).getCount());
			assertEquals(Items.EMERALD, playerInventory.getItem(0).getItem());
		}
	}

	@Test
	public void fallsBackToOrdinaryContainerPullWhenNoExternalHandlerHandlesMenu() {
		SimpleContainer container = new SimpleContainer(new ItemStack(Items.DIAMOND, 10));
		SimpleContainer playerInventory = playerInventory(ItemStack.EMPTY);
		TestMenu menu = new TestMenu(7);
		menu.addContainerSlots(container);
		menu.addPlayerSlots(playerInventory);

		int moved = ServerBookmarkPullTransfers.pull(
			menu,
			7,
			playerInventory,
			null,
			List.of(new BookmarkPullTarget(new ItemStack(Items.DIAMOND), 5))
		);

		assertEquals(5, moved);
		assertEquals(5, container.getItem(0).getCount());
		assertEquals(5, playerInventory.getItem(0).getCount());
	}

	@Test
	public void externalStoragePullCapsExtractionAtPlayerInventoryCapacity() {
		SimpleContainer playerInventory = playerInventory(new ItemStack(Items.DIAMOND, 63));
		TestMenu menu = new TestMenu(7);
		List<Integer> requestedAmounts = new ArrayList<>();

		int moved = ServerBookmarkExternalStoragePull.pull(
			menu,
			7,
			playerInventory,
			List.of(new BookmarkPullTarget(new ItemStack(Items.DIAMOND), 5)),
			(target, amount) -> {
				requestedAmounts.add(amount);
				ItemStack extracted = target.itemStack().copy();
				extracted.setCount(amount);
				return extracted;
			}
		);

		assertEquals(List.of(1), requestedAmounts);
		assertEquals(1, moved);
		assertEquals(64, playerInventory.getItem(0).getCount());
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
