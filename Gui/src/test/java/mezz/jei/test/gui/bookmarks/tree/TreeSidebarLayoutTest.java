package mezz.jei.test.gui.bookmarks.tree;

import mezz.jei.gui.bookmarks.tree.RecipeTreeSidebarLayout;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeSidebarLayoutTest {
	@Test
	void wrapsIngredients() {
		var layout = layout(72, "#output", "result", "#input", "a", "b", "c", "d");
		assertEquals(120, layout.height());
		assertCell(layout, "a", 0, 72);
		assertCell(layout, "b", 24, 72);
		assertCell(layout, "c", 48, 72);
		assertCell(layout, "d", 0, 96);
	}

	@Test
	void placesHeadings() {
		var layout = layout(48, "#machine", "#input", "a", "b", "#catalyst", "c");
		assertCell(layout, "#catalyst", 0, 72);
		assertCell(layout, "c", 0, 96);
		assertEquals(120, layout.height());
	}

	@Test
	void findsHoveredEntry() {
		var layout = layout(72, "#input", "a", "b");
		assertEquals("b", layout.entryAt(26, 26).orElseThrow());
		assertEquals("#input", layout.entryAt(50, 5).orElseThrow());
		assertTrue(layout.entryAt(22, 26).isEmpty());
		assertTrue(layout.entryAt(50, 26).isEmpty());
		assertTrue(layout.entryAt(-1, 26).isEmpty());
		assertTrue(layout.entryAt(2, 48).isEmpty());
	}

	@Test
	void computesHeight() {
		assertEquals(48, layout(96, "#input", "a", "b", "c", "d").height());
		assertEquals(72, layout(48, "#input", "a", "b", "c", "d").height());
		assertEquals(120, layout(24, "#input", "a", "b", "c", "d").height());
		assertEquals(0, layout(24).height());
		assertEquals(24, layout(0, "a").height());
	}

	private static RecipeTreeSidebarLayout<String> layout(int width, String... entries) {
		return new RecipeTreeSidebarLayout<>(List.of(entries), width, entry -> entry.startsWith("#"));
	}

	private static void assertCell(RecipeTreeSidebarLayout<String> layout, String entry, int x, int y) {
		var cell = layout.cells().stream().filter(value -> value.entry().equals(entry)).findFirst().orElseThrow();
		assertEquals(x, cell.x());
		assertEquals(y, cell.y());
	}
}
