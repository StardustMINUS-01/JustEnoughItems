package mezz.jei.gui.bookmarks;

import mezz.jei.common.gui.CandidateTooltipWindow;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public final class BookmarkPermutationTooltipState {
	private @Nullable Object sourceKey;
	private List<BookmarkIngredientKey> candidates = List.of();
	private int windowStart;

	public int updateStart(int bookmarkIndex, List<BookmarkIngredientKey> candidates, int selectedIndex) {
		return updateStart(Integer.valueOf(bookmarkIndex), candidates, selectedIndex);
	}

	public int updateStart(Object sourceKey, List<BookmarkIngredientKey> candidates, int selectedIndex) {
		if (!Objects.equals(sourceKey, this.sourceKey) || !this.candidates.equals(candidates)) {
			this.sourceKey = sourceKey;
			this.candidates = candidates;
			this.windowStart = 0;
		}
		this.windowStart = CandidateTooltipWindow.updateStart(candidates.size(), selectedIndex, windowStart);
		return windowStart;
	}
}
