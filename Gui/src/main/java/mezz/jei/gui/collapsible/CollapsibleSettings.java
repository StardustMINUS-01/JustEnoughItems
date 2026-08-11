package mezz.jei.gui.collapsible;

/**
 * Optional visual settings for collapsible group slots.
 * Defaults match the GTNH NEI collapsible item colors.
 */
public record CollapsibleSettings(int collapsedColor, int expandedColor) {
	public static final int DEFAULT_COLLAPSED_COLOR = 0x335555EE;
	public static final int DEFAULT_EXPANDED_COLOR = 0x335555EE;
	public static final CollapsibleSettings DEFAULT = new CollapsibleSettings(
		DEFAULT_COLLAPSED_COLOR,
		DEFAULT_EXPANDED_COLOR
	);
}
