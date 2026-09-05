package mezz.jei.test.bookmarks;

import mezz.jei.common.bookmarks.BookmarkPullTarget;
import mezz.jei.common.bookmarks.ServerBookmarkExternalStoragePull;
import mezz.jei.common.bookmarks.ServerBookmarkPullTransfer;
import mezz.jei.common.bookmarks.ServerBookmarkPullTransfers;
import net.minecraft.SharedConstants;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ServerBookmarkPullTransferTest {
	@BeforeAll
	public static void setup() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void pullsRequestedItemsFromContainerSlotsIntoPlayerInventory() {
		PullFixture fixture = PullFixture.create(
			new ItemStack(Items.DIAMOND, 10),
			new ItemStack(Items.DIAMOND, 60),
			ItemStack.EMPTY
		);

		int moved = fixture.pull(7, 8);

		assertEquals(8, moved);
		assertEquals(2, fixture.container().getItem(0).getCount());
		assertEquals(64, fixture.playerInventory().getItem(0).getCount());
		assertEquals(4, fixture.playerInventory().getItem(1).getCount());
	}

	@Test
	public void doesNotUsePlayerInventorySlotsAsSources() {
		PullFixture fixture = PullFixture.create(ItemStack.EMPTY, new ItemStack(Items.DIAMOND, 10), ItemStack.EMPTY);

		int moved = fixture.pull(7, 5);

		assertEquals(0, moved);
		assertEquals(10, fixture.playerInventory().getItem(0).getCount());
		assertEquals(true, fixture.playerInventory().getItem(1).isEmpty());
	}

	@Test
	public void capsTransferAtPlayerInventoryCapacity() {
		PullFixture fixture = PullFixture.create(new ItemStack(Items.DIAMOND, 10), new ItemStack(Items.DIAMOND, 63));

		int moved = fixture.pull(7, 5);

		assertEquals(1, moved);
		assertEquals(9, fixture.container().getItem(0).getCount());
		assertEquals(64, fixture.playerInventory().getItem(0).getCount());
	}

	@Test
	public void rejectsMismatchedContainerId() {
		PullFixture fixture = PullFixture.create(new ItemStack(Items.DIAMOND, 10), ItemStack.EMPTY);

		int moved = fixture.pull(8, 5);

		assertEquals(0, moved);
		assertEquals(10, fixture.container().getItem(0).getCount());
		assertEquals(true, fixture.playerInventory().getItem(0).isEmpty());
	}

	@Test
	public void doesNotUseArmorOrOffhandSlots() {
		SimpleContainer container = new SimpleContainer(new ItemStack(Items.DIAMOND, 10));
		SimpleContainer playerInventory = new SimpleContainer(41);
		for (int i = 0; i < 36; i++) {
			playerInventory.setItem(i, new ItemStack(Items.DIAMOND, 64));
		}
		TestMenu menu = new TestMenu(7);
		menu.addSlots(container);
		menu.addSlots(playerInventory);

		int moved = ServerBookmarkPullTransfer.pull(
			menu,
			7,
			playerInventory,
			null,
			List.of(new BookmarkPullTarget(new ItemStack(Items.DIAMOND), 10))
		);

		assertEquals(0, moved);
		for (int i = 36; i < 41; i++) {
			assertEquals(true, playerInventory.getItem(i).isEmpty());
		}
	}

	@Test
	public void externalHandlerConsumesCurrentMenuBeforeContainerFallback() throws Exception {
		PullFixture fixture = PullFixture.create(new ItemStack(Items.DIAMOND, 10), ItemStack.EMPTY);

		try (AutoCloseable ignored = ServerBookmarkPullTransfers.registerHandler(
			(currentMenu, containerId, currentPlayerInventory, player, targets) -> {
				if (currentMenu == fixture.menu() && containerId == 7 && targets.size() == 1) {
					currentPlayerInventory.setItem(0, new ItemStack(Items.EMERALD, 3));
					return OptionalInt.of(3);
				}
				return OptionalInt.empty();
			}
		)) {
			int moved = ServerBookmarkPullTransfers.pull(
				fixture.menu(),
				7,
				fixture.playerInventory(),
				null,
				pullTargets(5)
			);

			assertEquals(3, moved);
			assertEquals(10, fixture.container().getItem(0).getCount());
			assertEquals(3, fixture.playerInventory().getItem(0).getCount());
			assertEquals(Items.EMERALD, fixture.playerInventory().getItem(0).getItem());
		}
	}

	@Test
	public void fallsBackToOrdinaryContainerPullWhenNoExternalHandlerHandlesMenu() {
		PullFixture fixture = PullFixture.create(new ItemStack(Items.DIAMOND, 10), ItemStack.EMPTY);

		int moved = ServerBookmarkPullTransfers.pull(
			fixture.menu(),
			7,
			fixture.playerInventory(),
			null,
			pullTargets(5)
		);

		assertEquals(5, moved);
		assertEquals(5, fixture.container().getItem(0).getCount());
		assertEquals(5, fixture.playerInventory().getItem(0).getCount());
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

	private static List<BookmarkPullTarget> pullTargets(int amount) {
		return List.of(new BookmarkPullTarget(new ItemStack(Items.DIAMOND), amount));
	}

	private record PullFixture(SimpleContainer container, SimpleContainer playerInventory, TestMenu menu) {
		private static PullFixture create(ItemStack containerStack, ItemStack... playerStacks) {
			SimpleContainer container = new SimpleContainer(containerStack);
			SimpleContainer playerInventory = ServerBookmarkPullTransferTest.playerInventory(playerStacks);
			TestMenu menu = new TestMenu(7);
			menu.addSlots(container);
			menu.addSlots(playerInventory);
			return new PullFixture(container, playerInventory, menu);
		}

		private int pull(int containerId, int amount) {
			return ServerBookmarkPullTransfer.pull(
				menu,
				containerId,
				playerInventory,
				null,
				pullTargets(amount)
			);
		}
	}

	private static final class TestMenu extends AbstractContainerMenu {
		private TestMenu(int containerId) {
			super(null, containerId);
		}

		private void addSlots(Container container) {
			for (int i = 0; i < container.getContainerSize(); i++) {
				addSlot(new Slot(container, i, 0, 0));
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
