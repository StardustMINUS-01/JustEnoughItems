package mezz.jei.test.bench;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.favorites.FavoriteTreeBuilder;
import mezz.jei.gui.input.FocusedRecipe;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The recipe-tree reference scenario catalog, mirroring the Thunderbolt
 * graph families: single-dag (chain, binary tree), multi-dag (fan-out),
 * cycle (conversion ring), catalyst, pruning, and deep recursion — each under
 * the MISSING / MINIMUM / UNBOUNDED data modes.
 */
public final class JeiRecipeTreeReferenceScenarios {
	private static final ResourceLocation RECIPE_TYPE = new ResourceLocation("test", "recipe_type");
	private static final String INGREDIENT_TYPE_UID = "test:ingredient";

	private JeiRecipeTreeReferenceScenarios() {
	}

	public static List<JeiRecipeTreeScenario> all() {
		return List.of(
			// --- single-dag / chain ---
			chain("chain/minimum", JeiDataMode.MINIMUM, 4, 4, true),
			chain("chain/missing", JeiDataMode.MISSING, 3, 3, false),
			chain("chain/unbounded", JeiDataMode.UNBOUNDED, 500, 500, true),
			// --- single-dag / binary tree (exponential expansion) ---
			binary("binary/minimum", JeiDataMode.MINIMUM, 3, true),
			binary("binary/missing", JeiDataMode.MISSING, 3, false),
			binary("binary/unbounded", JeiDataMode.UNBOUNDED, 10, true),
			// --- multi-dag / fan-out ---
			fanOut("fanout/minimum", JeiDataMode.MINIMUM, 2, 1, true),
			fanOut("fanout/missing", JeiDataMode.MISSING, 4, 1, false),
			fanOut("fanout/unbounded", JeiDataMode.UNBOUNDED, 16, 2, true),
			// --- cycle / conversion ring ---
			cycle("cycle/minimum", JeiDataMode.MINIMUM, 5, true),
			cycle("cycle/unbounded", JeiDataMode.UNBOUNDED, 200, true),
			// --- catalyst (un-favorited inputs do not block completion) ---
			catalyst("catalyst/minimum", JeiDataMode.MINIMUM, 1, true),
			catalyst("catalyst/unbounded", JeiDataMode.UNBOUNDED, 100, true),
			// --- pruning (missing middle input stops the branch) ---
			pruned("pruned/minimum", JeiDataMode.MINIMUM, 5, 2, true),
			pruned("pruned/unbounded", JeiDataMode.UNBOUNDED, 50, 10, true),
			// --- deep recursion ---
			deep("deep/minimum", JeiDataMode.MINIMUM, 10, 10, true),
			deep("deep/unbounded", JeiDataMode.UNBOUNDED, 1000, 1000, true)
		);
	}

	// --- family builders ---

	private static JeiRecipeTreeScenario chain(String id, JeiDataMode mode, int length, int depth, boolean favorites) {
		FocusedRecipe root = recipe("r0");
		Map<FocusedRecipe, FavoriteTreeBuilder.ResolvedRecipe> graph = new LinkedHashMap<>();
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		for (int i = 0; i < length; i++) {
			FocusedRecipe r = recipe("r" + i);
			if (i + 1 < length) {
				BookmarkIngredientKey key = key("in_" + i);
				graph.put(r, resolved(r, input(key)));
				if (favorites) {
					store.setFavorite(key, recipe("r" + (i + 1)), Map.of());
				}
			} else {
				graph.put(r, resolved(r));
			}
		}
		int expected = favorites ? Math.min(length, depth + 1) : 1;
		return new JeiRecipeTreeScenario(id, JeiTreeFamily.CHAIN, mode, length, depth, root, graph, store, expected);
	}

	private static JeiRecipeTreeScenario binary(String id, JeiDataMode mode, int treeDepth, boolean favorites) {
		int count = (1 << (treeDepth + 1)) - 1;
		FocusedRecipe root = recipe("b0");
		Map<FocusedRecipe, FavoriteTreeBuilder.ResolvedRecipe> graph = new LinkedHashMap<>();
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		for (int i = 0; i < count; i++) {
			FocusedRecipe r = recipe("b" + i);
			int left = 2 * i + 1;
			int right = 2 * i + 2;
			if (left < count) {
				BookmarkIngredientKey lk = key("b_in_" + i + "_l");
				BookmarkIngredientKey rk = key("b_in_" + i + "_r");
				graph.put(r, resolved(r, input(0, lk), input(1, rk)));
				if (favorites) {
					store.setFavorite(lk, recipe("b" + left), Map.of());
					store.setFavorite(rk, recipe("b" + right), Map.of());
				}
			} else {
				graph.put(r, resolved(r));
			}
		}
		int expected = favorites ? count : 1;
		return new JeiRecipeTreeScenario(id, JeiTreeFamily.BINARY_TREE, mode, count, treeDepth, root, graph, store, expected);
	}

	private static JeiRecipeTreeScenario fanOut(String id, JeiDataMode mode, int fanout, int depth, boolean favorites) {
		FocusedRecipe root = recipe("f_root");
		Map<FocusedRecipe, FavoriteTreeBuilder.ResolvedRecipe> graph = new LinkedHashMap<>();
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		List<FavoriteTreeBuilder.ResolvedInput> inputs = new ArrayList<>(fanout);
		for (int f = 0; f < fanout; f++) {
			BookmarkIngredientKey key = key("f_in_" + f);
			inputs.add(input(f, key));
			if (favorites) {
				store.setFavorite(key, recipe("f_leaf_" + f), Map.of());
			}
		}
		graph.put(root, resolved(root, inputs.toArray(new FavoriteTreeBuilder.ResolvedInput[0])));
		if (favorites) {
			for (int f = 0; f < fanout; f++) {
				FocusedRecipe leaf = recipe("f_leaf_" + f);
				graph.put(leaf, resolved(leaf));
			}
		}
		int expected = favorites ? fanout + 1 : 1;
		return new JeiRecipeTreeScenario(id, JeiTreeFamily.FAN_OUT, mode, fanout + 1, depth, root, graph, store, expected);
	}

	private static JeiRecipeTreeScenario cycle(String id, JeiDataMode mode, int depth, boolean favorites) {
		FocusedRecipe root = recipe("c_root");
		FocusedRecipe a = recipe("c_a");
		BookmarkIngredientKey rootKey = key("c_in_root");
		BookmarkIngredientKey aKey = key("c_in_a");
		Map<FocusedRecipe, FavoriteTreeBuilder.ResolvedRecipe> graph = new LinkedHashMap<>();
		graph.put(root, resolved(root, input(rootKey)));
		graph.put(a, resolved(a, input(aKey)));
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		if (favorites) {
			store.setFavorite(rootKey, a, Map.of());
			store.setFavorite(aKey, root, Map.of());
		}
		int expected = favorites ? 2 : 1;
		return new JeiRecipeTreeScenario(id, JeiTreeFamily.CYCLE, mode, 2, depth, root, graph, store, expected);
	}

	private static JeiRecipeTreeScenario catalyst(String id, JeiDataMode mode, int catalystCount, boolean favorites) {
		FocusedRecipe root = recipe("ct_root");
		FocusedRecipe a = recipe("ct_a");
		BookmarkIngredientKey rootKey = key("ct_in_root");
		Map<FocusedRecipe, FavoriteTreeBuilder.ResolvedRecipe> graph = new LinkedHashMap<>();
		List<FavoriteTreeBuilder.ResolvedInput> aInputs = new ArrayList<>(catalystCount);
		for (int i = 0; i < catalystCount; i++) {
			// Un-favorited catalyst input: resolved as a key, never expanded.
			aInputs.add(input(i, key("ct_cat_" + i)));
		}
		graph.put(root, resolved(root, input(rootKey)));
		graph.put(a, resolved(a, aInputs.toArray(new FavoriteTreeBuilder.ResolvedInput[0])));
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		if (favorites) {
			store.setFavorite(rootKey, a, Map.of());
		}
		int expected = favorites ? 2 : 1;
		return new JeiRecipeTreeScenario(id, JeiTreeFamily.CATALYST, mode, 2, 1, root, graph, store, expected);
	}

	private static JeiRecipeTreeScenario pruned(String id, JeiDataMode mode, int length, int cutAt, boolean favorites) {
		FocusedRecipe root = recipe("p0");
		Map<FocusedRecipe, FavoriteTreeBuilder.ResolvedRecipe> graph = new LinkedHashMap<>();
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		for (int i = 0; i < length; i++) {
			FocusedRecipe r = recipe("p" + i);
			if (i == cutAt) {
				// Missing middle input: this branch stops expanding at r_cutAt.
				graph.put(r, resolved(r, input(key("p_missing_" + i))));
			} else if (i + 1 < length) {
				BookmarkIngredientKey key = key("p_in_" + i);
				graph.put(r, resolved(r, input(key)));
				if (favorites) {
					store.setFavorite(key, recipe("p" + (i + 1)), Map.of());
				}
			} else {
				graph.put(r, resolved(r));
			}
		}
		int expected = favorites ? cutAt + 1 : 1;
		return new JeiRecipeTreeScenario(id, JeiTreeFamily.PRUNED, mode, length, length, root, graph, store, expected);
	}

	private static JeiRecipeTreeScenario deep(String id, JeiDataMode mode, int length, int depth, boolean favorites) {
		JeiRecipeTreeScenario base = chain(id, mode, length, depth, favorites);
		return new JeiRecipeTreeScenario(
			base.id(),
			JeiTreeFamily.DEEP_RECURSION,
			base.dataMode(),
			base.graphSize(),
			base.depth(),
			base.root(),
			base.graph(),
			base.store(),
			base.expectedRecipes()
		);
	}

	// --- helpers ---

	private static FocusedRecipe recipe(String uid) {
		return new FocusedRecipe(RECIPE_TYPE, new ResourceLocation("test", uid));
	}

	private static BookmarkIngredientKey key(String uid) {
		return BookmarkIngredientKey.of(INGREDIENT_TYPE_UID, uid);
	}

	private static FavoriteTreeBuilder.ResolvedInput input(BookmarkIngredientKey displayedKey) {
		return new FavoriteTreeBuilder.ResolvedInput(0, displayedKey, List.of(), List.of());
	}

	private static FavoriteTreeBuilder.ResolvedInput input(int slotIndex, BookmarkIngredientKey displayedKey) {
		return new FavoriteTreeBuilder.ResolvedInput(slotIndex, displayedKey, List.of(), List.of());
	}

	private static FavoriteTreeBuilder.ResolvedRecipe resolved(
		FocusedRecipe recipe,
		FavoriteTreeBuilder.ResolvedInput... inputs
	) {
		return new FavoriteTreeBuilder.ResolvedRecipe(recipe, List.of(inputs));
	}
}
