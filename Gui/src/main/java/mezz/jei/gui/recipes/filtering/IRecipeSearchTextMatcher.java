package mezz.jei.gui.recipes.filtering;

@FunctionalInterface
public interface IRecipeSearchTextMatcher {
	IRecipeSearchTextMatcher DEFAULT = String::contains;

	boolean contains(String text, String token);
}
