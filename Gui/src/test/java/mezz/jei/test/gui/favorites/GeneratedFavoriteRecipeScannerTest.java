package mezz.jei.test.gui.favorites;

import mezz.jei.gui.favorites.GeneratedFavoriteRecipeScanner;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRule;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import mezz.jei.gui.favorites.preferences.RecipePreferenceTarget;
import mezz.jei.gui.favorites.preferences.RecipePreferenceCandidate;
import mezz.jei.gui.favorites.preferences.RecipePreferenceIngredientInfo;
import mezz.jei.gui.input.FocusedRecipe;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class GeneratedFavoriteRecipeScannerTest {
	private static final ResourceLocation FINE_WIRES = ResourceLocation.fromNamespaceAndPath("c", "fine_wires");
	private static final ResourceLocation COBALT_FINE_WIRE = ResourceLocation.fromNamespaceAndPath("gtceu", "cobalt_fine_wire");
	private static final ResourceLocation WIREMILL = ResourceLocation.fromNamespaceAndPath("gtceu", "wiremill");

	@Test
	public void multipleRecipesCanResolveToRulePreferredFavorite() {
		RecipePreferenceRules rules = new RecipePreferenceRules(List.of(new RecipePreferenceRule(
			"fine wires",
			RecipePreferenceTarget.tag(FINE_WIRES),
			Optional.of(WIREMILL),
			List.of(),
			List.of(List.of("gtceu:wiremill/mill_*_wire_fine"))
		)));
		List<RecipePreferenceCandidate> candidates = List.of(
			recipe("gtceu:wiremill/mill_cobalt_wire_fine"),
			recipe("gtceu:wiremill/mill_cobalt_wire_to_fine_wire")
		);

		Optional<FocusedRecipe> selected = GeneratedFavoriteRecipeScanner.resolveGeneratedFavorite(
			target(),
			candidates,
			rules
		);

		Assertions.assertEquals(Optional.of(recipe("gtceu:wiremill/mill_cobalt_wire_fine").recipe()), selected);
	}

	@Test
	public void multipleRecipesStayUnresolvedWhenRulesDoNotSelectOne() {
		List<RecipePreferenceCandidate> candidates = List.of(
			recipe("gtceu:wiremill/mill_cobalt_wire_fine"),
			recipe("gtceu:wiremill/mill_cobalt_wire_to_fine_wire")
		);

		Optional<FocusedRecipe> selected = GeneratedFavoriteRecipeScanner.resolveGeneratedFavorite(
			target(),
			candidates,
			RecipePreferenceRules.EMPTY
		);

		Assertions.assertTrue(selected.isEmpty());
	}

	@Test
	public void uniqueRecipeStillResolvesWithoutPreferenceRules() {
		List<RecipePreferenceCandidate> candidates = List.of(
			recipe("gtceu:wiremill/mill_cobalt_wire_fine")
		);

		Optional<FocusedRecipe> selected = GeneratedFavoriteRecipeScanner.resolveGeneratedFavorite(
			Optional.empty(),
			candidates,
			RecipePreferenceRules.EMPTY
		);

		Assertions.assertEquals(Optional.of(recipe("gtceu:wiremill/mill_cobalt_wire_fine").recipe()), selected);
	}

	private static RecipePreferenceIngredientInfo target() {
		return RecipePreferenceIngredientInfo.item(COBALT_FINE_WIRE, Set.of(FINE_WIRES));
	}

	private static RecipePreferenceCandidate recipe(String recipeUid) {
		FocusedRecipe recipe = new FocusedRecipe(WIREMILL, ResourceLocation.parse(recipeUid));
		return new RecipePreferenceCandidate(recipe, List.of());
	}
}
