package mezz.jei.gui.bookmarks;

import mezz.jei.common.gui.CandidateTooltipWindow;

import java.util.List;

public final class BookmarkPermutationTooltipState {
	private int bookmarkIndex = -1;
	private List<BookmarkIngredientKey> candidates = List.of();
	private int windowStart;

	public int updateStart(int bookmarkIndex, List<BookmarkIngredientKey> candidates, int selectedIndex) {
		if (bookmarkIndex != this.bookmarkIndex || !this.candidates.equals(candidates)) {
			this.bookmarkIndex = bookmarkIndex;
			this.candidates = candidates;
			this.windowStart = 0;
		}
		this.windowStart = CandidateTooltipWindow.updateStart(candidates.size(), selectedIndex, windowStart);
		return windowStart;
	}
}
