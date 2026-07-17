package mezz.jei.test.gui.config;

import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.config.BookmarkGroupConfigSerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class BookmarkGroupConfigSerializerTest {
	@Test
	public void catalystTypeRoundTripsWithoutLegacyBoolean() {
		BookmarkItemMetadata catalyst = new BookmarkItemMetadata(
			"group",
			BookmarkItemType.CATALYST,
			1,
			3,
			BookmarkItemMetadata.CHANCE_FULL,
			null,
			null,
			Set.of()
		);

		String serialized = BookmarkGroupConfigSerializer.serializeBookmarkMetadata(catalyst);
		BookmarkItemMetadata decoded = BookmarkGroupConfigSerializer.deserializeBookmarkMetadata(serialized).orElseThrow();
		BookmarkItemMetadata legacy = BookmarkGroupConfigSerializer.deserializeBookmarkMetadata(
			BookmarkGroupConfigSerializer.serializeBookmarkGroupId(BookmarkGroupManager.DEFAULT_GROUP_ID)
		).orElseThrow();

		Assertions.assertEquals(BookmarkItemType.CATALYST, decoded.type());
		Assertions.assertEquals(BookmarkItemType.ITEM, legacy.type());
		Assertions.assertFalse(serialized.contains("\"catalyst\""));
	}
}
