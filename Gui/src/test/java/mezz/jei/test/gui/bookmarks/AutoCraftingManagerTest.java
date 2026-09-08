package mezz.jei.test.gui.bookmarks;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.chain.AutoCraftingManager;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainMath;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.ingredient;
import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.input;
import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.item;
import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.key;
import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.result;

public class AutoCraftingManagerTest {
	private static final ResourceLocation C_RECIPE = ResourceLocation.fromNamespaceAndPath("test", "c");
	private static final ResourceLocation E_RECIPE = ResourceLocation.fromNamespaceAndPath("test", "e");

	@ParameterizedTest(name = "expand root demand: {0}")
	@ValueSource(booleans = {true, false})
	public void craftAllVariantsDoNotCraftAnotherTargetWhenMaterialsRemain(boolean expandRootDemand) {
		TestInventory inventory = chainInventory();
		RecipeChainMath math = chainMath();
		if (expandRootDemand) {
			math.expandRootDemandForCraftAll(inventory.snapshot());
		}

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
