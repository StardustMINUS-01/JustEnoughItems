package mezz.jei.gui.recipes.filtering;

public enum RecipeFilterMode {
	DEFAULT,
	ALL,
	PREFERRED,
	NOT_PREFERRED,
	DISABLED;

	public boolean filtersPreference() {
		return this == PREFERRED || this == NOT_PREFERRED;
	}

	public boolean isUnrestricted() {
		return this == ALL || this == DEFAULT && RecipeCategoryPreferences.get().disabled().isEmpty();
	}

}
