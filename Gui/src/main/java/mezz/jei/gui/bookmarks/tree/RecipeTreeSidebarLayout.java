package mezz.jei.gui.bookmarks.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Full-width headings followed by wrapping rows of ingredient icons. */
public final class RecipeTreeSidebarLayout<T> {
	private static final int PITCH = 24;
	private final int width;
	private final int height;
	private final List<Cell<T>> cells;

	public RecipeTreeSidebarLayout(List<T> entries, int width, Predicate<T> isHeading) {
		this.width = Math.max(1, width);
		int columns = Math.max(1, this.width / PITCH);
		int column = 0, y = 0;
		List<Cell<T>> cells = new ArrayList<>(entries.size());
		for (T entry : entries) {
			if (isHeading.test(entry)) {
				if (column != 0) {
					y += PITCH;
					column = 0;
				}
				cells.add(new Cell<>(entry, 0, y, this.width, PITCH));
				y += PITCH;
			} else {
				cells.add(new Cell<>(entry, column * PITCH, y, Math.min(20, this.width), 20));
				if (++column == columns) {
					column = 0;
					y += PITCH;
				}
			}
		}
		this.height = y + (column == 0 ? 0 : PITCH);
		this.cells = List.copyOf(cells);
	}

	public int width() { return width; }
	public int height() { return height; }
	public List<Cell<T>> cells() { return cells; }

	public Optional<T> entryAt(double x, double y) {
		return cells.stream().filter(cell -> x >= cell.x() && x < cell.x() + cell.width() &&
			y >= cell.y() && y < cell.y() + cell.height())
			.map(Cell::entry).findFirst();
	}

	public record Cell<T>(T entry, int x, int y, int width, int height) {}
}
