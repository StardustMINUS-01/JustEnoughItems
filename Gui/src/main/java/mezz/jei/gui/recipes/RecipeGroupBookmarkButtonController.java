package mezz.jei.gui.recipes;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.common.Internal;
import mezz.jei.common.network.packets.PacketShareRecipe;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
import org.jetbrains.annotations.Nullable;

final class RecipeGroupBookmarkButtonController extends RecipeBookmarkButtonController {
	private final BookmarkList bookmarks;
	private final @Nullable IRecipeLayoutDrawable<?> recipeLayout;

	RecipeGroupBookmarkButtonController(BookmarkList bookmarks, IRecipeLayoutDrawable<?> recipeLayout, @Nullable IBookmark recipeBookmark) {
		super(bookmarks, recipeBookmark);
		this.bookmarks = bookmarks;
		this.recipeLayout = recipeBookmark == null ? null : recipeLayout;
	}

	@Override
	public void getTooltips(ITooltipBuilder tooltip) {
		super.getTooltips(tooltip);
		if (recipeLayout != null && Internal.getServerConnection().canSendPacket(PacketShareRecipe.TYPE)) {
			tooltip.addKeyUsageComponent("jei.tooltip.recipe.share", Internal.getKeyMappings().getRightClick());
		}
	}

	@Override
	public boolean onPress(IJeiUserInput input) {
		if (recipeLayout == null) {
			return false;
		}
		if (!input.isSimulate()) {
			bookmarks.toggleRecipeBookmark(recipeLayout, false);
		}
		return true;
	}
}
