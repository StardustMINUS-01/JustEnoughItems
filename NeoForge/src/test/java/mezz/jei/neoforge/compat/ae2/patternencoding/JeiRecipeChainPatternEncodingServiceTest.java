package mezz.jei.neoforge.compat.ae2.patternencoding;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import mezz.jei.gui.compat.ae2.JeiPatternEncodeMode;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.ItemLike;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JeiRecipeChainPatternEncodingServiceTest {
	@BeforeAll
	public static void setup() {
		LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void rejectsNonTerminalMenuBeforeAnyPlayerStateIsChecked() {
		JeiPatternEncodeRequestWire request = processingRequest(List.of(), List.of());

		JeiBatchPatternEncodeResult result =
			JeiRecipeChainPatternEncodingService.encodeRecipeChainPatterns(null, new FakeMenu(), List.of(request));

		Assertions.assertEquals(JeiPatternEncodeStopReason.NOT_PATTERN_ENCODING_TERMINAL, result.stopReason());
		Assertions.assertEquals(1, result.notProcessedCount());
	}

	@Test
	public void rejectsCatalystForNonProcessingMode() {
		JeiPatternEncodeRequestWire request = new JeiPatternEncodeRequestWire(
			ResourceLocation.fromNamespaceAndPath("test", "category"),
			ResourceLocation.fromNamespaceAndPath("test", "recipe"),
			JeiPatternEncodeMode.CRAFTING,
			List.of(),
			List.of(),
			List.of(new JeiPatternCatalystWire(0, stack(Items.IRON_INGOT, 1))),
			List.of(),
			null,
			false,
			false
		);

		Assertions.assertNotNull(JeiRecipeChainPatternEncodingService.validateCatalysts(request));
	}

	@Test
	public void rejectsOutOfRangeCatalystSlot() {
		JeiPatternEncodeRequestWire request = processingRequest(
			List.of(stack(Items.IRON_INGOT, 1)),
			List.of(new JeiPatternCatalystWire(2, stack(Items.IRON_INGOT, 1)))
		);

		Assertions.assertNotNull(JeiRecipeChainPatternEncodingService.validateCatalysts(request));
	}

	@Test
	public void rejectsDuplicateCatalystSlots() {
		JeiPatternEncodeRequestWire request = processingRequest(
			List.of(stack(Items.IRON_INGOT, 1), stack(Items.GOLD_INGOT, 1)),
			List.of(
				new JeiPatternCatalystWire(0, stack(Items.IRON_INGOT, 1)),
				new JeiPatternCatalystWire(0, stack(Items.IRON_INGOT, 1))
			)
		);

		Assertions.assertNotNull(JeiRecipeChainPatternEncodingService.validateCatalysts(request));
	}

	@Test
	public void rejectsMismatchedCatalystStack() {
		JeiPatternEncodeRequestWire request = processingRequest(
			List.of(stack(Items.IRON_INGOT, 1)),
			List.of(new JeiPatternCatalystWire(0, stack(Items.GOLD_INGOT, 1)))
		);

		Assertions.assertNotNull(JeiRecipeChainPatternEncodingService.validateCatalysts(request));
	}

	@Test
	public void rejectsProcessingPatternWithOnlyCatalysts() {
		JeiPatternEncodeRequestWire request = processingRequest(
			List.of(stack(Items.IRON_INGOT, 1)),
			List.of(new JeiPatternCatalystWire(0, stack(Items.IRON_INGOT, 1)))
		);

		Assertions.assertNotNull(JeiRecipeChainPatternEncodingService.validateCatalysts(request));
	}

	@Test
	public void acceptsValidProcessingRequestWithCatalyst() {
		JeiPatternEncodeRequestWire request = processingRequest(
			List.of(stack(Items.IRON_INGOT, 1), stack(Items.GOLD_INGOT, 1)),
			List.of(new JeiPatternCatalystWire(1, stack(Items.GOLD_INGOT, 1)))
		);

		Assertions.assertNull(JeiRecipeChainPatternEncodingService.validateCatalysts(request));
	}

	@Test
	public void officialModeRemovesCatalystInputsFromPattern() {
		JeiPatternEncodeRequestWire request = processingRequest(
			List.of(stack(Items.IRON_INGOT, 1), stack(Items.GOLD_INGOT, 1)),
			List.of(new JeiPatternCatalystWire(1, stack(Items.GOLD_INGOT, 1)))
		);

		List<GenericStack> realInputs = JeiRecipeChainPatternEncodingService.removeCatalystInputs(request.sparseInputs(), request.catalysts());

		Assertions.assertEquals(stack(Items.IRON_INGOT, 1), realInputs.get(0));
		Assertions.assertNull(realInputs.get(1));
	}

	@Test
	public void craftingGuideItemsMapSingleRowRecipeFromJeiLayoutOrder() {
		NonNullList<Ingredient> ingredients = NonNullList.withSize(3, Ingredient.EMPTY);
		ingredients.set(0, Ingredient.of(Items.GLOWSTONE_DUST));
		ingredients.set(1, Ingredient.of(Items.GLASS));
		ingredients.set(2, Ingredient.of(Items.GLOWSTONE_DUST));
		ShapedRecipe recipe = new ShapedRecipe(
			"",
			CraftingBookCategory.MISC,
			new ShapedRecipePattern(3, 1, ingredients, Optional.empty()),
			new ItemStack(Items.GLOWSTONE),
			false
		);
		List<@Nullable GenericStack> guides = emptyGuides();
		guides.set(3, stack(Items.GLOWSTONE_DUST, 1));
		guides.set(4, stack(Items.GLASS, 1));
		guides.set(5, stack(Items.GLOWSTONE_DUST, 1));

		ItemStack[] items = JeiRecipeChainPatternEncodingService.createCraftingGuideItems(
			craftingRequest(guides),
			new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("test", "vibrant_glass"), recipe)
		);

		Assertions.assertNotNull(items);
		Assertions.assertEquals(Items.GLOWSTONE_DUST, items[0].getItem());
		Assertions.assertEquals(Items.GLASS, items[1].getItem());
		Assertions.assertEquals(Items.GLOWSTONE_DUST, items[2].getItem());
		for (int i = 3; i < items.length; i++) {
			Assertions.assertTrue(items[i].isEmpty());
		}
	}

	@Test
	public void craftingGuideItemsRejectTopLeftGuidesForSingleRowRecipe() {
		NonNullList<Ingredient> ingredients = NonNullList.withSize(3, Ingredient.EMPTY);
		ingredients.set(0, Ingredient.of(Items.GLOWSTONE_DUST));
		ingredients.set(1, Ingredient.of(Items.GLASS));
		ingredients.set(2, Ingredient.of(Items.GLOWSTONE_DUST));
		ShapedRecipe recipe = new ShapedRecipe(
			"",
			CraftingBookCategory.MISC,
			new ShapedRecipePattern(3, 1, ingredients, Optional.empty()),
			new ItemStack(Items.GLOWSTONE),
			false
		);
		List<@Nullable GenericStack> guides = emptyGuides();
		guides.set(0, stack(Items.GLOWSTONE_DUST, 1));
		guides.set(1, stack(Items.GLASS, 1));
		guides.set(2, stack(Items.GLOWSTONE_DUST, 1));

		ItemStack[] items = JeiRecipeChainPatternEncodingService.createCraftingGuideItems(
			craftingRequest(guides),
			new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("test", "vibrant_glass"), recipe)
		);

		Assertions.assertNull(items);
	}

	@Test
	public void craftingGuideItemsMapShapelessThreeFromJeiLayoutOrder() {
		NonNullList<Ingredient> ingredients = NonNullList.withSize(3, Ingredient.EMPTY);
		ingredients.set(0, Ingredient.of(Items.GLOWSTONE_DUST));
		ingredients.set(1, Ingredient.of(Items.GLASS));
		ingredients.set(2, Ingredient.of(Items.REDSTONE));
		ShapelessRecipe recipe = new ShapelessRecipe(
			"",
			CraftingBookCategory.MISC,
			new ItemStack(Items.GLOWSTONE),
			ingredients
		);
		List<@Nullable GenericStack> guides = emptyGuides();
		guides.set(0, stack(Items.GLOWSTONE_DUST, 1));
		guides.set(1, stack(Items.GLASS, 1));
		guides.set(3, stack(Items.REDSTONE, 1));

		ItemStack[] items = JeiRecipeChainPatternEncodingService.createCraftingGuideItems(
			craftingRequest(guides),
			new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("test", "shapeless_three"), recipe)
		);

		Assertions.assertNotNull(items);
		Assertions.assertEquals(Items.GLOWSTONE_DUST, items[0].getItem());
		Assertions.assertEquals(Items.GLASS, items[1].getItem());
		Assertions.assertEquals(Items.REDSTONE, items[2].getItem());
		for (int i = 3; i < items.length; i++) {
			Assertions.assertTrue(items[i].isEmpty());
		}
	}

	private static List<@Nullable GenericStack> emptyGuides() {
		return new ArrayList<>(Collections.nCopies(9, (GenericStack) null));
	}

	private static JeiPatternEncodeRequestWire craftingRequest(
		List<@Nullable GenericStack> guides
	) {
		return new JeiPatternEncodeRequestWire(
			ResourceLocation.fromNamespaceAndPath("test", "category"),
			ResourceLocation.fromNamespaceAndPath("test", "recipe"),
			JeiPatternEncodeMode.CRAFTING,
			List.of(),
			List.of(),
			List.of(),
			guides,
			ResourceLocation.fromNamespaceAndPath("test", "recipe"),
			false,
			false
		);
	}

	private static JeiPatternEncodeRequestWire processingRequest(
		List<GenericStack> inputs,
		List<JeiPatternCatalystWire> catalysts
	) {
		return processingRequest(inputs, catalysts, List.of(stack(Items.DIAMOND, 1)));
	}

	private static JeiPatternEncodeRequestWire processingRequest(
		List<GenericStack> inputs,
		List<JeiPatternCatalystWire> catalysts,
		List<GenericStack> outputs
	) {
		return new JeiPatternEncodeRequestWire(
			ResourceLocation.fromNamespaceAndPath("test", "category"),
			ResourceLocation.fromNamespaceAndPath("test", "recipe"),
			JeiPatternEncodeMode.PROCESSING,
			List.copyOf(inputs),
			List.copyOf(outputs),
			List.copyOf(catalysts),
			List.of(),
			null,
			false,
			false
		);
	}

	private static GenericStack stack(ItemLike item, long amount) {
		return new GenericStack(AEItemKey.of(new ItemStack(item)), amount);
	}

	private static final class FakeMenu extends AbstractContainerMenu {
		private FakeMenu() {
			super(new MenuType<>((id, inventory) -> null, FeatureFlags.DEFAULT_FLAGS), 0);
		}

		@Override
		public ItemStack quickMoveStack(Player player, int index) {
			return ItemStack.EMPTY;
		}

		@Override
		public boolean stillValid(Player player) {
			return true;
		}
	}
}
