package mezz.jei.gui.bookmarks;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.common.Internal;
import mezz.jei.gui.recipes.IIngredientCandidateSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.Optional;

public final class BookmarkCandidateSource implements IIngredientCandidateSource {
	private final BookmarkList bookmarks;
	private final Screen screen = Minecraft.getInstance().screen;
	private IBookmark bookmark;
	private long version;

	public BookmarkCandidateSource(BookmarkList bookmarks, IBookmark bookmark) {
		this.bookmarks = bookmarks;
		this.bookmark = bookmark;
		this.version = bookmarks.getChangeVersion();
	}

	@Override
	public Optional<ITypedIngredient<?>> getSelectedIngredient() {
		return Optional.ofNullable(bookmark.getElement().getTypedIngredient());
	}

	@Override
	public boolean isValid() {
		return Minecraft.getInstance().screen == screen && bookmarks.getChangeVersion() == version;
	}

	@Override
	public boolean canSelect() { return true; }

	boolean isFor(IBookmark bookmark) {
		return this.bookmark == bookmark;
	}

	void replaceBookmark(IBookmark original, IBookmark replacement) {
		if (isFor(original)) {
			bookmark = replacement;
			version = bookmarks.getChangeVersion();
		}
	}

	@Override
	public boolean select(ITypedIngredient<?> ingredient, boolean synchronize) {
		if (!isValid()) {
			return false;
		}
		var key = BookmarkItemMetadataFactory.createPermutationKey(ingredient, Internal.getJeiRuntime().getIngredientManager());
		if (!bookmarks.cycleBookmarkPermutation(bookmark, 0)) {
			return false;
		}
		version = bookmarks.getChangeVersion();
		return key != null;
	}
}
