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
			BookmarkViewMode.TODO_LIST,
			true,
			true,
			Set.of(ResourceLocation.parse("test:plate"))
		);

		String serialized = BookmarkGroupConfigSerializer.serializeGroup(group);
		BookmarkGroup decoded = BookmarkGroupConfigSerializer.deserializeGroup(serialized).orElseThrow();

		Assertions.assertEquals(group, decoded);
		Assertions.assertTrue(serialized.contains("\"viewMode\":\"TODO_LIST\""));
		Assertions.assertTrue(serialized.contains("\"collapsed\":true"));
		Assertions.assertFalse(serialized.contains("expandedViewMode"));
		Assertions.assertFalse(serialized.contains("\"newLine\""));
		Assertions.assertFalse(serialized.contains("\"resultOnly\""));
	}

}
