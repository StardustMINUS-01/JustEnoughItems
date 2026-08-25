package mezz.jei.test.gui.recipes.filtering;

import mezz.jei.gui.recipes.filtering.RecipeSearchDocument;
import mezz.jei.gui.recipes.filtering.RecipeSearchIngredient;
import mezz.jei.gui.recipes.filtering.RecipeSearchQuery;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

public class RecipeSearchQueryTest {
	private static final RecipeSearchDocument PULVERIZING = new RecipeSearchDocument(
		List.of(ingredient("Silver Ingot", "gtceu:silver_ingot", "c:ingots")),
		List.of(ingredient("Silver Dust", "gtceu:silver_dust", "c:dusts")),
		List.of(ingredient("Macerator", "gtceu:macerator", "c:machines")),
		List.of("Macerating", "gtceu:macerator", "gtceu:macerator/silver_ingot")
	);

	@Test
	public void matchesScopedTagsAndRejectsOtherSlots() {
		Assertions.assertTrue(RecipeSearchQuery.parse("i:#c:ingots").matches(PULVERIZING));
		Assertions.assertFalse(RecipeSearchQuery.parse("o:#c:ingots").matches(PULVERIZING));
	}

	@Test
	public void matchesNamesCatalystsModsAndResourceIds() {
		Assertions.assertTrue(RecipeSearchQuery.parse("silver").matches(PULVERIZING));
		Assertions.assertTrue(RecipeSearchQuery.parse("c:macerator").matches(PULVERIZING));
		Assertions.assertTrue(RecipeSearchQuery.parse("@gtceu").matches(PULVERIZING));
		Assertions.assertTrue(RecipeSearchQuery.parse("r:&gtceu:macerator/silver_ingot").matches(PULVERIZING));
	}

	@Test
	public void combinesAndOrExclusionAndQuotedPhrases() {
		Assertions.assertTrue(RecipeSearchQuery.parse("\"silver ingot\" macerator").matches(PULVERIZING));
		Assertions.assertTrue(RecipeSearchQuery.parse("gold | silver -plate").matches(PULVERIZING));
		Assertions.assertFalse(RecipeSearchQuery.parse("silver -macerator").matches(PULVERIZING));
		Assertions.assertFalse(RecipeSearchQuery.parse("gold silver").matches(PULVERIZING));
	}

	@Test
	public void emptyQueryMatchesEveryDocument() {
		Assertions.assertTrue(RecipeSearchQuery.parse("").matches(PULVERIZING));
		Assertions.assertTrue(RecipeSearchQuery.parse("   ").isEmpty());
	}

	private static RecipeSearchIngredient ingredient(String name, String id, String... tags) {
		ResourceLocation resourceLocation = ResourceLocation.parse(id);
		return new RecipeSearchIngredient(
			name,
			resourceLocation,
			resourceLocation.getNamespace(),
			Set.of(tags)
		);
	}
}
