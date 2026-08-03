package mezz.jei.test.gui.config;

import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.BookmarkGroup;
import mezz.jei.gui.bookmarks.BookmarkViewMode;
import mezz.jei.gui.config.BookmarkGroupConfigSerializer;
import net.minecraft.resources.ResourceLocation;
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

	@Test
	public void groupFlagsRoundTrip() {
		BookmarkGroup group = new BookmarkGroup(
			"group_1",
			"Machines",
			BookmarkViewMode.COLLAPSED,
			BookmarkViewMode.TODO_LIST,
			true,
			Set.of(ResourceLocation.parse("test:plate"))
		);

		String serialized = BookmarkGroupConfigSerializer.serializeGroup(group);
		BookmarkGroup decoded = BookmarkGroupConfigSerializer.deserializeGroup(serialized).orElseThrow();

		Assertions.assertEquals(group, decoded);
		Assertions.assertTrue(serialized.contains("\"viewMode\":\"COLLAPSED\""));
		Assertions.assertTrue(serialized.contains("\"expandedViewMode\":\"TODO_LIST\""));
		Assertions.assertFalse(serialized.contains("\"newLine\""));
		Assertions.assertFalse(serialized.contains("\"resultOnly\""));
		Assertions.assertFalse(serialized.contains("collapsed\""));
	}

	@Test
	public void collapsedGroupWithoutExpandedViewModeDefaultsToExpanded() {
		BookmarkGroup decoded = BookmarkGroupConfigSerializer.deserializeGroup(
			"G:{\"id\":\"group_1\",\"title\":\"Machines\",\"viewMode\":\"COLLAPSED\",\"crafting\":false}"
		).orElseThrow();

		Assertions.assertEquals(BookmarkViewMode.COLLAPSED, decoded.viewMode());
		Assertions.assertEquals(BookmarkViewMode.DEFAULT, decoded.expandedViewMode());
		Assertions.assertEquals(BookmarkViewMode.DEFAULT, decoded.toggleCollapsed().viewMode());
	}
}
