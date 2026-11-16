package mezz.jei.gui.recipes;

import mezz.jei.common.config.IClientConfig;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.match.IngredientExpression;
import mezz.jei.gui.match.IngredientMatchInfo;
import mezz.jei.gui.recipes.filtering.RecipeSearchQuery;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import mezz.jei.gui.recipes.lookups.LookupStatePositionUtil;
import mezz.jei.gui.recipes.lookups.ProjectedLookupState;
import mezz.jei.gui.recipes.lookups.SingleCategoryLookupState;
import mezz.jei.gui.recipes.lookups.StaticFocusedRecipes;
import net.minecraft.resources.ResourceLocation;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

public class RecipesGuiTest {
	@Test
	public void matchesStrictNbtWithWildcards() throws Exception {
		var id = new ResourceLocation("minecraft", "enchanted_book");
		var tag = net.minecraft.nbt.TagParser.parseTag("{StoredEnchantments:[{id:\"minecraft:sharpness\",lvl:3s}]}");
		var info = new IngredientMatchInfo(IngredientMatchInfo.Kind.ITEM, id, Set.of(), tag);
		var rule = IngredientExpression.parseIngredient("minecraft:enchanted_book & nbt:{StoredEnchantments:[{id:\"minecraft:*\",lvl:*}]}").orElseThrow();
		assertTrue(rule.matches(info));
		var groups = mezz.jei.gui.collapsible.CollapsibleRulesSerializer.deserialize(List.of(
			"item = minecraft:enchanted_book & nbt:{StoredEnchantments:[{id:\"minecraft:*\",lvl:*}]}",
			"item = minecraft:enchanted_book"
		));
		assertEquals(0, groups.resolve(info));
		assertTrue(IngredientExpression.parseIngredient("nbt:{StoredEnchantments:[{lvl:3s,id:\"minecraft:sharpness\"}]}").orElseThrow().matches(info));
		assertFalse(IngredientExpression.parseIngredient("nbt:{StoredEnchantments:[{lvl:3,id:\"minecraft:sharpness\"}]}").orElseThrow().matches(info));
		tag.getList("StoredEnchantments", net.minecraft.nbt.Tag.TAG_COMPOUND).add(net.minecraft.nbt.TagParser.parseTag("{id:\"minecraft:unbreaking\",lvl:1s}"));
		assertFalse(rule.matches(info));
		assertEquals(1, groups.resolve(info));
		assertFalse(IngredientExpression.parseIngredient("nbt:{}").orElseThrow().matches(info));
		assertTrue(IngredientExpression.parseIngredient("nbt:{Stored*: *}").orElseThrow().matches(info));
		tag.putInt("extra", 1);
		assertFalse(IngredientExpression.parseIngredient("nbt:{Stored*: *}").orElseThrow().matches(info));
		assertTrue(IngredientExpression.parseIngredient("nbt:{*: *}").orElseThrow().matches(info));
		assertTrue(IngredientExpression.parseIngredient("nbt:{}").orElseThrow().matches(IngredientMatchInfo.item(id, Set.of())));
		for (String invalid : List.of("nbt:{", "nbt:{x:", "nbt:{x:[I;1,2}", "nbt:{x:1}garbage")) {
			assertTrue(IngredientExpression.parseIngredient(invalid).isEmpty(), invalid);
		}
		var entries = mezz.jei.gui.config.ConfigLineReader.read(List.of("item = minecraft:enchanted_book &", "nbt:{text:\"$;=|x\", values:[I;1,2]}", "item = minecraft:stone"));
		assertEquals(2, entries.size());
		assertTrue(IngredientExpression.parseIngredient(entries.get(0).value()).isPresent());
	}

	@Test
	public void limitsCandidateMatchingToInputTerms() {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		var iron = mezz.jei.common.ingredients.TypedIngredient.createUnvalidated(mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
			new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
		var gold = mezz.jei.common.ingredients.TypedIngredient.createUnvalidated(mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
			new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_INGOT));
		var query = RecipeSearchQuery.parse("i:alias o:plate");
		java.util.function.BiPredicate<mezz.jei.api.ingredients.ITypedIngredient<?>, String> alias = (ingredient, token) -> ingredient == iron && token.equals("alias");
		assertTrue(query.matchesInputCandidate(iron, alias));
		assertFalse(query.matchesInputCandidate(gold, alias));
	}

	@Test
	public void restoresFilteredPagesAndEmptyResults() {
		IRecipeCategory<String> first = category("first");
		IRecipeCategory<String> second = category("second");
		IFocusedRecipes<String> recipes = new StaticFocusedRecipes<>(first, List.of("a", "b", "c", "d", "e"));
		var original = new SingleCategoryLookupState(recipes, null);
		var filtered = new ProjectedLookupState(original, List.of(recipes, new StaticFocusedRecipes<>(second, List.of("f"))));
		filtered.setRecipesPerPage(2);
		LookupStatePositionUtil.restoreRecipeIndex(filtered, 100);
		assertEquals(4, filtered.getRecipeIndex());
		filtered.nextPage();
		assertEquals(0, filtered.getRecipeIndex());
		filtered.previousPage();
		assertEquals(4, filtered.getRecipeIndex());
		filtered.nextRecipeCategory();
		assertSame(second, filtered.getFocusedRecipes().getRecipeCategory());
		assertEquals(0, filtered.getRecipeIndex());
		filtered.previousRecipeCategory();
		assertSame(first, filtered.getFocusedRecipes().getRecipeCategory());
		assertEquals(0, original.getRecipeIndex());

		var empty = new ProjectedLookupState(original, List.of());
		empty.nextRecipeCategory();
		empty.previousRecipeCategory();
		empty.previousPage();
		LookupStatePositionUtil.restoreRecipeIndex(empty, 100);
		assertTrue(empty.getRecipeCategories().isEmpty());
		assertTrue(empty.getFocusedRecipes().getRecipes().isEmpty());
		assertSame(first, empty.getFocusedRecipes().getRecipeCategory());
		assertEquals(0, empty.getRecipeIndex());
	}

	@SuppressWarnings("unchecked")
	private static IRecipeCategory<String> category(String id) {
		RecipeType<String> type = RecipeType.create("test", id, String.class);
		return (IRecipeCategory<String>) Proxy.newProxyInstance(IRecipeCategory.class.getClassLoader(), new Class<?>[]{IRecipeCategory.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "getRecipeType" -> type;
				default -> throw new UnsupportedOperationException(method.getName());
			});
	}

	@Test
	public void smallScreenUsesMinimumRecipeGuiHeight() {
		// Setup: the screen height is smaller than the recipe GUI's minimum height.
		int screenHeight = IClientConfig.minRecipeGuiHeight / 2;
		int maxHeight = IClientConfig.defaultRecipeGuiHeight;

		// Operation: calculate the initial recipe GUI size.
		RecipeGuiSizing.Size size = RecipeGuiSizing.calculateInitialSize(screenHeight, false, maxHeight);

		// Assertions: the recipe GUI keeps enough height for its internal layout.
		assertEquals(IClientConfig.minRecipeGuiHeight, size.ySize());
		assertEquals(0, size.extraSpace());
	}

	@Test
	public void smallScreenWithCenterSearchUsesMinimumRecipeGuiHeight() {
		// Setup: centered-search mode reserves more vertical space around the recipe GUI.
		int screenHeight = IClientConfig.minRecipeGuiHeight / 2;
		int maxHeight = IClientConfig.defaultRecipeGuiHeight;

		// Operation: calculate the initial recipe GUI size in centered-search mode.
		RecipeGuiSizing.Size size = RecipeGuiSizing.calculateInitialSize(screenHeight, true, maxHeight);

		// Assertions: the same minimum-height guard applies with centered search enabled.
		assertEquals(IClientConfig.minRecipeGuiHeight, size.ySize());
		assertEquals(0, size.extraSpace());
	}

	@Test
	public void tallScreenClampsToMaxRecipeGuiHeight() {
		// Setup: a tall screen has more available height than the configured maximum recipe GUI height.
		int maxHeight = IClientConfig.minRecipeGuiHeight + 20;
		int screenHeight = maxHeight + 200;

		// Operation: calculate the initial recipe GUI size.
		RecipeGuiSizing.Size size = RecipeGuiSizing.calculateInitialSize(screenHeight, false, maxHeight);

		// Assertions: the recipe GUI height is clamped to the configured maximum, with extra height for centering.
		assertEquals(maxHeight, size.ySize());
		assertTrue(size.extraSpace() > 0);
	}
}
