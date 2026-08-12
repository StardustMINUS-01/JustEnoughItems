package mezz.jei.test.gui.favorites;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.RecipeLayoutProjection;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.favorites.FavoriteTreeBookmarkWriter;
import mezz.jei.gui.favorites.FavoriteTreeBuilder;
import mezz.jei.gui.input.FocusedRecipe;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FavoriteTreeBookmarkWriterTest {
	private static final FocusedRecipe ROOT = recipe("root");
	private static final FocusedRecipe PLATE = recipe("plate");
	private static final FocusedRecipe WHITE_RECIPE = recipe("white_recipe");
	private static final FocusedRecipe DUST_RECIPE = recipe("dust_recipe");
	private static final BookmarkIngredientKey OUTPUT_KEY = key("output");

	@Test
	public void saveWritesOnlyCompleteRecipesWithRootSelections() {
		IRecipeLayoutDrawable<?> rootLayout = layout();
		IRecipeLayoutDrawable<?> plateLayout = layout();
		IRecipeLayoutDrawable<?> whiteLayout = layout();
		IRecipeLayoutDrawable<?> dustLayout = layout();
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setGeneratedFavorite(key("ingot"), PLATE);
		store.setGeneratedFavorite(key("white"), WHITE_RECIPE);
		store.setGeneratedFavorite(key("dust"), DUST_RECIPE);
		Map<FocusedRecipe, FavoriteTreeBuilder.ResolvedRecipe> graph = new LinkedHashMap<>();
		graph.put(ROOT, resolved(ROOT, rootLayout,
			new FavoriteTreeBuilder.ResolvedInput(0, key("ingot"), List.of(key("ingot")), List.of()),
			new FavoriteTreeBuilder.ResolvedInput(1, key("stained"), List.of(key("stained"), key("glass"), key("stained")), List.of())
		));
		graph.put(PLATE, resolved(PLATE, plateLayout,
			new FavoriteTreeBuilder.ResolvedInput(0, key("dust"), List.of(key("dust")), List.of()),
			new FavoriteTreeBuilder.ResolvedInput(1, key("stained"), List.of(key("stained"), key("glass"), key("stained")), List.of())
		));
		graph.put(WHITE_RECIPE, resolved(WHITE_RECIPE, whiteLayout));
		graph.put(DUST_RECIPE, resolved(DUST_RECIPE, dustLayout));
		FavoriteTreeBuilder treeBuilder = new FavoriteTreeBuilder(
			store,
			recipe -> Optional.ofNullable(graph.get(recipe))
		);
		List<RecipeLayoutProjection> projections = new ArrayList<>();
		FavoriteTreeBookmarkWriter writer = new FavoriteTreeBookmarkWriter(
			treeBuilder,
			recipe -> {
				throw new AssertionError("layout fallback should not be needed");
			},
			(layouts, preserveAmount) -> {
				projections.addAll(layouts);
				return Optional.of("group");
			}
		);

		Optional<String> groupId = writer.save(
			ROOT,
			2,
			Optional.of(OUTPUT_KEY),
			Map.of(1, key("white"))
		);

		Assertions.assertEquals(Optional.of("group"), groupId);
		Assertions.assertEquals(2, projections.size());
		Assertions.assertSame(rootLayout, projections.get(0).layout());
		Assertions.assertSame(whiteLayout, projections.get(1).layout());
		Assertions.assertFalse(projections.stream().anyMatch(p -> p.layout() == plateLayout));
		Assertions.assertFalse(projections.stream().anyMatch(p -> p.layout() == dustLayout));
		RecipeLayoutProjection rootProjection = projections.get(0);
		Assertions.assertEquals(Optional.of(OUTPUT_KEY), rootProjection.selectedOutputKey());
		Assertions.assertEquals(
			Map.of(0, key("ingot"), 1, key("white")),
			rootProjection.selectedInputKeys()
		);
		RecipeLayoutProjection whiteProjection = projections.get(1);
		Assertions.assertEquals(Optional.empty(), whiteProjection.selectedOutputKey());
		Assertions.assertEquals(Map.of(), whiteProjection.selectedInputKeys());
	}

	private static FavoriteTreeBuilder.ResolvedRecipe resolved(
		FocusedRecipe recipe,
		IRecipeLayoutDrawable<?> layout,
		FavoriteTreeBuilder.ResolvedInput... inputs
	) {
		return new FavoriteTreeBuilder.ResolvedRecipe(recipe, List.of(inputs), Optional.of(layout));
	}

	private static IRecipeLayoutDrawable<?> layout() {
		return (IRecipeLayoutDrawable<?>) Proxy.newProxyInstance(
			FavoriteTreeBookmarkWriterTest.class.getClassLoader(),
			new Class<?>[]{IRecipeLayoutDrawable.class},
			(proxy, method, args) -> defaultValue(method.getReturnType())
		);
	}

	private static Object defaultValue(Class<?> type) {
		if (!type.isPrimitive()) {
			return null;
		}
		if (type == boolean.class) {
			return false;
		}
		if (type == int.class || type == short.class || type == byte.class || type == long.class || type == float.class || type == double.class) {
			return 0;
		}
		return '\0';
	}

	private static BookmarkIngredientKey key(String uid) {
		return BookmarkIngredientKey.of("test:ingredient", uid);
	}

	private static FocusedRecipe recipe(String uid) {
		return new FocusedRecipe(ResourceLocation.fromNamespaceAndPath("test", "recipe_type"), ResourceLocation.fromNamespaceAndPath("test", uid));
	}
}
