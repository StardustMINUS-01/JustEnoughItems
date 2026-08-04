package mezz.jei.test.gui.config;

import mezz.jei.gui.bookmarks.BookmarkGroup;
import mezz.jei.gui.bookmarks.BookmarkViewMode;
import mezz.jei.gui.config.BookmarkGroupConfigSerializer;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class BookmarkGroupConfigSerializerTest {
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
