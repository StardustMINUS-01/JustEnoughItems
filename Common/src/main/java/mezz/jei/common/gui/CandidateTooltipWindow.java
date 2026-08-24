package mezz.jei.common.gui;

public final class CandidateTooltipWindow {
	private static final int MAX_INGREDIENTS = 30;
	private static final int MAX_VISIBLE_CANDIDATES = MAX_INGREDIENTS - 1;

	private CandidateTooltipWindow() {
	}

	public static int updateStart(int candidateCount, int selectedIndex, int currentStart) {
		if (candidateCount <= MAX_INGREDIENTS || selectedIndex < 0 || selectedIndex >= candidateCount) {
			return 0;
		}
		int maximumStart = candidateCount - MAX_VISIBLE_CANDIDATES;
		int start = Math.max(0, Math.min(currentStart, maximumStart));
		if (selectedIndex < start) {
			return selectedIndex;
		}
		if (selectedIndex >= start + MAX_VISIBLE_CANDIDATES) {
			return selectedIndex - MAX_VISIBLE_CANDIDATES + 1;
		}
		return start;
	}

	public static int getVisibleCandidateCount(int candidateCount) {
		return candidateCount > MAX_INGREDIENTS ? MAX_VISIBLE_CANDIDATES : candidateCount;
	}
}
