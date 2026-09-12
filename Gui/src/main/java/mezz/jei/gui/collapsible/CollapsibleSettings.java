package mezz.jei.gui.collapsible;

import mezz.jei.common.config.CollapsibleColorConfig;

/**
 * Optional visual settings for collapsible group slots.
 */
public record CollapsibleSettings(int collapsedColor, int expandedColor) {
	public static final int DEFAULT_COLLAPSED_COLOR = CollapsibleColorConfig.DEFAULT_COLOR;
	public static final int DEFAULT_EXPANDED_COLOR = CollapsibleColorConfig.DEFAULT_COLOR;
	public static final CollapsibleSettings DEFAULT = new CollapsibleSettings(
		DEFAULT_COLLAPSED_COLOR,
		DEFAULT_EXPANDED_COLOR
	);

	public static CollapsibleSettings fromConfig() {
		return new CollapsibleSettings(
			CollapsibleColorConfig.getCollapsedColor().getValue(),
			CollapsibleColorConfig.getExpandedColor().getValue()
		);
	}
}
