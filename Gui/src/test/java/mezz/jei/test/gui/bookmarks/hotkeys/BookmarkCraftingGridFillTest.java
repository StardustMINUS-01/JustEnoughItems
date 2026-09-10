package mezz.jei.test.gui.bookmarks.hotkeys;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkCraftingGridFill;
import mezz.jei.test.gui.fixtures.RecipeLayoutTestFixtures;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.item;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookmarkCraftingGridFillTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@ParameterizedTest
	@CsvSource({"false, 1", "true, 1", "false, 4", "true, 4"})
	void reservesMaterialForRestrictedSlot(boolean restrictedFirst, int quantity) {
		var flexible = List.<ITypedIngredient<?>>of(item(Items.OAK_PLANKS), item(Items.BIRCH_PLANKS));
		var restricted = List.<ITypedIngredient<?>>of(item(Items.OAK_PLANKS));
		var fill = fill(List.of(
			restrictedFirst ? restricted : flexible,
			List.of(),
			restrictedFirst ? flexible : restricted
		), quantity);

		assertEquals(quantity, fill.multiplier());
		assertEquals(4, fill.targetStacks().size());
		assertTrue(fill.targetStacks().get(0).is(restrictedFirst ? Items.OAK_PLANKS : Items.BIRCH_PLANKS));
		assertTrue(fill.targetStacks().get(2).is(restrictedFirst ? Items.BIRCH_PLANKS : Items.OAK_PLANKS));
		assertEquals(1, fill.targetStacks().get(0).getCount());
		assertEquals(1, fill.targetStacks().get(2).getCount());
		assertTrue(fill.targetStacks().get(1).isEmpty());
		assertTrue(fill.targetStacks().get(3).isEmpty());
	}

	@Test
	void keepsSlotOrderWhenAvailableCandidateCountsAreEqual() {
		var flexible = List.<ITypedIngredient<?>>of(item(Items.OAK_PLANKS), item(Items.BIRCH_PLANKS));
		var fill = fill(List.of(flexible, flexible), 1);

		assertEquals(1, fill.multiplier());
		assertTrue(fill.targetStacks().get(0).is(Items.OAK_PLANKS));
		assertTrue(fill.targetStacks().get(1).is(Items.BIRCH_PLANKS));
	}

	@Test
	void ignoresUnavailableAlternativesWhenOrderingSlots() {
		var fill = fill(List.of(
			List.of(item(Items.OAK_PLANKS), item(Items.BIRCH_PLANKS)),
			List.of(item(Items.OAK_PLANKS), item(Items.SPRUCE_PLANKS))
		), 1);

		assertEquals(1, fill.multiplier());
		assertTrue(fill.targetStacks().get(0).is(Items.BIRCH_PLANKS));
		assertTrue(fill.targetStacks().get(1).is(Items.OAK_PLANKS));
	}

	private static BookmarkCraftingGridFill fill(List<List<ITypedIngredient<?>>> inputs, int quantity) {
		var layout = RecipeLayoutTestFixtures.layout(
			RecipeType.create("test", "crafting", Object.class),
			new Object(), ResourceLocation.fromNamespaceAndPath("test", "overlapping_inputs"),
			inputs, List.of(List.of(item(Items.STICK)))
		);
		return BookmarkCraftingGridFill.create(layout, 4, quantity, List.of(
			new ItemStack(Items.OAK_PLANKS, quantity),
			new ItemStack(Items.BIRCH_PLANKS, quantity)
		)).orElseThrow();
	}
}
