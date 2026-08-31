package mezz.jei.gui.recipes.navigation;

public enum RecipeNavigationDirection {
	BACK("back"),
	FORWARD("forward");

	private final String translationKey;

	RecipeNavigationDirection(String translationKey) {
		this.translationKey = translationKey;
	}

	String getTranslationKey() {
		return translationKey;
	}
}
