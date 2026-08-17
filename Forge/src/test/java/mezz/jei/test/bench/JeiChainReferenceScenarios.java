package mezz.jei.test.bench;

import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The expansion-chain reference scenario catalog, mirroring the recipe-tree
 * catalog but driving the real {@code RecipeChainMath} + {@code AutoCraftingManager}
 * quantity-propagation engine (the "expansion chain" that shift+C drives).
 *
 * <p>Families: CHAIN (linear raw -&gt; ingot -&gt; plate), MULTIPLIER (root result
 * carries a batch multiplier &gt; 1), CYCLE (conversion ring guarded by the cycle
 * breaker), CATALYST (un-favorited input that must not be consumed). The
 * inventory modes MISSING / MINIMUM / UNBOUNDED map to the player's inventory
 * at shift+C time: empty, exactly enough raw material, and full/excess batches
 * of the final result.</p>
 *
 * <p>The critical scenarios are {@code chain/full-batch}, {@code chain/excess},
 * {@code multiplier/full-batch} and {@code multiplier/excess}: with the Bug6
 * fix the chain crafts 0 when the inventory already holds a full batch; the
 * correct GTNH semantics is to craft the set quantity regardless of inventory.</p>
 */
public final class JeiChainReferenceScenarios {
	private static final ResourceLocation CRAFTING = new ResourceLocation("minecraft", "crafting");

	private JeiChainReferenceScenarios() {
	}

	public static List<JeiChainScenario> all() {
		return List.of(
			// --- CHAIN: raw -> ingot -> plate, craft 1 plate ---
			chain("chain/empty", JeiDataMode.MISSING, 1, 0),
			chain("chain/partial", JeiDataMode.MINIMUM, 1, 0),
			chain("chain/full-batch", JeiDataMode.UNBOUNDED, 1, 1),
			chain("chain/excess", JeiDataMode.UNBOUNDED, 1, 2),
			// --- MULTIPLIER: same chain, craft-all batch of 8 plates ---
			multiplier("multiplier/empty", JeiDataMode.MISSING, 8, 0),
			multiplier("multiplier/partial", JeiDataMode.MINIMUM, 8, 0),
			multiplier("multiplier/full-batch", JeiDataMode.UNBOUNDED, 8, 8),
			multiplier("multiplier/excess", JeiDataMode.UNBOUNDED, 8, 16),
			// --- CYCLE: a -> b -> a, guarded by the cycle breaker ---
			cycle("cycle/empty", JeiDataMode.MISSING, 0),
			cycle("cycle/partial", JeiDataMode.MINIMUM, 1),
			// --- CATALYST: mold is an un-favorited input that must not be consumed ---
			catalyst("catalyst/empty", JeiDataMode.MISSING, 0),
			catalyst("catalyst/partial", JeiDataMode.MINIMUM, 1)
		);
	}

	// --- family builders ---

	/**
	 * Linear chain: raw -&gt; ingot -&gt; plate. plateRecipe: 2 ingot -&gt; 1 plate;
	 * ingotRecipe: 1 raw -&gt; 2 ingot. {@code multiplier} is the requested batch
	 * of the final result; {@code inventoryBatches} is how many full batches of
	 * the final result the inventory already holds (0 = none, 1 = full batch,
	 * 2 = excess).
	 */
	private static JeiChainScenario chain(String id, JeiDataMode mode, long multiplier, long inventoryBatches) {
		ResourceLocation plateRecipe = new ResourceLocation("test", "plate");
		ResourceLocation ingotRecipe = new ResourceLocation("test", "ingot");
		BookmarkIngredientKey raw = key("raw");
		BookmarkIngredientKey ingot = key("ingot");
		BookmarkIngredientKey plate = key("plate");

		List<RecipeChainInput> inputs = new ArrayList<>();
		int index = 0;
		inputs.add(input(index++, result(plateRecipe, plate, 1, multiplier)));
		inputs.add(input(index++, ingredient(plateRecipe, ingot, 2)));
		inputs.add(input(index++, result(ingotRecipe, ingot, 2, 1)));
		inputs.add(input(index++, ingredient(ingotRecipe, raw, 1)));

		Map<BookmarkIngredientKey, Long> inventoryAmounts = new LinkedHashMap<>();
		if (mode != JeiDataMode.MISSING) {
			// exactly enough raw material for one batch
			inventoryAmounts.put(raw, multiplier);
		}
		if (inventoryBatches > 0) {
			inventoryAmounts.put(plate, multiplier * inventoryBatches);
		}

		Map<ResourceLocation, Long> expected = new LinkedHashMap<>();
		if (mode != JeiDataMode.MISSING) {
			expected.put(ingotRecipe, multiplier);
			expected.put(plateRecipe, multiplier);
		}

		Map<ResourceLocation, JeiChainScenario.ChainRecipeDef> recipes = new LinkedHashMap<>();
		recipes.put(plateRecipe, new JeiChainScenario.ChainRecipeDef(
			List.of(new JeiChainScenario.ChainIngredientDef(ingot, 2, true)),
			List.of(new JeiChainScenario.ChainOutputDef(plate, 1))
		));
		recipes.put(ingotRecipe, new JeiChainScenario.ChainRecipeDef(
			List.of(new JeiChainScenario.ChainIngredientDef(raw, 1, true)),
			List.of(new JeiChainScenario.ChainOutputDef(ingot, 2))
		));

		return new JeiChainScenario(
			id,
			JeiChainFamily.CHAIN,
			mode,
			2,
			2,
			inputs,
			inventoryOf(inventoryAmounts),
			Set.of(),
			expected,
			recipes
		);
	}

	/**
	 * Same linear chain but the root result carries a batch multiplier &gt; 1
	 * (the "craft all" batch size). The chain must craft exactly {@code multiplier}
	 * of the final result regardless of inventory.
	 */
	private static JeiChainScenario multiplier(String id, JeiDataMode mode, long multiplier, long inventoryBatches) {
		JeiChainScenario base = chain(id, mode, multiplier, inventoryBatches);
		return new JeiChainScenario(
			base.id(),
			JeiChainFamily.MULTIPLIER,
			base.dataMode(),
			base.graphSize(),
			base.depth(),
			base.inputs(),
			base.inventory(),
			base.collapsedRecipes(),
			base.expectedCrafted(),
			base.recipes()
		);
	}

	/**
	 * Conversion ring: firstRecipe: 1 second_item -&gt; 1 first_item;
	 * secondRecipe: 1 first_item -&gt; 1 second_item. The cycle breaker must stop
	 * the chain from looping forever. Both recipes are middle recipes (each
	 * produces the other's input), so nothing is craftable and the chain must
	 * terminate immediately with an empty craft plan — never loop.
	 */
	private static JeiChainScenario cycle(String id, JeiDataMode mode, long inventoryAmount) {
		ResourceLocation firstRecipe = new ResourceLocation("test", "first");
		ResourceLocation secondRecipe = new ResourceLocation("test", "second");
		BookmarkIngredientKey firstItem = key("first_item");
		BookmarkIngredientKey secondItem = key("second_item");

		List<RecipeChainInput> inputs = new ArrayList<>();
		int index = 0;
		inputs.add(input(index++, result(firstRecipe, firstItem, 1, 1)));
		inputs.add(input(index++, ingredient(firstRecipe, secondItem, 1)));
		inputs.add(input(index++, result(secondRecipe, secondItem, 1, 1)));
		inputs.add(input(index++, ingredient(secondRecipe, firstItem, 1)));

		Map<BookmarkIngredientKey, Long> inventoryAmounts = new LinkedHashMap<>();
		if (mode != JeiDataMode.MISSING) {
			inventoryAmounts.put(secondItem, inventoryAmount);
		}

		Map<ResourceLocation, Long> expected = new LinkedHashMap<>();
		if (mode != JeiDataMode.MISSING) {
			// The chain converts the available second_item into one first_item
			// (firstRecipe is the only non-middle recipe), then stops — the
			// cycle breaker must never let it loop back through secondRecipe.
			expected.put(firstRecipe, 1L);
		}

		Map<ResourceLocation, JeiChainScenario.ChainRecipeDef> recipes = new LinkedHashMap<>();
		recipes.put(firstRecipe, new JeiChainScenario.ChainRecipeDef(
			List.of(new JeiChainScenario.ChainIngredientDef(secondItem, 1, true)),
			List.of(new JeiChainScenario.ChainOutputDef(firstItem, 1))
		));
		recipes.put(secondRecipe, new JeiChainScenario.ChainRecipeDef(
			List.of(new JeiChainScenario.ChainIngredientDef(firstItem, 1, true)),
			List.of(new JeiChainScenario.ChainOutputDef(secondItem, 1))
		));

		return new JeiChainScenario(
			id,
			JeiChainFamily.CYCLE,
			mode,
			2,
			2,
			inputs,
			inventoryOf(inventoryAmounts),
			Set.of(),
			expected,
			recipes
		);
	}

	/**
	 * plateRecipe: 2 ingot + 1 mold (catalyst) -&gt; 1 plate. The mold is an
	 * un-favorited input that must not be consumed by the executor.
	 */
	private static JeiChainScenario catalyst(String id, JeiDataMode mode, long inventoryAmount) {
		ResourceLocation plateRecipe = new ResourceLocation("test", "plate");
		BookmarkIngredientKey plate = key("plate");
		BookmarkIngredientKey ingot = key("ingot");
		BookmarkIngredientKey mold = key("mold");

		List<RecipeChainInput> inputs = new ArrayList<>();
		int index = 0;
		inputs.add(input(index++, result(plateRecipe, plate, 1, 1)));
		inputs.add(input(index++, ingredient(plateRecipe, ingot, 2)));
		inputs.add(input(index++, ingredient(plateRecipe, mold, 1).withType(BookmarkItemType.CATALYST)));

		Map<BookmarkIngredientKey, Long> inventoryAmounts = new LinkedHashMap<>();
		if (mode != JeiDataMode.MISSING) {
			inventoryAmounts.put(ingot, 2L);
			inventoryAmounts.put(mold, inventoryAmount);
		}

		Map<ResourceLocation, Long> expected = new LinkedHashMap<>();
		if (mode != JeiDataMode.MISSING) {
			expected.put(plateRecipe, 1L);
		}

		Map<ResourceLocation, JeiChainScenario.ChainRecipeDef> recipes = new LinkedHashMap<>();
		recipes.put(plateRecipe, new JeiChainScenario.ChainRecipeDef(
			List.of(
				new JeiChainScenario.ChainIngredientDef(ingot, 2, true),
				new JeiChainScenario.ChainIngredientDef(mold, 1, false)
			),
			List.of(new JeiChainScenario.ChainOutputDef(plate, 1))
		));

		return new JeiChainScenario(
			id,
			JeiChainFamily.CATALYST,
			mode,
			1,
			1,
			inputs,
			inventoryOf(inventoryAmounts),
			Set.of(),
			expected,
			recipes
		);
	}

	// --- helpers ---

	private static List<RecipeChainInput> inventoryOf(Map<BookmarkIngredientKey, Long> amounts) {
		List<RecipeChainInput> inputs = new ArrayList<>();
		int index = 100;
		for (Map.Entry<BookmarkIngredientKey, Long> entry : amounts.entrySet()) {
			if (entry.getValue() > 0) {
				inputs.add(input(index++, item(entry.getKey(), entry.getValue())));
			}
		}
		return inputs;
	}

	private static RecipeChainInput input(int index, BookmarkItemMetadata metadata) {
		return new RecipeChainInput(index, metadata);
	}

	private static BookmarkItemMetadata item(BookmarkIngredientKey key, long amount) {
		return metadata(null, BookmarkItemType.ITEM, key, amount, 1);
	}

	private static BookmarkItemMetadata result(ResourceLocation recipeUid, BookmarkIngredientKey key, long factor, long multiplier) {
		return metadata(recipeUid, BookmarkItemType.RESULT, key, factor, multiplier);
	}

	private static BookmarkItemMetadata ingredient(ResourceLocation recipeUid, BookmarkIngredientKey key, long factor) {
		return metadata(recipeUid, BookmarkItemType.INGREDIENT, key, factor, 1);
	}

	private static BookmarkItemMetadata metadata(ResourceLocation recipeUid, BookmarkItemType type, BookmarkIngredientKey key, long factor, long multiplier) {
		return new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			type,
			multiplier,
			factor,
			BookmarkItemMetadata.CHANCE_FULL,
			CRAFTING,
			recipeUid,
			Set.of(key)
		);
	}

	private static BookmarkIngredientKey key(String uid) {
		return new BookmarkIngredientKey("test:item", uid, null);
	}
}