package mezz.jei.gui.bookmarks;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BookmarkListProjectionTest {
	@Test
	public void defaultPlainBookmarkDoesNotNeedProjection() {
		BookmarkItemMetadata metadata = BookmarkItemMetadata.defaultForGroup(BookmarkGroupManager.DEFAULT_GROUP_ID);

		assertFalse(BookmarkList.needsProjectedElement(entry(metadata)));
	}

	@Test
	public void nonDefaultMetadataNeedsProjection() {
		BookmarkItemMetadata metadata = new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			BookmarkItemType.ITEM,
			1,
			4,
			BookmarkItemMetadata.CHANCE_FULL,
			null,
			null,
			Set.of()
		);

		assertTrue(BookmarkList.needsProjectedElement(entry(metadata)));
	}

	private static BookmarkDisplayEntry<Object> entry(BookmarkItemMetadata metadata) {
		return new BookmarkDisplayEntry<>(
			new Object(),
			0,
			metadata,
			BookmarkViewMode.DEFAULT,
			Optional.empty(),
			Optional.empty(),
			false,
			false
		);
	}
}
