package mezz.jei.gui.recipes.filtering;

public enum RecipeFilterMode {
	ALL,
	PREFERRED,
	NOT_PREFERRED;

	public RecipeFilterMode next() {
		return switch (this) {
			case ALL -> PREFERRED;
			case PREFERRED -> NOT_PREFERRED;
			case NOT_PREFERRED -> ALL;
		};
	}
}
