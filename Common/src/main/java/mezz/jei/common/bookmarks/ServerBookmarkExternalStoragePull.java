package mezz.jei.common.bookmarks;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class ServerBookmarkExternalStoragePull {
	private ServerBookmarkExternalStoragePull() {
	}

	public static int pull(
		AbstractContainerMenu menu,
		int containerId,
		Container playerInventory,
		List<BookmarkPullTarget> targets,
		Extractor extractor
	) {
		if (menu.containerId != containerId || targets.isEmpty()) {
			return 0;
		}

		int moved = 0;
		for (BookmarkPullTarget target : targets) {
			if (target.isEmpty()) {
				continue;
			}
			int insertable = ServerBookmarkPullTransfer.getInsertableAmount(playerInventory, target.itemStack(), target.amount());
			if (insertable <= 0) {
				break;
			}
			ItemStack extracted = extractor.extract(target, insertable);
			if (extracted.isEmpty()) {
				continue;
			}
			int inserted = ServerBookmarkPullTransfer.insertIntoPlayerInventory(
				playerInventory,
				target.itemStack(),
				extracted.getCount()
			);
			moved += inserted;
		}

		if (moved > 0) {
			playerInventory.setChanged();
			menu.broadcastChanges();
		}
		return moved;
	}

	@FunctionalInterface
	public interface Extractor {
		ItemStack extract(BookmarkPullTarget target, int amount);
	}
}
