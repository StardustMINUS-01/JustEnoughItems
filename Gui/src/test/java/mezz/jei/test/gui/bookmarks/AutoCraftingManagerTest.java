package mezz.jei.test.gui.bookmarks;

import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.chain.AutoCraftingManager;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainMath;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AutoCraftingManagerTest {
	private static final ResourceLocation CRAFTING = ResourceLocation.fromNamespaceAndPath("minecraft", "crafting");
	private static final ResourceLocation C_RECIPE = ResourceLocation.fromNamespaceAndPath("test", "c");
	private static final ResourceLocation E_RECIPE = ResourceLocation.fromNamespaceAndPath("test", "e");

	@Test
	public void shiftCraftAllDoesNotCraftAnotherTargetWhenMaterialsRemain() {
		TestInventory inventory = chainInventory();
		RecipeChainMath math = chainMath();
		math.expandRootDemandForCraftAll(inventory.snapshot());

		AutoCraftingManager.Result result = AutoCraftingManager.run(
			math,
			List.of(),
			inventory::snapshot,
			inventory::craft
		);

		Assertions.assertTrue(result.completed());
		Assertions.assertEquals(1, inventory.amount(key("e")));
		Assertions.assertEquals(List.of(
			new Craft(C_RECIPE, 2),
			new Craft(E_RECIPE, 1)
		), inventory.crafted);
	}

	@Test
	public void controlShiftCraftMissingDoesNotCraftAnotherTargetWhenMaterialsRemain() {
		TestInventory inventory = chainInventory();

		AutoCraftingManager.Result result = AutoCraftingManager.run(
			chainMath(),
			List.of(),
			inventory::snapshot,
			inventory::craft
		);

		Assertions.assertTrue(result.completed());
		Assertions.assertEquals(1, inventory.amount(key("e")));
		Assertions.assertEquals(List.of(
			new Craft(C_RECIPE, 2),
			new Craft(E_RECIPE, 1)
		), inventory.crafted);
	}

	private static RecipeChainMath chainMath() {
		return RecipeChainMath.of(List.of(
			input(0, result(C_RECIPE, key("c"), 1, 1)),
			input(1, ingredient(C_RECIPE, key("a"), 2)),
			input(2, ingredient(C_RECIPE, key("b"), 3)),
			input(3, result(E_RECIPE, key("e"), 1, 1)),
			input(4, ingredient(E_RECIPE, key("c"), 3)),
			input(5, ingredient(E_RECIPE, key("d"), 4))
		), Set.of());
	}

	private static TestInventory chainInventory() {
		TestInventory inventory = new TestInventory();
		inventory.add(key("a"), 20);
		inventory.add(key("b"), 30);
		inventory.add(key("c"), 1);
		inventory.add(key("d"), 8);
		return inventory;
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
		return new BookmarkIngredientKey("test:item", uid);
	}

	private record Craft(ResourceLocation recipeUid, int multiplier) {
	}

	private static final class TestInventory {
		private final Map<BookmarkIngredientKey, Long> amounts = new LinkedHashMap<>();
		private final List<Craft> crafted = new ArrayList<>();

		void add(BookmarkIngredientKey key, long amount) {
			amounts.merge(key, amount, Long::sum);
		}

		long amount(BookmarkIngredientKey key) {
			return amounts.getOrDefault(key, 0L);
		}

		List<RecipeChainInput> snapshot() {
			List<RecipeChainInput> inputs = new ArrayList<>();
			int index = 100;
			for (Map.Entry<BookmarkIngredientKey, Long> entry : amounts.entrySet()) {
				if (entry.getValue() > 0) {
					inputs.add(input(index++, item(entry.getKey(), entry.getValue())));
				}
			}
			return inputs;
		}

		boolean craft(ResourceLocation recipeUid, int multiplier) {
			if (C_RECIPE.equals(recipeUid) && take(key("a"), 2L * multiplier) && take(key("b"), 3L * multiplier)) {
				add(key("c"), multiplier);
				crafted.add(new Craft(recipeUid, multiplier));
				return true;
			}
			if (E_RECIPE.equals(recipeUid) && take(key("c"), 3L * multiplier) && take(key("d"), 4L * multiplier)) {
				add(key("e"), multiplier);
				crafted.add(new Craft(recipeUid, multiplier));
				return true;
			}
			return false;
		}

		private boolean take(BookmarkIngredientKey key, long amount) {
			long current = amount(key);
			if (current < amount) {
				return false;
			}
			add(key, -amount);
			return true;
		}
	}
}
