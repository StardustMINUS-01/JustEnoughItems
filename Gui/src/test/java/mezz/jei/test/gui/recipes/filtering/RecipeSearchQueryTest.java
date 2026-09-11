package mezz.jei.test.gui.recipes.filtering;

import mezz.jei.gui.recipes.filtering.RecipeSearchDocument;
import mezz.jei.gui.recipes.filtering.RecipeSearchIngredient;
import mezz.jei.gui.recipes.filtering.RecipeSearchQuery;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

public class RecipeSearchQueryTest {
	private static final RecipeSearchDocument PULVERIZING = new RecipeSearchDocument(
		List.of(ingredient("Silver Ingot", "gtceu:silver_ingot", "c:ingots")),
		List.of(ingredient("Silver Dust", "gtceu:silver_dust", "c:dusts")),
		List.of(ingredient("Macerator", "gtceu:macerator", "c:machines")),
		List.of("Macerating", "gtceu:macerator", "gtceu:macerator/silver_ingot")
	);

	@ParameterizedTest
	@CsvSource({
		"i:#c:ingots, true",
		"o:#c:ingots, false",
		"silver, true",
		"c:macerator, true",
		"@gtceu, true",
		"r:&gtceu:macerator/silver_ingot, true",
		"'\"silver ingot\" macerator', true",
		"gold | silver -plate, true",
		"silver -macerator, false",
		"gold silver, false",
		"'\"ingot silver\"', false",
		"ingot silver, true"
	})
	public void matchesDocument(String query, boolean expected) {
		Assertions.assertEquals(expected, RecipeSearchQuery.parse(query).matches(PULVERIZING));
	}

	@Test
	public void matchesEmptyQuery() {
		Assertions.assertTrue(RecipeSearchQuery.parse("").matches(PULVERIZING));
		Assertions.assertTrue(RecipeSearchQuery.parse("   ").isEmpty());
	}

	@ParameterizedTest
	@CsvSource({
		"i:silver -i:plate o:dust, false",
		"silver, true"
	})
	public void filtersInputCandidates(String text, boolean matchesPlate) {
		RecipeSearchQuery query = RecipeSearchQuery.parse(text);
		Assertions.assertTrue(query.hasInputTerms());
		Assertions.assertTrue(query.matchesInputCandidate(ingredient("Silver Ingot", "gtceu:silver_ingot", "c:ingots")));
		Assertions.assertEquals(matchesPlate, query.matchesInputCandidate(ingredient("Silver Plate", "gtceu:silver_plate", "c:plates")));
		Assertions.assertFalse(query.matchesInputCandidate(ingredient("Gold Ingot", "gtceu:gold_ingot", "c:ingots")));
	}

	@ParameterizedTest
	@ValueSource(strings = {"o:silver_dust c:macerator", "i:silver | o:gold_dust"})
	public void keepsUnrestrictedCandidates(String text) {
		RecipeSearchQuery query = RecipeSearchQuery.parse(text);
		Assertions.assertFalse(query.hasInputTerms());
		Assertions.assertTrue(query.matchesInputCandidate(ingredient("Gold Ingot", "gtceu:gold_ingot", "c:ingots")));
	}

	private static RecipeSearchIngredient ingredient(String name, String resourceId, String... tags) {
		ResourceLocation id = ResourceLocation.parse(resourceId);
		return new RecipeSearchIngredient(
			name,
			id,
			id.getNamespace(),
			Set.of(tags)
		);
	}
}
