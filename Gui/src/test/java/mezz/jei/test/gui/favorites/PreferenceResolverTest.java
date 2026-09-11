package mezz.jei.test.gui.favorites;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.common.ingredients.TypedIngredient;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.favorites.IRecipeCandidateFactory;
import mezz.jei.gui.favorites.IRecipeCandidateFinder;
import mezz.jei.gui.favorites.RecipeCandidateReference;
import mezz.jei.gui.favorites.RecipeCandidateResult;
import mezz.jei.gui.favorites.RecipeLayoutBuildCache;
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

public class PreferenceResolverTest {
	private static final ResourceLocation RECIPE_TYPE = ResourceLocation.fromNamespaceAndPath("test", "wiremill");
	private static final ResourceLocation FINE_WIRES = ResourceLocation.fromNamespaceAndPath("test", "fine_wires");
	private static final IIngredientType<String> STRING_TYPE = new IIngredientType<>() {
		@Override
		public Class<? extends String> getIngredientClass() {
			return String.class;
		}
	};

	@Test
	public void filtersOutputs() {
		BookmarkIngredientKey keyA = key("a");
		BookmarkIngredientKey keyB = key("b");
		FocusedRecipe recipeA = recipe("test:wiremill/mill_cobalt_wire_fine");
		FocusedRecipe recipeB = recipe("test:wiremill/mill_cobalt_wire_to_fine_wire");
		FakeRecipeFinder finder = new FakeRecipeFinder(
			recipeA,
			recipeB
		);
		FakeCandidateFactory factory = new FakeCandidateFactory()
			.result(result(recipeA, keyA, keyB))
			.result(result(recipeB, keyB));
		RecipePreferenceCandidateResolver resolver = resolver(finder, factory, Map.of(keyA, typed("a")), () -> RecipePreferenceRules.EMPTY);

		List<RecipePreferenceCandidate> candidates = resolver.getCandidates(keyA, typed("a"));

		Assertions.assertEquals(List.of(recipeA), recipes(candidates));
	}

	@Test
	public void deduplicatesRecipes() {
		BookmarkIngredientKey outputKey = key("out");
		FocusedRecipe recipe = recipe("duplicate");
		FakeRecipeFinder finder = new FakeRecipeFinder(
			recipe,
			recipe
		);
		FakeCandidateFactory factory = new FakeCandidateFactory()
			.result(result(recipe, outputKey));
		RecipePreferenceCandidateResolver resolver = resolver(finder, factory, Map.of(outputKey, typed("out")), () -> RecipePreferenceRules.EMPTY);

		List<RecipePreferenceCandidate> candidates = resolver.getCandidates(outputKey, typed("out"));

		Assertions.assertEquals(1, candidates.size());
		Assertions.assertEquals(recipe, candidates.getFirst().recipe());
	}

	@Test
	public void cachesVariantLookup() {
		BookmarkIngredientKey output = key("out");
		FocusedRecipe recipe = recipe("cached");
		FakeRecipeFinder finder = new FakeRecipeFinder(recipe);
		FakeCandidateFactory factory = new FakeCandidateFactory().result(result(recipe, output));
		var resolver = resolver(finder, factory, Map.of(output, typed("out")), () -> RecipePreferenceRules.EMPTY);
		RecipeLayoutBuildCache cache = new RecipeLayoutBuildCache();

		Assertions.assertEquals(List.of(recipe), recipes(resolver.getCandidates(output, typed("out"), cache)));
		Assertions.assertSame(cache, factory.lastLayoutCache);
		Assertions.assertEquals(List.of(recipe), recipes(resolver.getCandidates(output, typed("out"))));
		Assertions.assertEquals(1, finder.calls);
		Assertions.assertEquals(1, factory.calls);
	}

	@Test
	public void skipsMissingCandidate() {
		BookmarkIngredientKey outputKey = key("out");
		FocusedRecipe recipeA = recipe("works");
		FocusedRecipe recipeB = recipe("fails");
		FakeRecipeFinder finder = new FakeRecipeFinder(
			recipeA,
			recipeB
		);
		FakeCandidateFactory factory = new FakeCandidateFactory()
			.result(result(recipeA, outputKey));
		RecipePreferenceCandidateResolver resolver = resolver(finder, factory, Map.of(outputKey, typed("out")), () -> RecipePreferenceRules.EMPTY);

		List<RecipePreferenceCandidate> candidates = resolver.getCandidates(outputKey, typed("out"));

		Assertions.assertEquals(List.of(recipeA), recipes(candidates));
	}

	@Test
	public void selectsUniqueRecipe() {
		BookmarkIngredientKey outputKey = key("out");
		FocusedRecipe recipe = recipe("unique");
		FakeRecipeFinder finder = new FakeRecipeFinder(
			recipe
		);
		FakeCandidateFactory factory = new FakeCandidateFactory()
			.result(result(recipe, outputKey));
		RecipePreferenceCandidateResolver resolver = resolver(finder, factory, Map.of(outputKey, typed("out")), () -> RecipePreferenceRules.EMPTY);

		Optional<FocusedRecipe> selected = resolver.resolveGeneratedFavorite(outputKey);

		Assertions.assertEquals(Optional.of(recipe), selected);
	}

	@Test
	public void selectsByRule() {
		BookmarkIngredientKey outputKey = key("out");
		FocusedRecipe recipeA = recipe("test:wiremill/mill_cobalt_wire_fine");
		FocusedRecipe recipeB = recipe("test:wiremill/mill_cobalt_wire_to_fine_wire");
		FakeRecipeFinder finder = new FakeRecipeFinder(
			recipeA,
			recipeB
		);
		FakeCandidateFactory factory = new FakeCandidateFactory()
			.result(resultWithOutput(recipeA, FINE_WIRES, outputKey))
			.result(resultWithOutput(recipeB, FINE_WIRES, outputKey));
		RecipePreferenceRules rules = wireRules();
		RecipePreferenceCandidateResolver resolver = resolver(finder, factory, Map.of(outputKey, typed("out")), () -> rules);

		Optional<FocusedRecipe> selected = resolver.resolveGeneratedFavorite(outputKey);

		Assertions.assertEquals(Optional.of(recipeA), selected);
	}

	@Test
	public void cachesMissingFavorite() {
		BookmarkIngredientKey output = key("out");
		FakeRecipeFinder finder = new FakeRecipeFinder();
		FakeCandidateFactory factory = new FakeCandidateFactory();
		AtomicInteger lookups = new AtomicInteger();
		var resolver = new RecipePreferenceCandidateResolver(finder, factory,
			key -> {
				lookups.incrementAndGet();
				return Optional.of(typed("out"));
			}, () -> RecipePreferenceRules.EMPTY);

		Assertions.assertTrue(resolver.resolveGeneratedFavorite(output).isEmpty());
		Assertions.assertTrue(resolver.resolveGeneratedFavorite(output).isEmpty());
		Assertions.assertEquals(1, lookups.get());
		Assertions.assertEquals(1, finder.calls);
	}

	@Test
	public void requiresTypedIngredient() {
		FakeRecipeFinder finder = new FakeRecipeFinder();
		var resolver = resolver(finder, new FakeCandidateFactory(), Map.of(), () -> RecipePreferenceRules.EMPTY);

		Assertions.assertTrue(resolver.resolveGeneratedFavorite(key("out")).isEmpty());
		Assertions.assertEquals(0, finder.calls);
	}

	@Test
	public void reloadsPreferenceRules() {
		BookmarkIngredientKey outputKey = key("out");
		FocusedRecipe recipeA = recipe("test:wiremill/mill_cobalt_wire_fine");
		FocusedRecipe recipeB = recipe("test:wiremill/mill_cobalt_wire_to_fine_wire");
		FakeRecipeFinder finder = new FakeRecipeFinder(
			recipeA,
			recipeB
		);
		FakeCandidateFactory factory = new FakeCandidateFactory()
			.result(resultWithOutput(recipeA, FINE_WIRES, outputKey))
			.result(resultWithOutput(recipeB, FINE_WIRES, outputKey));
		AtomicReference<RecipePreferenceRules> rulesRef = new AtomicReference<>(RecipePreferenceRules.EMPTY);
		RecipePreferenceCandidateResolver resolver = resolver(finder, factory, Map.of(outputKey, typed("out")), rulesRef::get);

		Assertions.assertTrue(resolver.resolveGeneratedFavorite(outputKey).isEmpty());

		rulesRef.set(wireRules());
		Assertions.assertTrue(resolver.resolveGeneratedFavorite(outputKey).isEmpty());
		resolver.invalidateGeneratedFavorites();

		Assertions.assertEquals(Optional.of(recipeA), resolver.resolveGeneratedFavorite(outputKey));
		Assertions.assertEquals(1, finder.calls);
		Assertions.assertEquals(2, factory.calls);
	}

	@Test
	public void clearsCaches() {
		BookmarkIngredientKey outputKey = key("out");
		FocusedRecipe recipe = recipe("cleared");
		FakeRecipeFinder finder = new FakeRecipeFinder(
			recipe
		);
		FakeCandidateFactory factory = new FakeCandidateFactory()
			.result(result(recipe, outputKey));
		RecipePreferenceCandidateResolver resolver = resolver(finder, factory, Map.of(outputKey, typed("out")), () -> RecipePreferenceRules.EMPTY);

		Assertions.assertEquals(Optional.of(recipe), resolver.resolveGeneratedFavorite(outputKey));
		resolver.invalidateAll();
		Assertions.assertEquals(Optional.of(recipe), resolver.resolveGeneratedFavorite(outputKey));

		Assertions.assertEquals(2, finder.calls);
		Assertions.assertEquals(2, factory.calls);
	}

	private static RecipePreferenceRules wireRules() {
		return new RecipePreferenceRules(List.of(new RecipePreferenceRule(
			IngredientExpression.parseIngredient("#test:fine_wires").orElseThrow(),
			Optional.empty(),
			Optional.of(IngredientExpression.parseUid("test:wiremill/mill_*_wire_fine").orElseThrow())
		)));
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
		return TypedIngredient.createUnvalidated(STRING_TYPE, value);
	}

	private static final class FakeRecipeFinder implements IRecipeCandidateFinder {
		private final List<RecipeCandidateReference> references;
		private int calls;

		private FakeRecipeFinder(FocusedRecipe... recipes) {
			this.references = java.util.Arrays.stream(recipes)
				.map(recipe -> new RecipeCandidateReference(recipe, new Object()))
				.toList();
		}

		@Override
		public List<RecipeCandidateReference> findRecipes(ITypedIngredient<?> output) {
			calls++;
			return references;
		}
	}

	private static final class FakeCandidateFactory implements IRecipeCandidateFactory {
		private final Map<FocusedRecipe, RecipeCandidateResult> results = new HashMap<>();
		private RecipeLayoutBuildCache lastLayoutCache;
		private int calls;

		private FakeCandidateFactory result(RecipeCandidateResult result) {
			results.put(result.candidate().recipe(), result);
			return this;
		}

		@Override
		public Optional<RecipeCandidateResult> create(RecipeCandidateReference reference) {
			return Optional.ofNullable(results.get(reference.focusedRecipe()));
		}

		@Override
		public Optional<RecipeCandidateResult> create(
			RecipeCandidateReference reference,
			RecipeLayoutBuildCache layoutCache
		) {
			calls++;
			this.lastLayoutCache = layoutCache;
			return create(reference);
		}
	}
}
