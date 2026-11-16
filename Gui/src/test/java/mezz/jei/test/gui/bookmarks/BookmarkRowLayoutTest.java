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
	public void repeatsExcludedRowWidthsAcrossPages() {
		var layout = BookmarkRowLayout.RowLayout.create(6, List.of(6, 3, 3));
		for (int page = 0; page < 3; page++) {
			int start = page * 12;
			Assertions.assertEquals(start, BookmarkRowLayout.rowStart(start + 5, layout));
			Assertions.assertEquals(start + 6, BookmarkRowLayout.rowStart(start + 8, layout));
			Assertions.assertEquals(start + 9, BookmarkRowLayout.rowStart(start + 11, layout));
			Assertions.assertEquals(start + 9, BookmarkRowLayout.nextRowStart(start + 6, layout));
			Assertions.assertEquals(start + 12, BookmarkRowLayout.below(start + 9, layout));
			Assertions.assertEquals(page == 0 ? -1 : start - 3, BookmarkRowLayout.above(start, layout));
		}
	}
}
