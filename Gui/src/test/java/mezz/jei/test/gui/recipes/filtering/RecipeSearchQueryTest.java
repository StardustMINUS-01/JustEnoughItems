package mezz.jei.test.gui.recipes.filtering;

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
	private static final SearchFixture PULVERIZING = new SearchFixture(
		List.of(ingredient("Silver Ingot", "gtceu:silver_ingot", "c:ingots")),
		List.of(ingredient("Silver Dust", "gtceu:silver_dust", "c:dusts")),
		List.of(ingredient("Macerator", "gtceu:macerator", "c:machines"))
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
	public void matchesRecipe(String query, boolean expected) {
		Assertions.assertEquals(expected, matchesDirect(query, new java.util.HashSet<>()));
	}

	@ParameterizedTest
	@CsvSource({
		"r:macerator, ''",
		"r:#ingots, ''",
		"i:@gtceu, INPUT+mod",
		"o:&silver_dust, OUTPUT+id",
		"i:#ingots, INPUT+tags",
		"i:silver, INPUT+name",
		"c:macerator, CATALYST+name",
		"i:missing | r:macerator, INPUT+name+id+mod+tags"
	})
	public void directSearchReadsOnlyRequiredFields(String query, String fields) {
		Set<String> accessed = new java.util.HashSet<>();
		matchesDirect(query, accessed);
		Set<String> expected = fields.isEmpty() ? Set.of() : Set.of(fields.split("\\+"));
		Assertions.assertEquals(expected, accessed);
	}

	private static boolean matchesDirect(String text, Set<String> accessed) {
		return matchesDirect(text, accessed, mezz.jei.gui.recipes.filtering.RecipeSearchScope.NONE);
	}

	@Test
	public void scopesUnqualifiedTermsWithoutOverridingExplicitTerms() {
		Assertions.assertTrue(matchesDirect("ingot", new java.util.HashSet<>(), mezz.jei.gui.recipes.filtering.RecipeSearchScope.INPUT));
		Assertions.assertFalse(matchesDirect("ingot", new java.util.HashSet<>(), mezz.jei.gui.recipes.filtering.RecipeSearchScope.OUTPUT));
		Assertions.assertTrue(matchesDirect("i:ingot dust", new java.util.HashSet<>(), mezz.jei.gui.recipes.filtering.RecipeSearchScope.OUTPUT));
		Assertions.assertTrue(matchesDirect("missing | macerator", new java.util.HashSet<>(), mezz.jei.gui.recipes.filtering.RecipeSearchScope.CATALYST));
		Assertions.assertFalse(matchesDirect("-macerator", new java.util.HashSet<>(), mezz.jei.gui.recipes.filtering.RecipeSearchScope.RECIPE));
	}

	private static boolean matchesDirect(String text, Set<String> accessed, mezz.jei.gui.recipes.filtering.RecipeSearchScope scope) {
		mezz.jei.api.ingredients.IIngredientType<RecipeSearchIngredient> type = () -> RecipeSearchIngredient.class;
		var helper = proxy(mezz.jei.api.ingredients.IIngredientHelper.class, (method, args) -> {
			RecipeSearchIngredient ingredient = (RecipeSearchIngredient) args[0];
			return switch (method) {
				case "getDisplayName" -> {
					accessed.add("name");
					yield ingredient.displayName();
				}
				case "getResourceLocation" -> {
					accessed.add("id");
					yield ResourceLocation.parse(ingredient.resourceLocation());
				}
				case "getDisplayModId" -> {
					accessed.add("mod");
					yield ingredient.modId();
				}
				case "getTagStream" -> {
					accessed.add("tags");
					yield ingredient.tags().stream().map(ResourceLocation::parse);
				}
				default -> throw new AssertionError(method);
			};
		});
		var ingredients = proxy(mezz.jei.api.runtime.IIngredientManager.class, (method, args) -> {
			if (method.equals("getIngredientHelper"))
				return helper;
			throw new AssertionError(method);
		});
		java.util.function.Function<RecipeSearchIngredient, mezz.jei.api.ingredients.ITypedIngredient<?>> typed = ingredient -> proxy(mezz.jei.api.ingredients.ITypedIngredient.class, (method, args) -> switch (method) {
			case "getIngredient" -> ingredient;
			case "getType" -> type;
			default -> throw new AssertionError(method);
		});
		var recipes = proxy(mezz.jei.api.recipe.IRecipeManager.class, (method, args) -> {
			if (method.equals("getRecipeIngredients")) {
				return (mezz.jei.api.ingredients.IIngredientSupplier) role -> {
					accessed.add(role.name());
					return switch (role) {
						case INPUT -> PULVERIZING.inputs().stream().map(typed).toList();
						case OUTPUT -> PULVERIZING.outputs().stream().map(typed).toList();
						default -> throw new AssertionError(role);
					};
				};
			}
			throw new AssertionError(method);
		});
		mezz.jei.api.recipe.category.IRecipeCategory<String> category = proxy(mezz.jei.api.recipe.category.IRecipeCategory.class, (method, args) -> switch (method) {
			case "getTitle" -> net.minecraft.network.chat.Component.literal("Macerating");
			case "getRecipeType" -> mezz.jei.api.recipe.RecipeType.create("gtceu", "macerator", String.class);
			case "getRegistryName" -> ResourceLocation.parse("gtceu:macerator/silver_ingot");
			default -> throw new AssertionError(method);
		});
		return RecipeSearchQuery.parse(text).withDefaultScope(scope).matches(category, "recipe", recipes, ingredients, () -> {
			accessed.add("CATALYST");
			return PULVERIZING.catalysts().stream().map(typed).toList();
		});
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, java.util.function.BiFunction<String, Object[], Object> handler) {
		return (T) java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
			(instance, method, args) -> handler.apply(method.getName(), args));
	}

	@Test
	public void matchesEmptyQuery() {
		Assertions.assertTrue(matchesDirect("", new java.util.HashSet<>()));
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

	private record SearchFixture(List<RecipeSearchIngredient> inputs, List<RecipeSearchIngredient> outputs, List<RecipeSearchIngredient> catalysts) {}

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
