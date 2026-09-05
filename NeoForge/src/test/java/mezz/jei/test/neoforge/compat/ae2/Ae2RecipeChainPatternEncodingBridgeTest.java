package mezz.jei.test.neoforge.compat.ae2;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.gui.compat.ae2.JeiPatternEncodeMode;
import mezz.jei.gui.compat.ae2.JeiPatternEncodeRequest;
import mezz.jei.neoforge.compat.ae2.Ae2RecipeChainPatternEncodingBridge;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class Ae2RecipeChainPatternEncodingBridgeTest {
	@BeforeAll
	public static void bootstrap() {
		LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void selectedCraftingRecipeUsesDedicatedTransferPath() {
		TestAccess access = new TestAccess(true, true);
		Ae2RecipeChainPatternEncodingBridge bridge = new Ae2RecipeChainPatternEncodingBridge(access);

		boolean transferred = bridge.transferSelectedCraftingRecipe(null, new Object(), RecipeTypes.CRAFTING, List::of);

		Assertions.assertTrue(transferred);
		Assertions.assertEquals(1, access.transferredSelectedRecipes);
	}

	@Test
	public void selectedCraftingRecipeFallsBackAfterAe2LinkageError() {
		TestAccess access = new TestAccess(true, true);
		access.throwSelectedTransferLinkageError = true;
		Ae2RecipeChainPatternEncodingBridge bridge = new Ae2RecipeChainPatternEncodingBridge(access);

		boolean firstTransfer = Assertions.assertDoesNotThrow(() -> bridge.transferSelectedCraftingRecipe(null, new Object(), RecipeTypes.CRAFTING, List::of));
		access.throwSelectedTransferLinkageError = false;
		boolean secondTransfer = bridge.transferSelectedCraftingRecipe(null, new Object(), RecipeTypes.CRAFTING, List::of);

		Assertions.assertFalse(firstTransfer);
		Assertions.assertFalse(secondTransfer);
		Assertions.assertEquals(1, access.transferredSelectedRecipes);
	}

	@Test
	public void selectedCraftingIngredientsUseInputSlotsInGridOrder() throws ReflectiveOperationException {
		IRecipeSlotView output = slot(RecipeIngredientRole.OUTPUT, item(Items.DIAMOND));
		IRecipeSlotView selectedInput = slot(RecipeIngredientRole.INPUT, item(Items.CHERRY_PLANKS));
		IRecipeSlotView unselectedInput = slot(RecipeIngredientRole.INPUT, item(Items.OAK_PLANKS), item(Items.BIRCH_PLANKS));
		List<IRecipeSlotView> slots = new ArrayList<>();
		slots.add(output);
		slots.add(selectedInput);
		slots.add(unselectedInput);
		for (int i = 0; i < 7; i++) {
			slots.add(slot(RecipeIngredientRole.INPUT));
		}
		IRecipeSlotsView slotsView = () -> slots;
		var method = Assertions.assertDoesNotThrow(() -> Ae2RecipeChainPatternEncodingBridge.class.getDeclaredMethod(
			"createCraftingIngredients",
			IRecipeSlotsView.class
		));
		method.setAccessible(true);

		@SuppressWarnings("unchecked")
		List<List<GenericStack>> ingredients = (List<List<GenericStack>>) method.invoke(null, slotsView);

		Assertions.assertEquals(9, ingredients.size());
		Assertions.assertEquals(List.of(AEItemKey.of(new ItemStack(Items.CHERRY_PLANKS))), ingredients.get(0).stream().map(GenericStack::what).toList());
		Assertions.assertEquals(
			List.of(AEItemKey.of(new ItemStack(Items.OAK_PLANKS)), AEItemKey.of(new ItemStack(Items.BIRCH_PLANKS))),
			ingredients.get(1).stream().map(GenericStack::what).toList()
		);
		Assertions.assertTrue(ingredients.subList(2, 9).stream().allMatch(List::isEmpty));
		slots.add(slot(RecipeIngredientRole.INPUT));
		@SuppressWarnings("unchecked")
		List<List<GenericStack>> oversizedIngredients = (List<List<GenericStack>>) method.invoke(null, slotsView);
		Assertions.assertTrue(oversizedIngredients.isEmpty());
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("sendRequestOutcomes")
	public void sendRequestsHandlesAllOutcomes(String scenario, boolean terminal, boolean sendSucceeds, boolean empty, boolean expected, int expectedCalls) {
		TestAccess access = new TestAccess(terminal, sendSucceeds);
		Ae2RecipeChainPatternEncodingBridge bridge = new Ae2RecipeChainPatternEncodingBridge(access);

		Assertions.assertEquals(expected, bridge.sendRequests(null, empty ? List.of() : List.of(request())));
		Assertions.assertEquals(expectedCalls, access.sentRequests);
	}

	private static Stream<Arguments> sendRequestOutcomes() {
		return Stream.of(
			Arguments.of("empty request", true, true, true, false, 0),
			Arguments.of("nonterminal menu", false, true, false, false, 0),
			Arguments.of("send failure", true, false, false, false, 1),
			Arguments.of("success", true, true, false, true, 1)
		);
	}

	private static JeiPatternEncodeRequest request() {
		return new JeiPatternEncodeRequest(
			ResourceLocation.fromNamespaceAndPath("test", "category"),
			ResourceLocation.fromNamespaceAndPath("test", "recipe"),
			JeiPatternEncodeMode.PROCESSING,
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			null,
			false,
			false
		);
	}

	private static IRecipeSlotView slot(RecipeIngredientRole role, ITypedIngredient<?>... ingredients) {
		List<ITypedIngredient<?>> ingredientList = List.of(ingredients);
		return new IRecipeSlotView() {
			@Override
			public java.util.stream.Stream<ITypedIngredient<?>> getAllIngredients() {
				return ingredientList.stream();
			}

			@Override
			public List<ITypedIngredient<?>> getAllIngredientsList() {
				return ingredientList;
			}

			@Override
			public Optional<ITypedIngredient<?>> getDisplayedIngredient() {
				return ingredientList.stream().findFirst();
			}

			@Override
			public RecipeIngredientRole getRole() {
				return role;
			}

			@Override
			public void drawHighlight(net.minecraft.client.gui.GuiGraphics guiGraphics, int color) {
			}

			@Override
			public Optional<String> getSlotName() {
				return Optional.empty();
			}
		};
	}

	private static ITypedIngredient<ItemStack> item(net.minecraft.world.level.ItemLike item) {
		ItemStack stack = new ItemStack(item);
		return new ITypedIngredient<>() {
			@Override
			public IIngredientType<ItemStack> getType() {
				return VanillaTypes.ITEM_STACK;
			}

			@Override
			public ItemStack getIngredient() {
				return stack;
			}
		};
	}

	private static final class TestAccess implements Ae2RecipeChainPatternEncodingBridge.Access {
		private final boolean terminal;
		private final boolean sendSucceeds;
		private int sentRequests;
		private int transferredSelectedRecipes;
		private boolean throwSelectedTransferLinkageError;

		private TestAccess(boolean terminal, boolean sendSucceeds) {
			this.terminal = terminal;
			this.sendSucceeds = sendSucceeds;
		}

		@Override
		public boolean isPatternEncodingTerminal(AbstractContainerMenu menu) {
			return terminal;
		}

		@Override
		public boolean sendRequests(AbstractContainerMenu menu, List<JeiPatternEncodeRequest> requests) {
			sentRequests++;
			if (!sendSucceeds) {
				return false;
			}
			return true;
		}

		@Override
		public boolean transferSelectedCraftingRecipe(
			AbstractContainerMenu menu,
			Object recipe,
			mezz.jei.api.recipe.RecipeType<?> recipeType,
			IRecipeSlotsView slotsView
		) {
			transferredSelectedRecipes++;
			if (throwSelectedTransferLinkageError) {
				throw new NoSuchMethodError("AE2 encoding helper changed");
			}
			return true;
		}
	}
}
