package mezz.jei.gui.bookmarks;

/**
 * Display modes shared by regular groups and recipe chains.
 * <ul>
 *   <li>{@link #DEFAULT}: regular groups show all entries compactly, recipe chains show
 *       only the calculated results compactly (GTNH NEI default chain view).</li>
 *   <li>{@link #TODO_LIST}: entries wrap, recipe chains show one recipe per row.</li>
 *   <li>{@link #COLLAPSED}: the group collapses to a single row of results.</li>
 * </ul>
 */
public enum BookmarkViewMode {
	DEFAULT,
	TODO_LIST,
	COLLAPSED
}
