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
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.ingredientManager;
import static mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.typed;

public class RecipeChainTooltipModelTest {
	@BeforeAll
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@ParameterizedTest
	@CsvSource({"0, 0", "1, 0", "64, 0", "64, 20", "64, 64", "64, 80", "128, 20", "2500, 1000"})
	public void savedTargetsShowFullDemandAndInventoryAllocation(long amount, long stored) {
		BookmarkIngredientKey key = itemKey("minecraft:iron_ingot");
		List<RecipeChainInput> inputs = List.of(new RecipeChainInput(0,
			item(key, 1).withMultiplier(amount), key, typed(new ItemStack(Items.IRON_INGOT))));
		Optional<RecipeChainDetails> details = Optional.of(RecipeChainMath.refresh(inputs, Set.of()));
		List<RecipeChainInput> inventory = List.of(input(-1, item(key, stored)));

		RecipeChainTooltipModel normal = RecipeChainTooltipModel.create(inputs, details, Set.of(), inventory, false, false, ingredientManager());
		Assertions.assertEquals(amount, sectionAmount(normal, RecipeChainTooltipSectionType.INPUT));
		for (boolean control : List.of(false, true)) {
			RecipeChainTooltipModel shift = RecipeChainTooltipModel.create(inputs, details, Set.of(), inventory, true, control, ingredientManager());
			Assertions.assertEquals(Math.max(0, amount - stored), sectionAmount(shift, RecipeChainTooltipSectionType.MISSING));
			Assertions.assertEquals(Math.min(amount, stored), sectionAmount(shift, RecipeChainTooltipSectionType.AVAILABLE));
		}
	}

	@Test
	public void ordinaryTargetsShareInventoryAcrossAvailabilityAliases() {
		BookmarkIngredientKey first = itemKey("mekanism:planks:first");
		BookmarkIngredientKey second = itemKey("mekanism:planks:second");
		List<RecipeChainInput> inputs = List.of(
			new RecipeChainInput(0, item(first, 10), first, typed(new ItemStack(Items.OAK_PLANKS))),
			new RecipeChainInput(1, item(second, 10), second, typed(new ItemStack(Items.OAK_PLANKS))));
		RecipeChainTooltipModel model = RecipeChainTooltipModel.create(inputs, Optional.empty(), Set.of(),
			List.of(input(-1, item(first, 15))), true, false, ingredientManager());
		Assertions.assertEquals(5, sectionAmount(model, RecipeChainTooltipSectionType.MISSING));
		Assertions.assertEquals(15, sectionAmount(model, RecipeChainTooltipSectionType.AVAILABLE));
	}

	private static long sectionAmount(RecipeChainTooltipModel model, RecipeChainTooltipSectionType type) {
		return model.sections().stream().filter(section -> section.type() == type)
			.flatMap(section -> section.items().stream()).mapToLong(RecipeChainTooltipModel.Item::amount).sum();
	}

	@Test
	public void createsDefaultTooltipFromPrecalculatedChainDetails() {
		ResourceLocation recipeUid = ResourceLocation.fromNamespaceAndPath("test", "machine");
		List<RecipeChainInput> inputs = List.of(
			input(0, recipe(recipeUid, BookmarkItemType.RESULT, key("machine"), 1)),
			input(1, recipe(recipeUid, BookmarkItemType.INGREDIENT, key("gear"), 2))
		);
		RecipeChainDetails details = RecipeChainMath.refresh(inputs, Set.of());

		RecipeChainTooltipModel model = RecipeChainTooltipModel.create(inputs, Optional.of(details), Set.of(), List.of(), false, false, ingredientManager());

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

		RecipeChainTooltipModel model = RecipeChainTooltipModel.create(inputs, Optional.of(details), Set.of(), inventory, true, true, ingredientManager());

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
			input(2, recipe(recipeUid, BookmarkItemType.NONCONSUMABLE, key("mold"), 1))
		);
		RecipeChainDetails details = RecipeChainMath.refresh(inputs, Set.of());
		List<RecipeChainInput> inventory = List.of(input(-1, item(key("gear"), 2)));

		RecipeChainTooltipModel model = RecipeChainTooltipModel.create(inputs, Optional.of(details), Set.of(), inventory, true, true, ingredientManager());

		Assertions.assertEquals(
			List.of(RecipeChainTooltipSectionType.OUTPUT, RecipeChainTooltipSectionType.AVAILABLE),
			model.sections().stream().map(RecipeChainTooltipModel.Section::type).toList()
		);
		Assertions.assertFalse(details.missedItems().containsKey(key("mold")));
	}

	@Test
	public void ordinaryTargetDoesNotReuseInventoryConsumedByRecipes() {
		ResourceLocation recipeUid = ResourceLocation.fromNamespaceAndPath("test", "planks");
		BookmarkIngredientKey requiredPlanks = itemKey("mekanism:planks:required");
		BookmarkIngredientKey storedPlanks = itemKey("mekanism:planks:stored");
		List<RecipeChainInput> inputs = List.of(
			input(0, recipe(recipeUid, BookmarkItemType.RESULT, key("result"), 1)),
			input(1, recipe(recipeUid, BookmarkItemType.INGREDIENT, requiredPlanks, 10)),
			new RecipeChainInput(2, item(requiredPlanks, 1), requiredPlanks, typed(new ItemStack(Items.OAK_PLANKS, 64)))
		);
		RecipeChainDetails details = RecipeChainMath.refresh(inputs, Set.of());
		List<RecipeChainInput> inventory = List.of(input(-1, item(storedPlanks, 20)));
		RecipeChainTooltipModel normal = RecipeChainTooltipModel.create(inputs, Optional.of(details), Set.of(), List.of(), false, false, ingredientManager());
		Assertions.assertEquals(74, sectionAmount(normal, RecipeChainTooltipSectionType.INPUT));

		RecipeChainTooltipModel model = RecipeChainTooltipModel.create(inputs, Optional.of(details), Set.of(), inventory, true, true, ingredientManager());

		long missing = model.sections().stream()
			.filter(section -> section.type() == RecipeChainTooltipSectionType.MISSING)
			.flatMap(section -> section.items().stream())
			.mapToLong(RecipeChainTooltipModel.Item::amount)
			.sum();
		Assertions.assertEquals(54, missing);
		Assertions.assertEquals(20, sectionAmount(model, RecipeChainTooltipSectionType.AVAILABLE));
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
		return new BookmarkIngredientKey("test:item", uid);
	}

	private static BookmarkIngredientKey itemKey(String uid) {
		return new BookmarkIngredientKey("minecraft:item_stack", uid);
	}
}
