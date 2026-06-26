package mezz.jei.gui.bookmarks.chain;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BookmarkContainerPullExecutor {
	private BookmarkContainerPullExecutor() {
	}

	public static boolean pull(Request request, BookmarkContainerHandler handler) {
		BookmarkPullPlanner.BookmarkPullPlan plan = BookmarkPullPlanner.plan(
			request.inputs(),
			request.collapsedRecipes(),
			request.playerInventory(),
			handler.getStorageAmounts(),
			request.freeSlots(),
			request.maxStackSize(),
			request.shift()
		);
		if (plan.amounts().isEmpty()) {
			return false;
		}
		handler.pullBookmarkItemsFromContainer(plan);
		return true;
	}

	public record Request(
		List<RecipeChainInput> inputs,
		Set<ResourceLocation> collapsedRecipes,
		Map<BookmarkIngredientKey, Long> playerInventory,
		int freeSlots,
		int maxStackSize,
		boolean shift
	) {
	}
}
