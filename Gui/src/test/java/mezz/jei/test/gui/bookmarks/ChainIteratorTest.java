package mezz.jei.test.gui.bookmarks;

import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainIterator;
import mezz.jei.gui.bookmarks.chain.RecipeChainMath;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.ingredient;
import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.input;
import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.item;
import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.key;
import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.result;

public class ChainIteratorTest {
	private static final ResourceLocation STICK = ResourceLocation.fromNamespaceAndPath("test", "stick");
	private static final ResourceLocation TABLE = ResourceLocation.fromNamespaceAndPath("test", "table");
	private static final ResourceLocation TORCH = ResourceLocation.fromNamespaceAndPath("test", "torch");

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	public void walksLayers(boolean stocked) {
		var iterator = createIterator(List.of(
			input(0, result(TABLE, key("table"), 1, 1)),
			input(1, ingredient(TABLE, key("stick"), 4)),
			input(2, result(STICK, key("stick"), 4, 1)),
			input(3, ingredient(STICK, key("plank"), 2))));
		iterator.updateInventory(List.of(input(100, item(key(stocked ? "stick" : "plank"), stocked ? 4 : 2))));

		Assertions.assertEquals(Map.of(TABLE, 1L), iterator.next());
		if (!stocked) {
			Assertions.assertEquals(Map.of(STICK, 1L), iterator.next());
		}
		Assertions.assertEquals(Map.of(), iterator.next());
		Assertions.assertFalse(iterator.hasNext());
	}

	@Test
	public void batchesParallelRecipes() {
		var iterator = createIterator(List.of(
			input(0, result(TABLE, key("table"), 1, 1)),
			input(1, ingredient(TABLE, key("plank"), 4)),
			input(2, result(TORCH, key("torch"), 4, 1)),
			input(3, ingredient(TORCH, key("coal"), 1))));
		iterator.updateInventory(List.of(input(100, item(key("plank"), 4)), input(101, item(key("coal"), 1))));

		Assertions.assertEquals(Map.of(TABLE, 1L, TORCH, 1L), iterator.next());
		Assertions.assertEquals(Map.of(), iterator.next());
		Assertions.assertFalse(iterator.hasNext());
	}

	@Test
	public void terminatesCycles() {
		var first = ResourceLocation.fromNamespaceAndPath("test", "first");
		var second = ResourceLocation.fromNamespaceAndPath("test", "second");
		var iterator = createIterator(List.of(
			input(0, result(first, key("first_item"), 1, 1)),
			input(1, ingredient(first, key("second_item"), 1)),
			input(2, result(second, key("second_item"), 1, 1)),
			input(3, ingredient(second, key("first_item"), 1))));
		int batches = 0;
		while (iterator.hasNext() && batches < 5) {
			iterator.next();
			batches++;
		}
		Assertions.assertTrue(batches < 5);
		Assertions.assertFalse(iterator.hasNext());
	}

	private static RecipeChainIterator createIterator(List<RecipeChainInput> inputs) {
		var math = RecipeChainMath.of(inputs, Set.of());
		math.createMasterRoot();
		return new RecipeChainIterator(math, List.of());
	}
}
