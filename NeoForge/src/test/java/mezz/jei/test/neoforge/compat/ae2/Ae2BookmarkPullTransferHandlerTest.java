package mezz.jei.test.neoforge.compat.ae2;

import mezz.jei.common.bookmarks.BookmarkPullTarget;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.chain.BookmarkExternalStorageSnapshots;
import mezz.jei.neoforge.compat.ae2.Ae2BookmarkPullTransferHandler;
import mezz.jei.neoforge.compat.ae2.Ae2BookmarkStorageSnapshotProvider;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Ae2BookmarkPullTransferHandlerTest {
	@BeforeAll
	public static void setup() {
		LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void extractsOnlyAmountThatFitsPlayerInventory() {
		TestMenu menu = new TestMenu(7);
		SimpleContainer playerInventory = new SimpleContainer(new ItemStack(Items.DIAMOND, 63));
		FakeStorageAccess access = new FakeStorageAccess(menu);
		Ae2BookmarkPullTransferHandler handler = new Ae2BookmarkPullTransferHandler(access);

		OptionalInt moved = handler.pull(
			menu,
			7,
			playerInventory,
			null,
			List.of(new BookmarkPullTarget(new ItemStack(Items.DIAMOND), 5))
		);

		assertTrue(moved.isPresent());
		assertEquals(1, moved.getAsInt());
		assertEquals(1, access.lastExtractAmount);
		assertEquals(64, playerInventory.getItem(0).getCount());
	}

	@Test
	public void doesNotHandleNonAe2Menus() {
		TestMenu ae2Menu = new TestMenu(7);
		TestMenu otherMenu = new TestMenu(7);
		SimpleContainer playerInventory = new SimpleContainer(ItemStack.EMPTY);
		Ae2BookmarkPullTransferHandler handler = new Ae2BookmarkPullTransferHandler(new FakeStorageAccess(ae2Menu));

		OptionalInt moved = handler.pull(
			otherMenu,
			7,
			playerInventory,
			null,
			List.of(new BookmarkPullTarget(new ItemStack(Items.DIAMOND), 5))
		);

		assertTrue(moved.isEmpty());
	}

	@Test
	public void snapshotProviderReadsStoredItemEntriesFromAe2ClientRepo() {
		TestMenu menu = new TestMenu(7);
		BookmarkIngredientKey diamondKey = new BookmarkIngredientKey("test:item", "diamond");
		Ae2BookmarkStorageSnapshotProvider provider = new Ae2BookmarkStorageSnapshotProvider(
			currentMenu -> currentMenu == menu ?
				Optional.of(List.of(new BookmarkExternalStorageSnapshots.Entry(new ItemStack(Items.DIAMOND), 9))) :
				Optional.empty()
		);

		var snapshot = provider.scan(menu, new Object(), stack ->
			stack.is(Items.DIAMOND) ? Optional.of(diamondKey) : Optional.empty()
		).orElseThrow();

		assertEquals(9L, snapshot.amounts().get(diamondKey));
		assertEquals(Items.DIAMOND, snapshot.representatives().get(diamondKey).getItem());
		assertTrue(provider.scan(new TestMenu(8), new Object(), stack -> Optional.empty()).isEmpty());
	}

	private static final class FakeStorageAccess implements Ae2BookmarkPullTransferHandler.StorageAccess {
		private final AbstractContainerMenu ae2Menu;
		private int lastExtractAmount;

		private FakeStorageAccess(AbstractContainerMenu ae2Menu) {
			this.ae2Menu = ae2Menu;
		}

		@Override
		public boolean isStorageMenu(AbstractContainerMenu menu) {
			return menu == ae2Menu;
		}

		@Override
		public boolean canInteract(AbstractContainerMenu menu) {
			return true;
		}

		@Override
		public Object createItemKey(ItemStack stack) {
			return stack.is(Items.DIAMOND) ? Items.DIAMOND : null;
		}

		@Override
		public long extract(AbstractContainerMenu menu, Object itemKey, long amount) {
			lastExtractAmount = (int) amount;
			return amount;
		}
	}

	private static final class TestMenu extends AbstractContainerMenu {
		private TestMenu(int containerId) {
			super(null, containerId);
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
