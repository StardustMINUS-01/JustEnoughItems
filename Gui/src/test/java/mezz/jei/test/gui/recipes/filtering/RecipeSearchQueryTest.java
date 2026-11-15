package mezz.jei.test.gui.recipes.filtering;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.recipes.filtering.IRecipeSearchTextMatcher;
import mezz.jei.gui.recipes.filtering.RecipeSearchQuery;
import mezz.jei.gui.recipes.filtering.RecipeSearchScope;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RecipeSearchQueryTest {
	private static final IIngredientType<String> TYPE = () -> String.class;
	private static final BiPredicate<ITypedIngredient<?>, String> MATCHER = (ingredient, token) -> ((String) ingredient.getIngredient()).contains(token);

	@ParameterizedTest
	@CsvSource({
		"i:silver i:copper o:dust, true",
		"i:silver -i:copper, false",
		"i:gold | i:silver, true",
		"i:gold i:silver, false",
		"o:ingot, false",
		"c:macerator, true",
		"r:&gtceu:macerator/silver_ingot, true",
		"r:&source:silver_processing, true",
		"r:@source, true",
		"r:&source:secondary_processing, true",
		"r:#ingots, false",
		"'i:\"silver ingot\"', true",
		"'i:\"ingot silver\"', false",
		"silver -macerator, false",
		"i:missing | r:macerating, true"
	})
	void preservesRecipeScopesAndComposition(String query, boolean expected) {
		assertEquals(expected, matches(RecipeSearchQuery.parse(query), new AtomicInteger()));
	}

	@Test
	void scopesOnlyUnqualifiedTermsAndSkipsUnusedMaterialExtraction() {
		assertTrue(matches(RecipeSearchQuery.parse("ingot").withDefaultScope(RecipeSearchScope.INPUT), new AtomicInteger()));
		assertFalse(matches(RecipeSearchQuery.parse("ingot").withDefaultScope(RecipeSearchScope.OUTPUT), new AtomicInteger()));
		assertTrue(matches(RecipeSearchQuery.parse("i:ingot dust").withDefaultScope(RecipeSearchScope.OUTPUT), new AtomicInteger()));
		AtomicInteger extractions = new AtomicInteger();
		assertTrue(matches(RecipeSearchQuery.parse("r:macerating"), extractions));
		assertEquals(0, extractions.get());
		assertTrue(matches(RecipeSearchQuery.parse("i:silver i:copper"), extractions));
		assertEquals(1, extractions.get());
		assertTrue(matches(RecipeSearchQuery.parse(""), extractions));
		assertTrue(RecipeSearchQuery.parse("   ").isEmpty());
	}

	@Test
	void preservesMaterialFieldTokensAndEscaping() {
		assertEquals(Set.of("#ingots/iron", "$voltage", "%tools", "^blue", "@gregtech", "&minecraft:iron_ingot"),
			RecipeSearchQuery.parse("i:#ingots/iron o:$voltage %tools ^blue c:@gregtech &minecraft:iron_ingot r:ignored").getIngredientTokens());
		assertEquals(Set.of("silver ingot", "@greg tech", "a|b", "-literal"),
			RecipeSearchQuery.parse("i:\"silver ingot\" c:@\"greg tech\" i:a\\|b i:\"-literal\"").getIngredientTokens());
		assertEquals(Set.of("-literal"), RecipeSearchQuery.parse("\\-literal").getIngredientTokens());
	}

	@Test
	void usesTheSameMatcherForInputCandidates() {
		var query = RecipeSearchQuery.parse("i:silver -i:plate o:dust");
		assertTrue(query.hasInputTerms());
		assertTrue(query.matchesInputCandidate(typed("silver ingot"), MATCHER));
		assertFalse(query.matchesInputCandidate(typed("silver plate"), MATCHER));
		assertFalse(query.matchesInputCandidate(typed("gold ingot"), MATCHER));
	}

	@ParameterizedTest
	@ValueSource(strings = {"o:silver c:macerator", "i:silver | o:gold"})
	void keepsUnrestrictedCandidates(String text) {
		var query = RecipeSearchQuery.parse(text);
		assertFalse(query.hasInputTerms());
		assertTrue(query.matchesInputCandidate(typed("gold"), MATCHER));
	}

	private static boolean matches(RecipeSearchQuery query, AtomicInteger extractions) {
		IRecipeManager recipes = proxy(IRecipeManager.class, (method, args) -> {
			if (method.equals("getRecipeIngredients")) {
				extractions.incrementAndGet();
				return (IIngredientSupplier) role -> switch (role) {
					case INPUT -> List.of(typed("silver ingot"), typed("copper ingot"));
					case OUTPUT -> List.of(typed("silver dust"));
					default -> throw new AssertionError(role);
				};
			}
			throw new AssertionError(method);
		});
		IRecipeCategory<String> category = proxy(IRecipeCategory.class, (method, args) -> switch (method) {
			case "getTitle" -> Component.literal("Macerating");
			case "getRecipeType" -> RecipeType.create("gtceu", "macerator", String.class);
			case "getRegistryName" -> ResourceLocation.parse("gtceu:macerator/silver_ingot");
			case "getSourceRecipeIds" -> List.of(ResourceLocation.parse("source:silver_processing"), ResourceLocation.parse("source:secondary_processing"));
			default -> throw new AssertionError(method);
		});
		return query.matches(category, "recipe", recipes, () -> List.of(typed("macerator")), IRecipeSearchTextMatcher.DEFAULT, MATCHER);
	}

	private static ITypedIngredient<?> typed(String value) {
		return proxy(ITypedIngredient.class, (method, args) -> switch (method) {
			case "getIngredient" -> value;
			case "getType" -> TYPE;
			default -> throw new AssertionError(method);
		});
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, java.util.function.BiFunction<String, Object[], Object> handler) {
		return (T) java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
			(instance, method, args) -> handler.apply(method.getName(), args));
	}
}
