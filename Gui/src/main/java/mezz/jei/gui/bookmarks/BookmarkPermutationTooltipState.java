package mezz.jei.gui.bookmarks;

import mezz.jei.common.gui.CandidateTooltipWindow;

import java.util.List;
import java.util.Objects;

public final class BookmarkPermutationTooltipState {
	private Object sourceKey;
	private List<BookmarkIngredientKey> candidates = List.of();
	private int windowStart;

	public int updateStart(int bookmarkIndex, List<BookmarkIngredientKey> candidates, int selectedIndex) {
		return updateStart((Object) bookmarkIndex, candidates, selectedIndex);
	}

	public int updateStart(Object sourceKey, List<BookmarkIngredientKey> candidates, int selectedIndex) {
		boolean sameSource = sourceKey == this.sourceKey || Objects.equals(sourceKey, this.sourceKey);
		boolean sameCandidates = candidates == this.candidates || this.candidates.equals(candidates);
		this.sourceKey = sourceKey;
		this.candidates = candidates;
		if (!(sameSource && sameCandidates)) {
			this.windowStart = 0;
		}
		this.windowStart = CandidateTooltipWindow.updateStart(candidates.size(), selectedIndex, windowStart);
		return windowStart;
	}
}
