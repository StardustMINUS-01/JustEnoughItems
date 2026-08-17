package mezz.jei.gui.bookmarks;

import java.util.List;

public final class BookmarkRowLayout {
	private BookmarkRowLayout() {
	}

	public static final class RowLayout {
		private final int columns;
		private final List<Integer> usableColumnsPerRow;
		private final int[] rowStarts;

		private RowLayout(int columns, List<Integer> usableColumnsPerRow) {
			this.columns = columns;
			this.usableColumnsPerRow = List.copyOf(usableColumnsPerRow);
			this.rowStarts = new int[this.usableColumnsPerRow.size()];
			int cumulative = 0;
			for (int i = 0; i < this.usableColumnsPerRow.size(); i++) {
				this.rowStarts[i] = cumulative;
				cumulative += this.usableColumnsPerRow.get(i);
			}
		}

		public static RowLayout create(int columns, List<Integer> usableColumnsPerRow) {
			return new RowLayout(columns, usableColumnsPerRow);
		}

		public int columns() {
			return columns;
		}
	}

	public static boolean isRowStart(int position, int columns, List<Integer> usableColumnsPerRow) {
		return isRowStart(position, RowLayout.create(columns, usableColumnsPerRow));
	}

	public static boolean isRowStart(int position, RowLayout layout) {
		return position == rowStart(position, layout);
	}

	public static int rowStart(int position, int columns, List<Integer> usableColumnsPerRow) {
		return rowStart(position, RowLayout.create(columns, usableColumnsPerRow));
	}

	public static int rowStart(int position, RowLayout layout) {
		int rowIndex = findRowIndex(position, layout.rowStarts);
		if (isWithinRow(position, rowIndex, layout)) {
			return layout.rowStarts[rowIndex];
		}
		return layout.columns > 0 ? position - position % layout.columns : position;
	}

	public static int nextRowStart(int position, int columns, List<Integer> usableColumnsPerRow) {
		return nextRowStart(position, RowLayout.create(columns, usableColumnsPerRow));
	}

	public static int nextRowStart(int position, RowLayout layout) {
		int rowIndex = findRowIndex(position, layout.rowStarts);
		if (isWithinRow(position, rowIndex, layout)) {
			return layout.rowStarts[rowIndex] + layout.usableColumnsPerRow.get(rowIndex);
		}
		return layout.columns > 0 ? position + layout.columns - position % layout.columns : position + 1;
	}

	public static boolean isRowEnd(int position, int columns, List<Integer> usableColumnsPerRow) {
		return isRowEnd(position, RowLayout.create(columns, usableColumnsPerRow));
	}

	public static boolean isRowEnd(int position, RowLayout layout) {
		return nextRowStart(position, layout) == position + 1;
	}

	public static int above(int position, int columns, List<Integer> usableColumnsPerRow) {
		return above(position, RowLayout.create(columns, usableColumnsPerRow));
	}

	public static int above(int position, RowLayout layout) {
		int rowIndex = findRowIndex(position, layout.rowStarts);
		if (isWithinRow(position, rowIndex, layout)) {
			int column = position - layout.rowStarts[rowIndex];
			if (rowIndex > 0) {
				if (column < layout.usableColumnsPerRow.get(rowIndex - 1)) {
					return layout.rowStarts[rowIndex - 1] + column;
				}
				return -1;
			}
			if (column < 0) {
				return column;
			}
			return -1;
		}
		return layout.columns > 0 && position >= layout.columns ? position - layout.columns : -1;
	}

	public static int below(int position, int columns, List<Integer> usableColumnsPerRow) {
		return below(position, RowLayout.create(columns, usableColumnsPerRow));
	}

	public static int below(int position, RowLayout layout) {
		int rowIndex = findRowIndex(position, layout.rowStarts);
		if (isWithinRow(position, rowIndex, layout)) {
			int column = position - layout.rowStarts[rowIndex];
			if (rowIndex + 1 < layout.rowStarts.length && column < layout.usableColumnsPerRow.get(rowIndex + 1)) {
				return layout.rowStarts[rowIndex] + layout.usableColumnsPerRow.get(rowIndex) + column;
			}
			return -1;
		}
		return layout.columns > 0 ? position + layout.columns : -1;
	}

	private static int findRowIndex(int position, int[] rowStarts) {
		if (position < 0) {
			return rowStarts.length == 0 ? -1 : 0;
		}
		return upperBound(rowStarts, position) - 1;
	}

	private static int upperBound(int[] values, int key) {
		int low = 0;
		int high = values.length;
		while (low < high) {
			int mid = (low + high) >>> 1;
			if (values[mid] <= key) {
				low = mid + 1;
			} else {
				high = mid;
			}
		}
		return low;
	}

	private static boolean isWithinRow(int position, int rowIndex, RowLayout layout) {
		return rowIndex >= 0 &&
			rowIndex < layout.rowStarts.length &&
			position < layout.rowStarts[rowIndex] + layout.usableColumnsPerRow.get(rowIndex);
	}
}
