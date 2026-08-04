package mezz.jei.gui.bookmarks;

import java.util.List;

public final class BookmarkRowLayout {
	private BookmarkRowLayout() {
	}

	public static boolean isRowStart(int position, int columns, List<Integer> usableColumnsPerRow) {
		return position == rowStart(position, columns, usableColumnsPerRow);
	}

	public static int rowStart(int position, int columns, List<Integer> usableColumnsPerRow) {
		int cumulative = 0;
		for (int usable : usableColumnsPerRow) {
			if (position < cumulative + usable) {
				return cumulative;
			}
			cumulative += usable;
		}
		return columns > 0 ? position - position % columns : position;
	}

	public static int nextRowStart(int position, int columns, List<Integer> usableColumnsPerRow) {
		int cumulative = 0;
		for (int usable : usableColumnsPerRow) {
			if (position < cumulative + usable) {
				return cumulative + usable;
			}
			cumulative += usable;
		}
		return columns > 0 ? position + columns - position % columns : position + 1;
	}

	public static int above(int position, int columns, List<Integer> usableColumnsPerRow) {
		int cumulative = 0;
		int previousRowStart = 0;
		int previousRowUsable = 0;
		for (int usable : usableColumnsPerRow) {
			if (position < cumulative + usable) {
				int column = position - cumulative;
				return column < previousRowUsable ? previousRowStart + column : -1;
			}
			previousRowStart = cumulative;
			previousRowUsable = usable;
			cumulative += usable;
		}
		return columns > 0 && position >= columns ? position - columns : -1;
	}

	public static int below(int position, int columns, List<Integer> usableColumnsPerRow) {
		int cumulative = 0;
		for (int i = 0; i < usableColumnsPerRow.size(); i++) {
			int usable = usableColumnsPerRow.get(i);
			if (position < cumulative + usable) {
				int column = position - cumulative;
				if (i + 1 < usableColumnsPerRow.size() && column < usableColumnsPerRow.get(i + 1)) {
					return cumulative + usable + column;
				}
				return -1;
			}
			cumulative += usable;
		}
		return columns > 0 ? position + columns : -1;
	}
}
