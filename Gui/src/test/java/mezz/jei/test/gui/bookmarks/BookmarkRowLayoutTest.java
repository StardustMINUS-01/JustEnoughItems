package mezz.jei.test.gui.bookmarks;

import mezz.jei.gui.bookmarks.BookmarkRowLayout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class BookmarkRowLayoutTest {
	private static final int COLUMNS = 9;
	private static final List<Integer> USABLE_COLUMNS_PER_ROW = List.of(4, 4, 9);

	@Test
	public void rowStartAndEndFollowLShapedRows() {
		Assertions.assertTrue(BookmarkRowLayout.isRowStart(0, COLUMNS, USABLE_COLUMNS_PER_ROW));
		Assertions.assertFalse(BookmarkRowLayout.isRowStart(1, COLUMNS, USABLE_COLUMNS_PER_ROW));
		Assertions.assertTrue(BookmarkRowLayout.isRowStart(4, COLUMNS, USABLE_COLUMNS_PER_ROW));
		Assertions.assertTrue(BookmarkRowLayout.isRowStart(8, COLUMNS, USABLE_COLUMNS_PER_ROW));

		Assertions.assertTrue(BookmarkRowLayout.isRowEnd(3, COLUMNS, USABLE_COLUMNS_PER_ROW));
		Assertions.assertTrue(BookmarkRowLayout.isRowEnd(7, COLUMNS, USABLE_COLUMNS_PER_ROW));
		Assertions.assertTrue(BookmarkRowLayout.isRowEnd(16, COLUMNS, USABLE_COLUMNS_PER_ROW));
		Assertions.assertFalse(BookmarkRowLayout.isRowEnd(2, COLUMNS, USABLE_COLUMNS_PER_ROW));
		Assertions.assertFalse(BookmarkRowLayout.isRowEnd(8, COLUMNS, USABLE_COLUMNS_PER_ROW));
	}

	@Test
	public void aboveAndBelowRespectLShapedRows() {
		Assertions.assertEquals(-1, BookmarkRowLayout.above(0, COLUMNS, USABLE_COLUMNS_PER_ROW));
		Assertions.assertEquals(0, BookmarkRowLayout.above(4, COLUMNS, USABLE_COLUMNS_PER_ROW));
		Assertions.assertEquals(4, BookmarkRowLayout.above(8, COLUMNS, USABLE_COLUMNS_PER_ROW));
		Assertions.assertEquals(-1, BookmarkRowLayout.above(16, COLUMNS, USABLE_COLUMNS_PER_ROW));

		Assertions.assertEquals(4, BookmarkRowLayout.below(0, COLUMNS, USABLE_COLUMNS_PER_ROW));
		Assertions.assertEquals(7, BookmarkRowLayout.below(3, COLUMNS, USABLE_COLUMNS_PER_ROW));
		Assertions.assertEquals(11, BookmarkRowLayout.below(7, COLUMNS, USABLE_COLUMNS_PER_ROW));
		Assertions.assertEquals(17, BookmarkRowLayout.below(8, COLUMNS, USABLE_COLUMNS_PER_ROW));
		Assertions.assertEquals(-1, BookmarkRowLayout.below(16, COLUMNS, USABLE_COLUMNS_PER_ROW));
	}

	@Test
	public void repeatsExcludedRowWidthsAcrossThreePages() {
		var layout = BookmarkRowLayout.RowLayout.create(6, List.of(6, 3, 3));
		for (int page = 0; page < 3; page++) {
			int offset = page * 12;
			for (int local = 0; local < 12; local++) {
				int start = local < 6 ? 0 : local < 9 ? 6 : 9;
				int end = local < 6 ? 6 : local < 9 ? 9 : 12;
				Assertions.assertEquals(offset + start, BookmarkRowLayout.rowStart(offset + local, layout));
				Assertions.assertEquals(offset + end, BookmarkRowLayout.nextRowStart(offset + local, layout));
			}
			Assertions.assertEquals(offset + 6, BookmarkRowLayout.below(offset, layout));
			Assertions.assertEquals(-1, BookmarkRowLayout.below(offset + 5, layout));
			Assertions.assertEquals(offset + 12, BookmarkRowLayout.below(offset + 9, layout));
			Assertions.assertEquals(page == 0 ? -1 : offset - 3, BookmarkRowLayout.above(offset, layout));
		}
	}
}
