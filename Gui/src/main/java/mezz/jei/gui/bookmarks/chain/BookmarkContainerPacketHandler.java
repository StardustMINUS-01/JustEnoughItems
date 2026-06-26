package mezz.jei.gui.bookmarks.chain;

import mezz.jei.common.bookmarks.BookmarkPullTarget;
import mezz.jei.common.network.packets.PacketPullBookmarkItems;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class BookmarkContainerPacketHandler implements BookmarkContainerHandler {
	private final Map<BookmarkIngredientKey, Long> storageAmounts;
	private final Map<BookmarkIngredientKey, ItemStack> representatives;
	private final int containerId;
	private final Consumer<PacketPullBookmarkItems> packetSender;

	public BookmarkContainerPacketHandler(
		Map<BookmarkIngredientKey, Long> storageAmounts,
		Map<BookmarkIngredientKey, ItemStack> representatives,
		int containerId,
		Consumer<PacketPullBookmarkItems> packetSender
	) {
		this.storageAmounts = Map.copyOf(storageAmounts);
		this.representatives = Map.copyOf(representatives);
		this.containerId = containerId;
		this.packetSender = packetSender;
	}

	@Override
	public Map<BookmarkIngredientKey, Long> getStorageAmounts() {
		return storageAmounts;
	}

	@Override
	public void pullBookmarkItemsFromContainer(BookmarkPullPlanner.BookmarkPullPlan plan) {
		List<BookmarkPullTarget> targets = new ArrayList<>();
		for (Map.Entry<BookmarkIngredientKey, Long> entry : plan.amounts().entrySet()) {
			ItemStack representative = representatives.get(entry.getKey());
			if (representative == null || representative.isEmpty() || entry.getValue() <= 0) {
				continue;
			}
			targets.add(new BookmarkPullTarget(representative, saturatingInt(entry.getValue())));
		}
		if (!targets.isEmpty()) {
			packetSender.accept(new PacketPullBookmarkItems(containerId, targets));
		}
	}

	private static int saturatingInt(long amount) {
		return amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
	}
}
