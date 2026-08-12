package mezz.jei.test.gui.favorites;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.favorites.IRecipeCandidateFactory;
import mezz.jei.gui.favorites.IRecipeCandidateFinder;
import mezz.jei.gui.favorites.RecipeCandidateReference;
import mezz.jei.gui.favorites.RecipeCandidateResult;
import mezz.jei.gui.favorites.RecipePreferenceCandidateResolver;
import mezz.jei.gui.favorites.preferences.RecipePreferenceCandidate;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRule;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.match.IngredientExpression;
import mezz.jei.gui.match.IngredientMatchInfo;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class RecipePreferenceCandidateResolverTest {
	private static final ResourceLocation RECIPE_TYPE = ResourceLocation.fromNamespaceAndPath("test", "wiremill");
	private static final ResourceLocation FINE_WIRES = ResourceLocation.fromNamespaceAndPath("test", "fine_wires");
	private static final ResourceLocation COBALT_FINE_WIRE = ResourceLocation.fromNamespaceAndPath("test", "cobalt_fine_wire");
	private static final IIngredientType<String> STRING_TYPE = new IIngredientType<>() {
		@Override
		public Class<? extends String> getIngredientClass() {
			return String.class;
		}
	};

	@Test
	public void getCandidatesReturnsOnlyRecipesWhoseOutputMatchesTarget() {
		BookmarkIngredientKey keyA = key("a");
		BookmarkIngredientKey keyB = key("b");
		FocusedRecipe recipeA = recipe("test:wiremill/mill_cobalt_wire_fine");
		FocusedRecipe recipeB = recipe("test:wiremill/mill_cobalt_wire_to_fine_wire");
		FakeRecipeFinder finder = new FakeRecipeFinder(
			new RecipeCandidateReference(recipeA, new Object()),
			new RecipeCandidateReference(recipeB, new Object())
		);
		FakeCandidateFactory factory = new FakeCandidateFactory()
			.result(recipeA, result(recipeA, keyA, keyB))
			.result(recipeB, result(recipeB, keyB));
		RecipePreferenceCandidateResolver resolver = resolver(finder, factory, Map.of(keyA, typed("a")), () -> RecipePreferenceRules.EMPTY);

		List<RecipePreferenceCandidate> candidates = resolver.getCandidates(keyA, typed("a"));

		Assertions.assertEquals(List.of(recipeA), recipes(candidates));
	}

	@Test
	public void getCandidatesDeduplicatesRecipesByFocusedRecipe() {
		BookmarkIngredientKey outputKey = key("out");
		FocusedRecipe recipe = recipe("duplicate");
		FakeRecipeFinder finder = new FakeRecipeFinder(
			new RecipeCandidateReference(recipe, new Object()),
			new RecipeCandidateReference(recipe, new Object())
		);
		FakeCandidateFactory factory = new FakeCandidateFactory()
			.result(recipe, result(recipe, outputKey));
		RecipePreferenceCandidateResolver resolver = resolver(finder, factory, Map.of(outputKey, typed("out")), () -> RecipePreferenceRules.EMPTY);

		List<RecipePreferenceCandidate> candidates = resolver.getCandidates(outputKey, typed("out"));

		Assertions.assertEquals(1, candidates.size());
		Assertions.assertEquals(recipe, candidates.getFirst().recipe());
	}

	@Test
	public void getCandidatesCachesVerifiedRecipesPerVariant() {
		BookmarkIngredientKey outputKey = key("out");
		FocusedRecipe recipe = recipe("cached");
		FakeRecipeFinder finder = new FakeRecipeFinder(
			new RecipeCandidateReference(recipe, new Object())
		);
		FakeCandidateFactory factory = new FakeCandidateFactory()
			.result(recipe, result(recipe, outputKey));
		RecipePreferenceCandidateResolver resolver = resolver(finder, factory, Map.of(outputKey, typed("out")), () -> RecipePreferenceRules.EMPTY);

		resolver.getCandidates(outputKey, typed("out"));
		resolver.getCandidates(outputKey, typed("out"));

		Assertions.assertEquals(1, finder.invocations());
	}

	@Test
	public void getCandidatesSkipsRecipesThatFailToMaterialize() {
		BookmarkIngredientKey outputKey = key("out");
		FocusedRecipe recipeA = recipe("works");
		FocusedRecipe recipeB = recipe("fails");
		FakeRecipeFinder finder = new FakeRecipeFinder(
			new RecipeCandidateReference(recipeA, new Object()),
			new RecipeCandidateReference(recipeB, new Object())
		);
		FakeCandidateFactory factory = new FakeCandidateFactory()
			.result(recipeA, result(recipeA, outputKey));
		RecipePreferenceCandidateResolver resolver = resolver(finder, factory, Map.of(outputKey, typed("out")), () -> RecipePreferenceRules.EMPTY);

		List<RecipePreferenceCandidate> candidates = resolver.getCandidates(outputKey, typed("out"));

		Assertions.assertEquals(List.of(recipeA), recipes(candidates));
	}

	@Test
	public void resolveGeneratedFavoriteReturnsUniqueRecipe() {
		BookmarkIngredientKey outputKey = key("out");
		FocusedRecipe recipe = recipe("unique");
		FakeRecipeFinder finder = new FakeRecipeFinder(
			new RecipeCandidateReference(recipe, new Object())
		);
		FakeCandidateFactory factory = new FakeCandidateFactory()
			.result(recipe, result(recipe, outputKey));
		RecipePreferenceCandidateResolver resolver = resolver(finder, factory, Map.of(outputKey, typed("out")), () -> RecipePreferenceRules.EMPTY);

		Optional<FocusedRecipe> selected = resolver.resolveGeneratedFavorite(outputKey);

		Assertions.assertEquals(Optional.of(recipe), selected);
	}

	@Test
	public void resolveGeneratedFavoriteUsesRulesForMultipleCandidates() {
		BookmarkIngredientKey outputKey = key("out");
		FocusedRecipe recipeA = recipe("test:wiremill/mill_cobalt_wire_fine");
		FocusedRecipe recipeB = recipe("test:wiremill/mill_cobalt_wire_to_fine_wire");
		FakeRecipeFinder finder = new FakeRecipeFinder(
			new RecipeCandidateReference(recipeA, new Object()),
			new RecipeCandidateReference(recipeB, new Object())
		);
		FakeCandidateFactory factory = new FakeCandidateFactory()
			.result(recipeA, resultWithOutput(recipeA, FINE_WIRES, outputKey))
			.result(recipeB, resultWithOutput(recipeB, FINE_WIRES, outputKey));
		RecipePreferenceRules rules = new RecipePreferenceRules(List.of(new RecipePreferenceRule(
			IngredientExpression.parseIngredient("#test:fine_wires").orElseThrow(),
			Optional.empty(),
			Optional.of(IngredientExpression.parseUid("test:wiremill/mill_*_wire_fine").orElseThrow())
		)));
		RecipePreferenceCandidateResolver resolver = resolver(finder, factory, Map.of(outputKey, typed("out")), () -> rules);

		Optional<FocusedRecipe> selected = resolver.resolveGeneratedFavorite(outputKey);

		Assertions.assertEquals(Optional.of(recipeA), selected);
	}

	@Test
	public void resolveGeneratedFavoriteCachesEmptyResult() {
		BookmarkIngredientKey outputKey = key("out");
		FakeRecipeFinder finder = new FakeRecipeFinder();
		FakeCandidateFactory factory = new FakeCandidateFactory();
		RecipePreferenceCandidateResolver resolver = resolver(finder, factory, Map.of(outputKey, typed("out")), () -> RecipePreferenceRules.EMPTY);

		Assertions.assertTrue(resolver.resolveGeneratedFavorite(outputKey).isEmpty());
		Assertions.assertTrue(resolver.resolveGeneratedFavorite(outputKey).isEmpty());

		Assertions.assertEquals(1, finder.invocations());
	}

	@Test
	public void resolveGeneratedFavoriteWithoutTypedIngredientReturnsEmpty() {
		BookmarkIngredientKey outputKey = key("out");
		FocusedRecipe recipe = recipe("unreachable");
		FakeRecipeFinder finder = new FakeRecipeFinder(
			new RecipeCandidateReference(recipe, new Object())
		);
		FakeCandidateFactory factory = new FakeCandidateFactory()
			.result(recipe, result(recipe, outputKey));
		RecipePreferenceCandidateResolver resolver = resolver(finder, factory, Map.of(), () -> RecipePreferenceRules.EMPTY);

		Assertions.assertTrue(resolver.resolveGeneratedFavorite(outputKey).isEmpty());

		Assertions.assertEquals(0, finder.invocations());
	}

	@Test
	public void invalidateGeneratedFavoritesReevaluatesAfterRulesChange() {
		BookmarkIngredientKey outputKey = key("out");
		FocusedRecipe recipeA = recipe("test:wiremill/mill_cobalt_wire_fine");
		FocusedRecipe recipeB = recipe("test:wiremill/mill_cobalt_wire_to_fine_wire");
		FakeRecipeFinder finder = new FakeRecipeFinder(
			new RecipeCandidateReference(recipeA, new Object()),
			new RecipeCandidateReference(recipeB, new Object())
		);
		FakeCandidateFactory factory = new FakeCandidateFactory()
			.result(recipeA, resultWithOutput(recipeA, FINE_WIRES, outputKey))
			.result(recipeB, resultWithOutput(recipeB, FINE_WIRES, outputKey));
		AtomicReference<RecipePreferenceRules> rulesRef = new AtomicReference<>(RecipePreferenceRules.EMPTY);
		RecipePreferenceCandidateResolver resolver = resolver(finder, factory, Map.of(outputKey, typed("out")), rulesRef::get);

		Assertions.assertTrue(resolver.resolveGeneratedFavorite(outputKey).isEmpty());

		rulesRef.set(new RecipePreferenceRules(List.of(new RecipePreferenceRule(
			IngredientExpression.parseIngredient("#test:fine_wires").orElseThrow(),
			Optional.empty(),
			Optional.of(IngredientExpression.parseUid("test:wiremill/mill_*_wire_fine").orElseThrow())
		))));
		resolver.invalidateGeneratedFavorites();

		Assertions.assertEquals(Optional.of(recipeA), resolver.resolveGeneratedFavorite(outputKey));
	}

	@Test
	public void invalidateAllClearsCaches() {
		BookmarkIngredientKey outputKey = key("out");
		FocusedRecipe recipe = recipe("cleared");
		FakeRecipeFinder finder = new FakeRecipeFinder(
			new RecipeCandidateReference(recipe, new Object())
		);
		FakeCandidateFactory factory = new FakeCandidateFactory()
			.result(recipe, result(recipe, outputKey));
		RecipePreferenceCandidateResolver resolver = resolver(finder, factory, Map.of(outputKey, typed("out")), () -> RecipePreferenceRules.EMPTY);

		resolver.getCandidates(outputKey, typed("out"));
		resolver.invalidateAll();
		resolver.getCandidates(outputKey, typed("out"));

		Assertions.assertEquals(2, finder.invocations());
	}

	private static RecipePreferenceCandidateResolver resolver(
		FakeRecipeFinder finder,
		FakeCandidateFactory factory,
		Map<BookmarkIngredientKey, ITypedIngredient<?>> typedByKey,
		Supplier<RecipePreferenceRules> rulesSupplier
	) {
		return new RecipePreferenceCandidateResolver(
			finder,
			factory,
			key -> Optional.ofNullable(typedByKey.get(key)),
			rulesSupplier
		);
	}

	private static RecipeCandidateResult result(FocusedRecipe recipe, BookmarkIngredientKey... outputKeys) {
		return resultWithOutput(recipe, ResourceLocation.fromNamespaceAndPath("test", "output"), outputKeys);
	}

	private static RecipeCandidateResult resultWithOutput(
		FocusedRecipe recipe,
		ResourceLocation outputId,
		BookmarkIngredientKey... outputKeys
	) {
		RecipePreferenceCandidate candidate = new RecipePreferenceCandidate(
			recipe,
			List.of(),
			List.of(IngredientMatchInfo.item(outputId, Set.of(FINE_WIRES)))
		);
		return new RecipeCandidateResult(candidate, Set.of(outputKeys));
	}

	private static List<FocusedRecipe> recipes(List<RecipePreferenceCandidate> candidates) {
		return candidates.stream()
			.map(RecipePreferenceCandidate::recipe)
			.toList();
	}

	private static BookmarkIngredientKey key(String uid) {
		return BookmarkIngredientKey.of("test:type", uid);
	}

	private static FocusedRecipe recipe(String recipeUid) {
		return new FocusedRecipe(RECIPE_TYPE, ResourceLocation.parse(recipeUid));
	}

	private static ITypedIngredient<String> typed(String value) {
		return new ITypedIngredient<>() {
			@Override
			public IIngredientType<String> getType() {
				return STRING_TYPE;
			}

			@Override
			public String getIngredient() {
				return value;
			}
		};
	}

	private static final class FakeRecipeFinder implements IRecipeCandidateFinder {
		private final List<RecipeCandidateReference> references;
		private final AtomicInteger invocations = new AtomicInteger();

		private FakeRecipeFinder(RecipeCandidateReference... references) {
			this.references = List.of(references);
		}

		@Override
		public List<RecipeCandidateReference> findRecipes(ITypedIngredient<?> output) {
			invocations.incrementAndGet();
			return references;
		}

		private int invocations() {
			return invocations.get();
		}
	}

	private static final class FakeCandidateFactory implements IRecipeCandidateFactory {
		private final Map<FocusedRecipe, RecipeCandidateResult> results = new HashMap<>();

		private FakeCandidateFactory result(FocusedRecipe recipe, RecipeCandidateResult result) {
			results.put(recipe, result);
			return this;
		}

		@Override
		public Optional<RecipeCandidateResult> create(RecipeCandidateReference reference) {
			return Optional.ofNullable(results.get(reference.focusedRecipe()));
		}
	}
}
