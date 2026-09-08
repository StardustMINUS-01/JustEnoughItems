package mezz.jei.gui.bookmarks.chain;

public enum RecipeChainTooltipSectionType {
	OUTPUT,
	INPUT,
	MISSING,
	NEEDED,
	AVAILABLE,
	REMAINDER;

	public String translationKey() {
		return "jei.tooltip.bookmarks.group.recipe_chain." + switch (this) {
			case OUTPUT -> "output";
			case INPUT -> "input";
			case MISSING -> "missing_items";
			case NEEDED -> "needed";
			case AVAILABLE -> "available";
			case REMAINDER -> "remainder";
		};
	}
}
