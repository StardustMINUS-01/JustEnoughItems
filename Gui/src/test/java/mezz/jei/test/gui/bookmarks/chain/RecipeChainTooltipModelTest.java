package mezz.jei.test.gui.bookmarks.chain;

import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.chain.RecipeChainDetails;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainMath;
import mezz.jei.gui.bookmarks.chain.RecipeChainTooltipModel;
import mezz.jei.gui.bookmarks.chain.RecipeChainTooltipSectionType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

public class RecipeChainTooltipModelTest {
	@Test
	public void createsDefaultTooltipFromPrecalculatedChainDetails() {
		ResourceLocation recipeUid = ResourceLocation.fromNamespaceAndPath("test", "machine");
		List<RecipeChainInput> inputs = List.of(
			input(0, recipe(recipeUid, BookmarkItemType.RESULT, key("machine"), 1)),
			input(1, recipe(recipeUid, BookmarkItemType.INGREDIENT, key("gear"), 2))
		);
		RecipeChainDetails details = RecipeChainMath.refresh(inputs, Set.of());

		RecipeChainTooltipModel model = RecipeChainTooltipModel.create(inputs, details);

		Assertions.assertEquals(
			List.of(RecipeChainTooltipSectionType.OUTPUT, RecipeChainTooltipSectionType.INPUT),
			model.sections().stream().map(RecipeChainTooltipModel.Section::type).toList()
		);
	}

	@Test
	public void createsShiftTooltipFromPrecalculatedBaseDetails() {
		ResourceLocation recipeUid = ResourceLocation.fromNamespaceAndPath("test", "machine");
		List<RecipeChainInput> inputs = List.of(
			input(0, recipe(recipeUid, BookmarkItemType.RESULT, key("machine"), 1)),
			input(1, recipe(recipeUid, BookmarkItemType.INGREDIENT, key("gear"), 2))
		);
		RecipeChainDetails details = RecipeChainMath.refresh(inputs, Set.of());
		List<RecipeChainInput> inventory = List.of(input(-1, item(key("gear"), 2)));

		RecipeChainTooltipModel model = RecipeChainTooltipModel.create(inputs, details, Set.of(), inventory, true, true);

		Assertions.assertEquals(
			List.of(RecipeChainTooltipSectionType.OUTPUT, RecipeChainTooltipSectionType.AVAILABLE),
			model.sections().stream().map(RecipeChainTooltipModel.Section::type).toList()
		);
	}

	@Test
	public void catalystRemainsVisibleButDoesNotEnterShiftTooltipRequirements() {
		ResourceLocation recipeUid = ResourceLocation.fromNamespaceAndPath("test", "machine");
		List<RecipeChainInput> inputs = List.of(
			input(0, recipe(recipeUid, BookmarkItemType.RESULT, key("machine"), 1)),
			input(1, recipe(recipeUid, BookmarkItemType.INGREDIENT, key("gear"), 2)),
			input(2, recipe(recipeUid, BookmarkItemType.CATALYST, key("mold"), 1))
		);
		RecipeChainDetails details = RecipeChainMath.refresh(inputs, Set.of());
		List<RecipeChainInput> inventory = List.of(input(-1, item(key("gear"), 2)));

		RecipeChainTooltipModel model = RecipeChainTooltipModel.create(inputs, details, Set.of(), inventory, true, true);

		Assertions.assertEquals(
			List.of(RecipeChainTooltipSectionType.OUTPUT, RecipeChainTooltipSectionType.AVAILABLE),
			model.sections().stream().map(RecipeChainTooltipModel.Section::type).toList()
		);
		Assertions.assertFalse(details.missedItems().containsKey(key("mold")));
	}

	private static RecipeChainInput input(int index, BookmarkItemMetadata metadata) {
		return new RecipeChainInput(index, metadata);
	}

	private static BookmarkItemMetadata recipe(
		ResourceLocation recipeUid,
		BookmarkItemType type,
		BookmarkIngredientKey key,
		long factor
	) {
		return new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			type,
			1,
			factor,
			BookmarkItemMetadata.CHANCE_FULL,
			ResourceLocation.fromNamespaceAndPath("minecraft", "crafting"),
			recipeUid,
			Set.of(key)
		);
	}

	private static BookmarkItemMetadata item(BookmarkIngredientKey key, long factor) {
		return new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			BookmarkItemType.ITEM,
			1,
			factor,
			BookmarkItemMetadata.CHANCE_FULL,
			null,
			null,
			Set.of(key)
		);
	}

	private static BookmarkIngredientKey key(String uid) {
		return new BookmarkIngredientKey("test:item", uid, null);
	}
}
