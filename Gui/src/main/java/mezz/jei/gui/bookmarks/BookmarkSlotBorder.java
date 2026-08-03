package mezz.jei.gui.bookmarks;

/**
 * Border frame for a bookmark slot, derived from GTNH NEI BookmarkGridGenerator.
 * Each side is drawn as an independent 1px line when the neighbouring slot does
 * not share the same border key, so all marked slots merge into one rectangle.
 */
public record BookmarkSlotBorder(
	int color,
	boolean left,
	boolean right,
	boolean top,
	boolean bottom
) {
	public static final int GROUP_NONE_COLOR = 0xFF666666;
	public static final int GROUP_CHAIN_COLOR = 0xFF4FA3FF;
	public static final int RECIPE_COLOR = 0x99A033A0;
}
