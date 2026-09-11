package mezz.jei.test.gui.bookmarks.tree;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainMath;
import mezz.jei.gui.bookmarks.chain.RecipeChainTooltipModel;
import mezz.jei.gui.bookmarks.chain.RecipeChainTooltipSectionType;
import mezz.jei.gui.bookmarks.tree.RecipeTreeSummary;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.ingredientManager;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeSummaryTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void updatesInventory() {
		var inputs = List.of(input(0, "root", BookmarkItemType.RESULT, "result", 1),
			input(1, "root", BookmarkItemType.INGREDIENT, "iron", 8),
			input(2, "root", BookmarkItemType.NONCONSUMABLE, "mold", 64));
		var first = summary(inputs, List.of(input(-1, null, BookmarkItemType.ITEM, "iron", 3)));
		assertEquals(3, amount(first, RecipeChainTooltipSectionType.AVAILABLE));
		assertEquals(5, amount(first, RecipeChainTooltipSectionType.MISSING));
		var next = summary(inputs, List.of(input(-1, null, BookmarkItemType.ITEM, "iron", 100)));
		assertEquals(8, amount(next, RecipeChainTooltipSectionType.AVAILABLE));
		assertEquals(0, amount(next, RecipeChainTooltipSectionType.MISSING));
		assertEquals(1, inputs.getFirst().metadata().amount());
	}

	@Test
	void preservesOutputTarget() {
		var inputs = List.of(input(0, "root", BookmarkItemType.RESULT, "result", 1), input(1, "root", BookmarkItemType.INGREDIENT, "iron", 8));
		var model = summary(inputs, List.of(input(-1, null, BookmarkItemType.ITEM, "result", 64)));
		assertEquals(1, amount(model, RecipeChainTooltipSectionType.OUTPUT));
		assertEquals(8, amount(model, RecipeChainTooltipSectionType.INPUT));
		assertEquals(0, amount(model, RecipeChainTooltipSectionType.MISSING));
		assertEquals(1, amount(model, RecipeChainTooltipSectionType.AVAILABLE));
	}

	@Test
	void showsRemainder() {
		var inputs = List.of(input(0, "root", BookmarkItemType.RESULT, "result", 1), input(1, "root", BookmarkItemType.INGREDIENT, "gear", 2),
			input(2, "gear", BookmarkItemType.RESULT, "gear", 3), input(3, "gear", BookmarkItemType.INGREDIENT, "iron", 1));
		var model = summary(inputs, List.of(input(-1, null, BookmarkItemType.ITEM, "iron", 1)));
		assertEquals(1, amount(model, RecipeChainTooltipSectionType.REMAINDER));
		assertEquals(0, amount(model, RecipeChainTooltipSectionType.MISSING));
	}

	@Test
	void keepsEmptySections() {
		var model = summary(List.of(), List.of());
		assertEquals(List.of(RecipeChainTooltipSectionType.MISSING, RecipeChainTooltipSectionType.AVAILABLE, RecipeChainTooltipSectionType.REMAINDER),
			model.sections().stream().map(RecipeChainTooltipModel.Section::type).toList());
		assertTrue(model.sections().stream().allMatch(section -> section.items().isEmpty()));
	}

	private static RecipeChainTooltipModel summary(List<RecipeChainInput> inputs, List<RecipeChainInput> inventory) {
		return RecipeTreeSummary.create(inputs, Optional.of(RecipeChainMath.refresh(inputs, Set.of())), Set.of(), inventory, ingredientManager());
	}
	private static long amount(RecipeChainTooltipModel model, RecipeChainTooltipSectionType type) {
		return model.sections().stream().filter(section -> section.type() == type)
			.flatMap(section -> section.items().stream()).mapToLong(RecipeChainTooltipModel.Item::amount).sum();
	}
	private static RecipeChainInput input(int index, String recipe, BookmarkItemType type, String item, long amount) {
		return new RecipeChainInput(index, new BookmarkItemMetadata(0, type, 1, amount, BookmarkItemMetadata.CHANCE_FULL,
			recipe == null ? null : ResourceLocation.fromNamespaceAndPath("test", "processing"),
			recipe == null ? null : ResourceLocation.fromNamespaceAndPath("test", recipe), Set.of(new BookmarkIngredientKey("test:item", item))));
	}
}
