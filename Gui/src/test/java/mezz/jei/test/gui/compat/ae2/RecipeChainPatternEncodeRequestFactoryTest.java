package mezz.jei.test.gui.compat.ae2;

import com.mojang.blaze3d.platform.InputConstants;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.compat.ae2.Ae2RecipeChainPatternEncodingBridge;
import mezz.jei.gui.compat.ae2.JeiPatternCatalyst;
import mezz.jei.gui.compat.ae2.JeiPatternEncodeMode;
import mezz.jei.gui.compat.ae2.JeiPatternEncodeRequest;
import mezz.jei.gui.compat.ae2.RecipeChainPatternEncodeController;
import mezz.jei.gui.compat.ae2.RecipeChainPatternEncodeRequestFactory;
import mezz.jei.gui.input.InputType;
import mezz.jei.gui.input.UserInput;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

public class RecipeChainPatternEncodeRequestFactoryTest {
	private static final ResourceLocation CRAFTING = ResourceLocation.fromNamespaceAndPath("minecraft", "crafting");
	private static final ResourceLocation CRAFTING_RECIPE = ResourceLocation.fromNamespaceAndPath("test", "crafting_result");
	private static final ResourceLocation PROCESSING_TYPE = ResourceLocation.fromNamespaceAndPath("gtceu", "assembler");
	private static final ResourceLocation PROCESSING_RECIPE = ResourceLocation.fromNamespaceAndPath("test", "processing_result");
	private static final ResourceLocation OTHER_RECIPE = ResourceLocation.fromNamespaceAndPath("test", "other_result");
	private static final IIngredientManager INGREDIENT_MANAGER = ingredientManager();

	@BeforeAll
	public static void setup() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void craftingRequestUsesCanonicalRecipeIdWithoutBookmarkSlots() {
		RecipeChainPatternEncodeRequestFactory factory = new RecipeChainPatternEncodeRequestFactory(INGREDIENT_MANAGER);
		List<RecipeChainInput> inputs = List.of(
			input(0, result(CRAFTING, CRAFTING_RECIPE, key("result"))),
			input(1, ingredient(CRAFTING, CRAFTING_RECIPE, key("a"))),
			input(2, ingredient(CRAFTING, CRAFTING_RECIPE, key("b")))
		);
		TestRecipeLayout layout = layout(
			RecipeTypes.CRAFTING,
			craftingRecipeHolder(CRAFTING_RECIPE),
			CRAFTING_RECIPE,
			Arrays.asList(item(Items.STICK), null, item(Items.COBBLESTONE), null, null, null, null, null, null),
			List.of(item(Items.DIAMOND))
		);

		RecipeChainPatternEncodeRequestFactory.Result result = factory.createRequests(
			inputs,
			Set.of(),
			resolver(layout)
		);

		Assertions.assertEquals(RecipeChainPatternEncodeRequestFactory.Status.OK, result.status());
		JeiPatternEncodeRequest request = Assertions.assertDoesNotThrow(() -> result.requests().getFirst());
		Assertions.assertEquals(JeiPatternEncodeMode.CRAFTING, request.mode());
		Assertions.assertEquals(CRAFTING_RECIPE, request.canonicalRecipeId());
		Assertions.assertTrue(request.sparseInputs().isEmpty());
		Assertions.assertTrue(request.sparseOutputs().isEmpty());
		Assertions.assertTrue(request.catalysts().isEmpty());
		Assertions.assertEquals(9, request.canonicalInputGuides().size());
		Assertions.assertEquals(Items.STICK, request.canonicalInputGuides().getFirst().ingredient().getItemStack().orElseThrow().getItem());
		Assertions.assertNull(request.canonicalInputGuides().get(1));
		Assertions.assertEquals(Items.COBBLESTONE, request.canonicalInputGuides().get(2).ingredient().getItemStack().orElseThrow().getItem());
	}

	@Test
	public void craftingRequestDoesNotInspectTheClientRecipeObject() {
		RecipeChainPatternEncodeRequestFactory factory = new RecipeChainPatternEncodeRequestFactory(INGREDIENT_MANAGER);
		List<RecipeChainInput> inputs = List.of(
			input(0, result(CRAFTING, CRAFTING_RECIPE, key("result")))
		);
		TestRecipeLayout layout = layout(
			RecipeTypes.CRAFTING,
			"not_a_crafting_recipe_holder",
			CRAFTING_RECIPE,
			Arrays.asList(item(Items.STICK), null, null, null, null, null, null, null, null),
			List.of(item(Items.DIAMOND))
		);

		RecipeChainPatternEncodeRequestFactory.Result result = factory.createRequests(
			inputs,
			Set.of(),
			resolver(layout)
		);

		Assertions.assertEquals(RecipeChainPatternEncodeRequestFactory.Status.OK, result.status());
		JeiPatternEncodeRequest request = result.requests().getFirst();
		Assertions.assertEquals(JeiPatternEncodeMode.CRAFTING, request.mode());
		Assertions.assertEquals(CRAFTING_RECIPE, request.canonicalRecipeId());
		Assertions.assertTrue(request.catalysts().isEmpty());
		Assertions.assertTrue(request.sparseInputs().isEmpty());
		Assertions.assertTrue(request.sparseOutputs().isEmpty());
		Assertions.assertEquals(9, request.canonicalInputGuides().size());
		Assertions.assertEquals(Items.STICK, request.canonicalInputGuides().getFirst().ingredient().getItemStack().orElseThrow().getItem());
	}

	@Test
	public void craftingBookmarkGuideAppliesToEverySlotWithTheSameSavedPermutationSet() {
		RecipeChainPatternEncodeRequestFactory factory = new RecipeChainPatternEncodeRequestFactory(INGREDIENT_MANAGER);
		ITypedIngredient<ItemStack> oak = item(Items.OAK_PLANKS);
		ITypedIngredient<ItemStack> birch = item(Items.BIRCH_PLANKS);
		Set<BookmarkIngredientKey> planks = Set.of(
			BookmarkItemMetadataFactory.createPermutationKey(oak, INGREDIENT_MANAGER),
			BookmarkItemMetadataFactory.createPermutationKey(birch, INGREDIENT_MANAGER)
		);
		BookmarkItemMetadata planksMetadata = new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			BookmarkItemType.INGREDIENT,
			1,
			3,
			BookmarkItemMetadata.CHANCE_FULL,
			CRAFTING,
			CRAFTING_RECIPE,
			planks
		);
		RecipeHolder<ShapelessRecipe> recipe = craftingRecipeHolder(CRAFTING_RECIPE);
		TestRecipeLayout layout = new TestRecipeLayout(
			new TestRecipeCategory(RecipeTypes.CRAFTING, recipe, CRAFTING_RECIPE),
			recipe,
			Arrays.asList(
				slot(List.of(oak, birch), oak),
				slot(List.of(oak, birch), birch),
				slot(List.of(oak, birch), birch),
				new TestRecipeSlotView(RecipeIngredientRole.INPUT, null),
				new TestRecipeSlotView(RecipeIngredientRole.INPUT, null),
				new TestRecipeSlotView(RecipeIngredientRole.INPUT, null),
				new TestRecipeSlotView(RecipeIngredientRole.INPUT, null),
				new TestRecipeSlotView(RecipeIngredientRole.INPUT, null),
				new TestRecipeSlotView(RecipeIngredientRole.INPUT, null)
			),
			List.of(new TestRecipeSlotView(RecipeIngredientRole.OUTPUT, item(Items.WHITE_BED)))
		);

		JeiPatternEncodeRequest request = factory.createSingleRequest(
			layout,
			Optional.empty(),
			List.of(new RecipeChainInput(0, planksMetadata, BookmarkItemMetadataFactory.createPermutationKey(oak, INGREDIENT_MANAGER)))
		).requests().getFirst();

		for (int index = 0; index < 3; index++) {
			Assertions.assertEquals(Items.OAK_PLANKS, request.canonicalInputGuides().get(index).ingredient().getItemStack().orElseThrow().getItem());
		}
	}

	@Test
	public void craftingBookmarkGuideKeepsMixedSavedCountsWithoutRecipeMultiplier() {
		RecipeChainPatternEncodeRequestFactory factory = new RecipeChainPatternEncodeRequestFactory(INGREDIENT_MANAGER);
		ITypedIngredient<ItemStack> oak = item(Items.OAK_PLANKS);
		ITypedIngredient<ItemStack> birch = item(Items.BIRCH_PLANKS);
		Set<BookmarkIngredientKey> planks = Set.of(
			BookmarkItemMetadataFactory.createPermutationKey(oak, INGREDIENT_MANAGER),
			BookmarkItemMetadataFactory.createPermutationKey(birch, INGREDIENT_MANAGER)
		);
		BookmarkItemMetadata oakMetadata = new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID, BookmarkItemType.INGREDIENT, 4, 1,
			BookmarkItemMetadata.CHANCE_FULL, CRAFTING, CRAFTING_RECIPE, planks
		);
		BookmarkItemMetadata birchMetadata = new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID, BookmarkItemType.INGREDIENT, 4, 7,
			BookmarkItemMetadata.CHANCE_FULL, CRAFTING, CRAFTING_RECIPE, planks
		);
		RecipeHolder<ShapelessRecipe> recipe = craftingRecipeHolder(CRAFTING_RECIPE);
		List<TestRecipeSlotView> inputs = new ArrayList<>();
		for (int index = 0; index < 8; index++) {
			inputs.add(slot(List.of(oak, birch), birch));
		}
		inputs.add(new TestRecipeSlotView(RecipeIngredientRole.INPUT, null));
		TestRecipeLayout layout = new TestRecipeLayout(
			new TestRecipeCategory(RecipeTypes.CRAFTING, recipe, CRAFTING_RECIPE),
			recipe,
			List.copyOf(inputs),
			List.of(new TestRecipeSlotView(RecipeIngredientRole.OUTPUT, item(Items.FURNACE)))
		);

		JeiPatternEncodeRequest request = factory.createSingleRequest(
			layout,
			Optional.empty(),
			List.of(
				new RecipeChainInput(0, oakMetadata, BookmarkItemMetadataFactory.createPermutationKey(oak, INGREDIENT_MANAGER)),
				new RecipeChainInput(1, birchMetadata, BookmarkItemMetadataFactory.createPermutationKey(birch, INGREDIENT_MANAGER))
			)
		).requests().getFirst();

		Assertions.assertEquals(Items.OAK_PLANKS, request.canonicalInputGuides().getFirst().ingredient().getItemStack().orElseThrow().getItem());
		Assertions.assertEquals(7, request.canonicalInputGuides().subList(1, 8).stream()
			.filter(stack -> stack.ingredient().getItemStack().orElseThrow().is(Items.BIRCH_PLANKS))
			.count());
		Assertions.assertNull(request.canonicalInputGuides().get(8));
	}

	@Test
	public void processingRequestPutsTargetOutputFirst() {
		RecipeChainPatternEncodeRequestFactory factory = new RecipeChainPatternEncodeRequestFactory(INGREDIENT_MANAGER);
		List<RecipeChainInput> inputs = List.of(
			input(0, result(PROCESSING_TYPE, PROCESSING_RECIPE, key("target"))),
			input(1, ingredient(PROCESSING_TYPE, PROCESSING_RECIPE, key("input"))),
			input(2, result(PROCESSING_TYPE, PROCESSING_RECIPE, key("diamond")))
		);
		TestRecipeLayout layout = layout(
			RecipeType.create("gtceu", "assembler", String.class),
			"processing",
			PROCESSING_RECIPE,
			List.of(item(Items.IRON_INGOT)),
			List.of(item(Items.GOLD_INGOT), item(Items.DIAMOND))
		);

		RecipeChainPatternEncodeRequestFactory.Result result = factory.createRequests(inputs, Set.of(), resolver(layout));

		Assertions.assertEquals(RecipeChainPatternEncodeRequestFactory.Status.OK, result.status());
		JeiPatternEncodeRequest request = result.requests().getFirst();
		Assertions.assertEquals(JeiPatternEncodeMode.PROCESSING, request.mode());
		Assertions.assertNull(request.canonicalRecipeId());
		Assertions.assertEquals(1, request.sparseInputs().size());
		Assertions.assertEquals(2, request.sparseOutputs().size());
		Assertions.assertEquals(Items.GOLD_INGOT, request.sparseOutputs().getFirst().ingredient().getItemStack().orElseThrow().getItem());
	}

	@Test
	public void processingBookmarkRequestDoesNotRestoreInputsMissingFromTheBookmark() {
		RecipeChainPatternEncodeRequestFactory factory = new RecipeChainPatternEncodeRequestFactory(INGREDIENT_MANAGER);
		List<RecipeChainInput> inputs = List.of(
			input(0, result(PROCESSING_TYPE, PROCESSING_RECIPE, key("target"))),
			input(1, ingredient(PROCESSING_TYPE, PROCESSING_RECIPE, key("input")))
		);

		RecipeChainPatternEncodeRequestFactory.Result result = factory.createRequests(
			inputs,
			Set.of(),
			recipeUid -> {
				throw new AssertionError("processing bookmark requests must not resolve the live recipe layout");
			}
		);

		JeiPatternEncodeRequest request = result.requests().getFirst();
		Assertions.assertEquals(List.of(Items.IRON_INGOT), request.sparseInputs().stream()
			.map(stack -> stack.ingredient().getItemStack().orElseThrow().getItem())
			.toList());
		Assertions.assertEquals(Items.GOLD_INGOT, request.sparseOutputs().getFirst().ingredient().getItemStack().orElseThrow().getItem());
	}

	@Test
	public void processingBookmarkRequestKeepsSavedCatalystsInTheirOwnSlots() {
		RecipeChainPatternEncodeRequestFactory factory = new RecipeChainPatternEncodeRequestFactory(INGREDIENT_MANAGER);
		BookmarkItemMetadata catalyst = ingredient(PROCESSING_TYPE, PROCESSING_RECIPE, key("shears"), 3)
			.withType(BookmarkItemType.CATALYST);
		List<RecipeChainInput> inputs = List.of(
			input(0, result(PROCESSING_TYPE, PROCESSING_RECIPE, key("target"))),
			input(1, ingredient(PROCESSING_TYPE, PROCESSING_RECIPE, key("input"))),
			input(2, catalyst)
		);

		JeiPatternEncodeRequest request = factory.createRequests(inputs, Set.of(), recipeUid -> {
			throw new AssertionError("processing bookmark requests must not resolve the live recipe layout");
		}).requests().getFirst();

		Assertions.assertEquals(2, request.sparseInputs().size());
		Assertions.assertEquals(3, request.sparseInputs().get(1).amount());
		Assertions.assertEquals(List.of(new JeiPatternCatalyst(1, request.sparseInputs().get(1))), request.catalysts());
	}

	@Test
	public void processingBookmarkRequestKeepsSavedGtmVirtualCircuitInput() {
		RecipeChainPatternEncodeRequestFactory factory = new RecipeChainPatternEncodeRequestFactory(INGREDIENT_MANAGER);
		List<RecipeChainInput> inputs = List.of(
			input(0, result(PROCESSING_TYPE, PROCESSING_RECIPE, key("target"))),
			input(1, ingredient(PROCESSING_TYPE, PROCESSING_RECIPE, key("input"))),
			input(2, ingredient(PROCESSING_TYPE, PROCESSING_RECIPE, key("repeater")).withType(BookmarkItemType.CATALYST))
		);
		TestRecipeLayout layout = layout(
			RecipeType.create("gtceu", "assembler", GTRecipe.class),
			new GTRecipe(7),
			PROCESSING_RECIPE,
			List.of(item(Items.IRON_INGOT)),
			List.of(item(Items.GOLD_INGOT))
		);

		RecipeChainPatternEncodeRequestFactory.Result result = factory.createRequests(inputs, Set.of(), resolver(layout));

		Assertions.assertEquals(RecipeChainPatternEncodeRequestFactory.Status.OK, result.status());
		JeiPatternEncodeRequest request = result.requests().getFirst();
		Assertions.assertEquals(2, request.sparseInputs().size());
		ItemStack circuit = request.sparseInputs().get(1).ingredient().getItemStack().orElseThrow();
		Assertions.assertEquals(Items.REPEATER, circuit.getItem());
		Assertions.assertEquals(1, circuit.getCount());
		Assertions.assertEquals(7, IntCircuitBehaviour.getCircuitConfiguration(circuit));
		Assertions.assertTrue(request.catalysts().isEmpty());
	}

	@Test
	public void processingBookmarkRequestKeepsGtmCircuitOutOfCatalystMetadata() {
		RecipeChainPatternEncodeRequestFactory factory = new RecipeChainPatternEncodeRequestFactory(INGREDIENT_MANAGER);
		List<RecipeChainInput> inputs = List.of(
			input(0, result(PROCESSING_TYPE, PROCESSING_RECIPE, key("target"))),
			input(1, ingredient(PROCESSING_TYPE, PROCESSING_RECIPE, key("repeater")).withType(BookmarkItemType.CATALYST))
		);
		TestRecipeLayout layout = layout(
			RecipeType.create("gtceu", "assembler", GTRecipe.class),
			new GTRecipe(7),
			PROCESSING_RECIPE,
			List.of(typed(IntCircuitBehaviour.stack(7))),
			List.of(item(Items.GOLD_INGOT))
		);

		RecipeChainPatternEncodeRequestFactory.Result result = factory.createRequests(inputs, Set.of(), resolver(layout));

		Assertions.assertEquals(RecipeChainPatternEncodeRequestFactory.Status.OK, result.status());
		JeiPatternEncodeRequest request = result.requests().getFirst();
		Assertions.assertEquals(1, request.sparseInputs().size());
		Assertions.assertTrue(request.catalysts().isEmpty());
	}

	@Test
	public void savedProgrammedCircuitDoesNotEnterOrdinaryCatalystMetadata() {
		RecipeChainPatternEncodeRequestFactory factory = new RecipeChainPatternEncodeRequestFactory(INGREDIENT_MANAGER);
		List<RecipeChainInput> inputs = List.of(
			input(0, result(PROCESSING_TYPE, PROCESSING_RECIPE, key("target"))),
			input(1, ingredient(PROCESSING_TYPE, PROCESSING_RECIPE, key("repeater"), 0).withType(BookmarkItemType.CATALYST))
		);
		TestRecipeLayout layout = layout(
			RecipeType.create("gtceu", "assembler", GTRecipe.class),
			new GTRecipe(7),
			PROCESSING_RECIPE,
			List.of(typed(IntCircuitBehaviour.stack(7))),
			List.of(item(Items.GOLD_INGOT))
		);

		JeiPatternEncodeRequest request = factory.createRequests(inputs, Set.of(), resolver(layout)).requests().getFirst();

		Assertions.assertTrue(request.catalysts().isEmpty());
	}

	@Test
	public void processingBookmarkRequestMarksSavedNonConsumableInputAsCatalyst() {
		RecipeChainPatternEncodeRequestFactory factory = new RecipeChainPatternEncodeRequestFactory(INGREDIENT_MANAGER);
		ItemStack mold = new ItemStack(Items.SHEARS, 3);
		List<RecipeChainInput> inputs = List.of(
			input(0, result(PROCESSING_TYPE, PROCESSING_RECIPE, key("target"))),
			input(1, ingredient(PROCESSING_TYPE, PROCESSING_RECIPE, key("input"))),
			input(2, ingredient(PROCESSING_TYPE, PROCESSING_RECIPE, key("shears"), 3).withType(BookmarkItemType.CATALYST))
		);
		TestRecipeLayout layout = layout(
			RecipeType.create("gtceu", "assembler", GTRecipe.class),
			new GTRecipe(7, mold),
			PROCESSING_RECIPE,
			List.of(item(Items.IRON_INGOT), typed(mold)),
			List.of(item(Items.GOLD_INGOT))
		);

		RecipeChainPatternEncodeRequestFactory.Result result = factory.createRequests(inputs, Set.of(), resolver(layout));

		Assertions.assertEquals(RecipeChainPatternEncodeRequestFactory.Status.OK, result.status());
		JeiPatternEncodeRequest request = result.requests().getFirst();
		Assertions.assertEquals(1, request.catalysts().size());
		Assertions.assertEquals(1, request.catalysts().getFirst().sourceSlot());
		Assertions.assertEquals(3, request.catalysts().getFirst().stack().amount());
		Assertions.assertEquals(Items.SHEARS, request.catalysts().getFirst().stack().ingredient().getItemStack().orElseThrow().getItem());
	}

	@Test
	public void processingBookmarkRequestUsesSavedNonConsumableInputWithoutLayoutSynthesis() {
		RecipeChainPatternEncodeRequestFactory factory = new RecipeChainPatternEncodeRequestFactory(INGREDIENT_MANAGER);
		ItemStack mold = new ItemStack(Items.SHEARS, 3);
		List<RecipeChainInput> inputs = List.of(
			input(0, result(PROCESSING_TYPE, PROCESSING_RECIPE, key("target"))),
			input(1, ingredient(PROCESSING_TYPE, PROCESSING_RECIPE, key("input"))),
			input(2, ingredient(PROCESSING_TYPE, PROCESSING_RECIPE, key("shears"), 3).withType(BookmarkItemType.CATALYST))
		);
		TestRecipeLayout layout = layout(
			RecipeType.create("gtceu", "assembler", GTRecipe.class),
			new GTRecipe(7, mold),
			PROCESSING_RECIPE,
			List.of(item(Items.IRON_INGOT)),
			List.of(item(Items.GOLD_INGOT))
		);

		RecipeChainPatternEncodeRequestFactory.Result result = factory.createRequests(inputs, Set.of(), resolver(layout));

		Assertions.assertEquals(RecipeChainPatternEncodeRequestFactory.Status.OK, result.status());
		JeiPatternEncodeRequest request = result.requests().getFirst();
		Assertions.assertEquals(2, request.sparseInputs().size());
		Assertions.assertEquals(Items.SHEARS, request.sparseInputs().get(1).ingredient().getItemStack().orElseThrow().getItem());
		Assertions.assertEquals(3, request.sparseInputs().get(1).amount());
		Assertions.assertEquals(List.of(
			new JeiPatternCatalyst(1, request.sparseInputs().get(1))
		), request.catalysts());
	}

	@Test
	public void singleProcessingRequestPreservesRawGtmNonConsumableAmount() {
		RecipeChainPatternEncodeRequestFactory factory = new RecipeChainPatternEncodeRequestFactory(INGREDIENT_MANAGER);
		ItemStack mold = new ItemStack(Items.SHEARS, 3);
		TestRecipeLayout layout = layout(
			RecipeType.create("gtceu", "assembler", GTRecipe.class),
			new GTRecipe(7, mold),
			PROCESSING_RECIPE,
			List.of(item(Items.IRON_INGOT), item(Items.SHEARS)),
			List.of(item(Items.GOLD_INGOT))
		);

		RecipeChainPatternEncodeRequestFactory.Result result = factory.createSingleRequest(layout, Optional.empty());

		Assertions.assertEquals(RecipeChainPatternEncodeRequestFactory.Status.OK, result.status());
		JeiPatternEncodeRequest request = result.requests().getFirst();
		Assertions.assertEquals(4, request.sparseInputs().size());
		Assertions.assertEquals(1, request.sparseInputs().get(1).amount());
		Assertions.assertEquals(3, request.sparseInputs().get(2).amount());
		Assertions.assertEquals(List.of(
			new JeiPatternCatalyst(2, request.sparseInputs().get(2))
		), request.catalysts());
	}

	@Test
	public void processingRequestKeepsDifferentAmountInputSeparateFromGtmCatalyst() {
		RecipeChainPatternEncodeRequestFactory factory = new RecipeChainPatternEncodeRequestFactory(INGREDIENT_MANAGER);
		ItemStack catalyst = new ItemStack(Items.SHEARS, 3);
		TestRecipeLayout layout = layout(
			RecipeType.create("gtceu", "assembler", GTRecipe.class),
			new GTRecipe(7, catalyst),
			PROCESSING_RECIPE,
			List.of(item(Items.IRON_INGOT), item(Items.SHEARS)),
			List.of(item(Items.GOLD_INGOT))
		);

		RecipeChainPatternEncodeRequestFactory.Result result = factory.createSingleRequest(layout, Optional.empty());

		Assertions.assertEquals(RecipeChainPatternEncodeRequestFactory.Status.OK, result.status());
		JeiPatternEncodeRequest request = result.requests().getFirst();
		Assertions.assertEquals(4, request.sparseInputs().size());
		Assertions.assertEquals(1, request.sparseInputs().get(1).amount());
		Assertions.assertEquals(3, request.sparseInputs().get(2).amount());
		Assertions.assertEquals(List.of(
			new JeiPatternCatalyst(2, request.sparseInputs().get(2))
		), request.catalysts());
	}

	@Test
	public void processingRequestUsesSavedBookmarkCatalystAndAmount() {
		RecipeChainPatternEncodeRequestFactory factory = new RecipeChainPatternEncodeRequestFactory(INGREDIENT_MANAGER);
		List<RecipeChainInput> inputs = List.of(
			input(0, result(PROCESSING_TYPE, PROCESSING_RECIPE, key("target"))),
			input(1, ingredient(PROCESSING_TYPE, PROCESSING_RECIPE, key("iron_ingot"), 5).withType(BookmarkItemType.CATALYST))
		);
		TestRecipeLayout layout = layout(
			RecipeType.create("gtceu", "assembler", String.class),
			"processing",
			PROCESSING_RECIPE,
			List.of(item(Items.IRON_INGOT)),
			List.of(item(Items.GOLD_INGOT))
		);

		RecipeChainPatternEncodeRequestFactory.Result result = factory.createRequests(inputs, Set.of(), resolver(layout));

		Assertions.assertEquals(RecipeChainPatternEncodeRequestFactory.Status.OK, result.status());
		JeiPatternEncodeRequest request = result.requests().getFirst();
		Assertions.assertEquals(5, request.sparseInputs().getFirst().amount());
		Assertions.assertEquals(List.of(new JeiPatternCatalyst(0, request.sparseInputs().getFirst())), request.catalysts());
	}

	@Test
	public void singleProcessingRequestUsesSavedBookmarkCatalystAndAmount() {
		RecipeChainPatternEncodeRequestFactory factory = new RecipeChainPatternEncodeRequestFactory(INGREDIENT_MANAGER);
		List<RecipeChainInput> inputs = List.of(
			input(0, result(PROCESSING_TYPE, PROCESSING_RECIPE, key("target"))),
			input(1, ingredient(PROCESSING_TYPE, PROCESSING_RECIPE, key("iron_ingot"), 5).withType(BookmarkItemType.CATALYST))
		);
		TestRecipeLayout layout = layout(
			RecipeType.create("gtceu", "assembler", String.class),
			"processing",
			PROCESSING_RECIPE,
			List.of(item(Items.IRON_INGOT)),
			List.of(item(Items.GOLD_INGOT))
		);

		RecipeChainPatternEncodeRequestFactory.Result result = factory.createSingleRequest(layout, Optional.empty(), inputs);

		Assertions.assertEquals(RecipeChainPatternEncodeRequestFactory.Status.OK, result.status());
		JeiPatternEncodeRequest request = result.requests().getFirst();
		Assertions.assertEquals(5, request.sparseInputs().getFirst().amount());
		Assertions.assertEquals(List.of(new JeiPatternCatalyst(0, request.sparseInputs().getFirst())), request.catalysts());
	}

	@Test
	public void singleCraftingRecipeRequestUsesCanonicalRecipeIdWithoutBookmarkSlots() {
		RecipeChainPatternEncodeRequestFactory factory = new RecipeChainPatternEncodeRequestFactory(INGREDIENT_MANAGER);
		TestRecipeLayout layout = layout(
			RecipeTypes.CRAFTING,
			craftingRecipeHolder(CRAFTING_RECIPE),
			CRAFTING_RECIPE,
			Arrays.asList(item(Items.STICK), null, item(Items.COBBLESTONE), null, null, null, null, null, null),
			List.of(item(Items.DIAMOND))
		);

		RecipeChainPatternEncodeRequestFactory.Result result = factory.createSingleRequest(layout, Optional.empty());

		Assertions.assertEquals(RecipeChainPatternEncodeRequestFactory.Status.OK, result.status());
		JeiPatternEncodeRequest request = result.requests().getFirst();
		Assertions.assertEquals(JeiPatternEncodeMode.CRAFTING, request.mode());
		Assertions.assertEquals(CRAFTING_RECIPE, request.canonicalRecipeId());
		Assertions.assertTrue(request.sparseInputs().isEmpty());
		Assertions.assertTrue(request.sparseOutputs().isEmpty());
		Assertions.assertTrue(request.catalysts().isEmpty());
	}

	@Test
	public void stonecuttingRequestUsesItsCanonicalRecipeIdInsteadOfBookmarkSlots() {
		RecipeChainPatternEncodeRequestFactory factory = new RecipeChainPatternEncodeRequestFactory(INGREDIENT_MANAGER);
		TestRecipeLayout layout = layout(
			RecipeTypes.STONECUTTING,
			"stonecutting",
			PROCESSING_RECIPE,
			List.of(item(Items.STONE)),
			List.of(item(Items.STONE_BRICKS))
		);

		JeiPatternEncodeRequest request = factory.createSingleRequest(layout, Optional.empty()).requests().getFirst();

		Assertions.assertEquals(JeiPatternEncodeMode.STONECUTTING, request.mode());
		Assertions.assertEquals(PROCESSING_RECIPE, request.canonicalRecipeId());
		Assertions.assertTrue(request.sparseInputs().isEmpty());
		Assertions.assertTrue(request.sparseOutputs().isEmpty());
		Assertions.assertTrue(request.catalysts().isEmpty());
		Assertions.assertEquals(Items.STONE, request.canonicalInputGuides().getFirst().ingredient().getItemStack().orElseThrow().getItem());
	}

	@Test
	public void singleProcessingRecipeRequestPutsHoveredOutputFirst() {
		RecipeChainPatternEncodeRequestFactory factory = new RecipeChainPatternEncodeRequestFactory(INGREDIENT_MANAGER);
		TestRecipeLayout layout = layout(
			RecipeType.create("gtceu", "assembler", String.class),
			"processing",
			PROCESSING_RECIPE,
			List.of(item(Items.IRON_INGOT)),
			List.of(item(Items.GOLD_INGOT), item(Items.DIAMOND))
		);
		IRecipeSlotView hoveredOutput = new TestRecipeSlotView(RecipeIngredientRole.OUTPUT, item(Items.DIAMOND));

		RecipeChainPatternEncodeRequestFactory.Result result = factory.createSingleRequest(layout, Optional.of(hoveredOutput));

		Assertions.assertEquals(RecipeChainPatternEncodeRequestFactory.Status.OK, result.status());
		JeiPatternEncodeRequest request = result.requests().getFirst();
		Assertions.assertEquals(JeiPatternEncodeMode.PROCESSING, request.mode());
		Assertions.assertEquals(2, request.sparseOutputs().size());
		Assertions.assertEquals(Items.DIAMOND, request.sparseOutputs().getFirst().ingredient().getItemStack().orElseThrow().getItem());
	}

	@Test
	public void singleProcessingRecipeRequestKeepsOutputOrderWhenHoveringInput() {
		RecipeChainPatternEncodeRequestFactory factory = new RecipeChainPatternEncodeRequestFactory(INGREDIENT_MANAGER);
		TestRecipeLayout layout = layout(
			RecipeType.create("gtceu", "assembler", String.class),
			"processing",
			PROCESSING_RECIPE,
			List.of(item(Items.IRON_INGOT)),
			List.of(item(Items.GOLD_INGOT), item(Items.DIAMOND))
		);
		IRecipeSlotView hoveredInput = new TestRecipeSlotView(RecipeIngredientRole.INPUT, item(Items.IRON_INGOT));

		RecipeChainPatternEncodeRequestFactory.Result result = factory.createSingleRequest(layout, Optional.of(hoveredInput));

		Assertions.assertEquals(RecipeChainPatternEncodeRequestFactory.Status.OK, result.status());
		JeiPatternEncodeRequest request = result.requests().getFirst();
		Assertions.assertEquals(Items.GOLD_INGOT, request.sparseOutputs().getFirst().ingredient().getItemStack().orElseThrow().getItem());
	}

	@Test
	public void duplicateRecipesAreDeduplicatedInChainOrder() {
		RecipeChainPatternEncodeRequestFactory factory = new RecipeChainPatternEncodeRequestFactory(INGREDIENT_MANAGER);
		List<RecipeChainInput> inputs = List.of(
			input(0, result(PROCESSING_TYPE, PROCESSING_RECIPE, key("target"))),
			input(1, result(PROCESSING_TYPE, PROCESSING_RECIPE, key("target"))),
			input(2, result(PROCESSING_TYPE, OTHER_RECIPE, key("other"))),
			input(3, ingredient(PROCESSING_TYPE, PROCESSING_RECIPE, key("input"))),
			input(4, ingredient(PROCESSING_TYPE, OTHER_RECIPE, key("input")))
		);
		TestRecipeLayout first = layout(RecipeType.create("gtceu", "assembler", String.class), "first", PROCESSING_RECIPE, List.of(item(Items.STICK)), List.of(item(Items.GOLD_INGOT)));
		TestRecipeLayout second = layout(RecipeType.create("gtceu", "assembler", String.class), "second", OTHER_RECIPE, List.of(item(Items.STICK)), List.of(item(Items.DIAMOND)));

		RecipeChainPatternEncodeRequestFactory.Result result = factory.createRequests(inputs, Set.of(), recipeUid -> {
			if (PROCESSING_RECIPE.equals(recipeUid)) {
				return Optional.of(first);
			}
			if (OTHER_RECIPE.equals(recipeUid)) {
				return Optional.of(second);
			}
			return Optional.empty();
		});

		Assertions.assertEquals(List.of(PROCESSING_RECIPE, OTHER_RECIPE), result.requests().stream()
			.map(JeiPatternEncodeRequest::recipeUid)
			.toList());
	}

	@Test
	public void unsupportedIngredientSkipsRecipe() {
		RecipeChainPatternEncodeRequestFactory factory = new RecipeChainPatternEncodeRequestFactory(INGREDIENT_MANAGER);
		List<RecipeChainInput> inputs = List.of(
			input(0, result(PROCESSING_TYPE, PROCESSING_RECIPE, key("target"))),
			new RecipeChainInput(1, ingredient(PROCESSING_TYPE, PROCESSING_RECIPE, key("input")), null, unsupported("input"))
		);
		TestRecipeLayout layout = layout(
			RecipeType.create("gtceu", "assembler", String.class),
			"processing",
			PROCESSING_RECIPE,
			List.of(unsupported("input")),
			List.of(item(Items.DIAMOND))
		);

		RecipeChainPatternEncodeRequestFactory.Result result = factory.createRequests(inputs, Set.of(), resolver(layout));

		Assertions.assertEquals(RecipeChainPatternEncodeRequestFactory.Status.EMPTY, result.status());
		Assertions.assertTrue(result.requests().isEmpty());
	}

	@Test
	public void requestCountIsLimitedToAe2PacketLimit() {
		RecipeChainPatternEncodeRequestFactory factory = new RecipeChainPatternEncodeRequestFactory(INGREDIENT_MANAGER);
		List<RecipeChainInput> inputs = new ArrayList<>();
		for (int i = 0; i < 257; i++) {
			ResourceLocation recipeUid = ResourceLocation.fromNamespaceAndPath("test", "recipe_" + i);
			inputs.add(input(i, result(PROCESSING_TYPE, recipeUid, key("target_" + i))));
			inputs.add(input(1_000 + i, ingredient(PROCESSING_TYPE, recipeUid, key("input"))));
		}
		TestRecipeLayout layout = layout(
			RecipeType.create("gtceu", "assembler", String.class),
			"processing",
			PROCESSING_RECIPE,
			List.of(item(Items.STICK)),
			List.of(item(Items.DIAMOND))
		);

		RecipeChainPatternEncodeRequestFactory.Result result = factory.createRequests(inputs, Set.of(), recipeUid -> Optional.of(layout.withRecipeUid(recipeUid)));

		Assertions.assertEquals(RecipeChainPatternEncodeRequestFactory.Status.TOO_MANY_REQUESTS, result.status());
		Assertions.assertTrue(result.requests().isEmpty());
	}

	@Test
	public void controllerHandlesSimulatedInputWithoutSending() {
		TestBridge bridge = new TestBridge(true, true);
		TestKeyMapping keyMapping = new TestKeyMapping(GLFW.GLFW_KEY_Q);
		RecipeChainPatternEncodeController.HandleResult result = RecipeChainPatternEncodeController.handle(
			input(InputType.SIMULATE),
			keyMapping,
			new TestMenu(),
			Optional.of("group"),
			true,
			bridge,
			() -> List.of(
				input(0, result(PROCESSING_TYPE, PROCESSING_RECIPE, key("target"))),
				input(1, ingredient(PROCESSING_TYPE, PROCESSING_RECIPE, key("input")))
			),
			() -> Set.of(),
			resolver(layout(RecipeType.create("gtceu", "assembler", String.class), "processing", PROCESSING_RECIPE, List.of(item(Items.STICK)), List.of(item(Items.DIAMOND)))),
			INGREDIENT_MANAGER,
			message -> {
			}
		).orElseThrow();

		Assertions.assertTrue(result.handled());
		Assertions.assertFalse(result.sent());
		Assertions.assertEquals(0, bridge.sentRequests.size());
	}

	@Test
	public void controllerSendsRealInputWhenBridgeAndGroupMatch() {
		TestBridge bridge = new TestBridge(true, true);
		TestKeyMapping keyMapping = new TestKeyMapping(GLFW.GLFW_KEY_Q);

		RecipeChainPatternEncodeController.HandleResult result = RecipeChainPatternEncodeController.handle(
			input(InputType.EXECUTE),
			keyMapping,
			new TestMenu(),
			Optional.of("group"),
			true,
			bridge,
			() -> List.of(
				input(0, result(PROCESSING_TYPE, PROCESSING_RECIPE, key("target"))),
				input(1, ingredient(PROCESSING_TYPE, PROCESSING_RECIPE, key("input")))
			),
			() -> Set.of(),
			resolver(layout(RecipeType.create("gtceu", "assembler", String.class), "processing", PROCESSING_RECIPE, List.of(item(Items.STICK)), List.of(item(Items.DIAMOND)))),
			INGREDIENT_MANAGER,
			message -> {
			}
		).orElseThrow();

		Assertions.assertTrue(result.handled());
		Assertions.assertTrue(result.sent());
		Assertions.assertEquals(1, bridge.sentRequests.size());
	}

	@Test
	public void controllerSendsChainRequestForNonCraftingModeGroupWhenAllowed() {
		TestBridge bridge = new TestBridge(true, true);
		TestKeyMapping keyMapping = new TestKeyMapping(GLFW.GLFW_KEY_Q);

		RecipeChainPatternEncodeController.HandleResult result = RecipeChainPatternEncodeController.handle(
			input(InputType.EXECUTE),
			keyMapping,
			new TestMenu(),
			Optional.of("group"),
			false,
			true,
			bridge,
			() -> List.of(
				input(0, result(PROCESSING_TYPE, PROCESSING_RECIPE, key("target"))),
				input(1, ingredient(PROCESSING_TYPE, PROCESSING_RECIPE, key("input")))
			),
			() -> Set.of(),
			resolver(layout(RecipeType.create("gtceu", "assembler", String.class), "processing", PROCESSING_RECIPE, List.of(item(Items.STICK)), List.of(item(Items.DIAMOND)))),
			INGREDIENT_MANAGER,
			message -> {
			}
		).orElseThrow();

		Assertions.assertTrue(result.handled());
		Assertions.assertTrue(result.sent());
		Assertions.assertEquals(1, bridge.sentRequests.size());
	}

	@Test
	public void controllerSendsSingleRecipeRequestWhenBridgeAndTerminalMatch() {
		TestBridge bridge = new TestBridge(true, true);
		TestKeyMapping keyMapping = new TestKeyMapping(GLFW.GLFW_KEY_Q);
		TestRecipeLayout layout = layout(
			RecipeType.create("gtceu", "assembler", String.class),
			"processing",
			PROCESSING_RECIPE,
			List.of(item(Items.STICK)),
			List.of(item(Items.DIAMOND))
		);

		RecipeChainPatternEncodeController.HandleResult result = RecipeChainPatternEncodeController.handleSingleRecipe(
			input(InputType.EXECUTE),
			keyMapping,
			new TestMenu(),
			bridge,
			Optional.of(layout),
			Optional.of(new TestRecipeSlotView(RecipeIngredientRole.INPUT, item(Items.STICK))),
			INGREDIENT_MANAGER,
			message -> {
			}
		).orElseThrow();

		Assertions.assertTrue(result.handled());
		Assertions.assertTrue(result.sent());
		Assertions.assertEquals(List.of(PROCESSING_RECIPE), bridge.sentRequests.stream()
			.map(JeiPatternEncodeRequest::recipeUid)
			.toList());
	}

	@Test
	public void controllerSendsSingleRecipeRequestWithSavedCatalyst() {
		TestBridge bridge = new TestBridge(true, true);
		TestKeyMapping keyMapping = new TestKeyMapping(GLFW.GLFW_KEY_Q);
		TestRecipeLayout layout = layout(
			RecipeType.create("gtceu", "assembler", String.class),
			"processing",
			PROCESSING_RECIPE,
			List.of(item(Items.IRON_INGOT)),
			List.of(item(Items.DIAMOND))
		);
		List<RecipeChainInput> savedInputs = List.of(
			input(0, result(PROCESSING_TYPE, PROCESSING_RECIPE, key("target"))),
			input(1, ingredient(PROCESSING_TYPE, PROCESSING_RECIPE, key("iron_ingot"), 5).withType(BookmarkItemType.CATALYST))
		);

		RecipeChainPatternEncodeController.HandleResult result = RecipeChainPatternEncodeController.handleSingleRecipe(
			input(InputType.EXECUTE),
			keyMapping,
			new TestMenu(),
			bridge,
			Optional.of(layout),
			Optional.empty(),
			() -> savedInputs,
			INGREDIENT_MANAGER,
			message -> {
			}
		).orElseThrow();

		Assertions.assertTrue(result.sent());
		JeiPatternEncodeRequest request = bridge.sentRequests.getFirst();
		Assertions.assertEquals(5, request.sparseInputs().getFirst().amount());
		Assertions.assertEquals(List.of(new JeiPatternCatalyst(0, request.sparseInputs().getFirst())), request.catalysts());
	}

	@Test
	public void controllerIgnoresUnavailableBridgeAndNonTerminalMenus() {
		TestKeyMapping keyMapping = new TestKeyMapping(GLFW.GLFW_KEY_Q);
		Assertions.assertTrue(RecipeChainPatternEncodeController.handle(
			input(InputType.EXECUTE),
			keyMapping,
			new TestMenu(),
			Optional.of("group"),
			true,
			new TestBridge(false, true),
			List::of,
			Set::of,
			recipeUid -> Optional.empty(),
			INGREDIENT_MANAGER,
			message -> {
			}
		).isEmpty());
		Assertions.assertTrue(RecipeChainPatternEncodeController.handle(
			input(InputType.EXECUTE),
			keyMapping,
			new TestMenu(),
			Optional.of("group"),
			true,
			new TestBridge(true, false),
			List::of,
			Set::of,
			recipeUid -> Optional.empty(),
			INGREDIENT_MANAGER,
			message -> {
			}
		).isEmpty());
	}

	private static Function<ResourceLocation, Optional<IRecipeLayoutDrawable<?>>> resolver(IRecipeLayoutDrawable<?> layout) {
		return recipeUid -> Optional.of(layout);
	}

	private static UserInput input(InputType inputType) {
		return new UserInput(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_Q), 0, 0, GLFW.GLFW_MOD_CONTROL, inputType);
	}

	private static RecipeChainInput input(int index, BookmarkItemMetadata metadata) {
		return new RecipeChainInput(index, metadata, null, bookmarkedIngredient(metadata));
	}

	private static ITypedIngredient<ItemStack> bookmarkedIngredient(BookmarkItemMetadata metadata) {
		String uid = metadata.permutations().stream().findFirst().orElseThrow().ingredientUid();
		return switch (uid) {
			case "minecraft:input", "minecraft:iron_ingot" -> item(Items.IRON_INGOT);
			case "minecraft:target", "minecraft:gold_ingot" -> item(Items.GOLD_INGOT);
			case "minecraft:other", "minecraft:diamond", "minecraft:result" -> item(Items.DIAMOND);
			case "minecraft:repeater" -> typed(IntCircuitBehaviour.stack(7));
			case "minecraft:shears" -> item(Items.SHEARS);
			default -> item(Items.STICK);
		};
	}

	private static BookmarkItemMetadata result(ResourceLocation recipeTypeUid, ResourceLocation recipeUid, BookmarkIngredientKey key) {
		return metadata(recipeTypeUid, recipeUid, BookmarkItemType.RESULT, key);
	}

	private static BookmarkItemMetadata ingredient(ResourceLocation recipeTypeUid, ResourceLocation recipeUid, BookmarkIngredientKey key) {
		return metadata(recipeTypeUid, recipeUid, BookmarkItemType.INGREDIENT, key);
	}

	private static BookmarkItemMetadata ingredient(ResourceLocation recipeTypeUid, ResourceLocation recipeUid, BookmarkIngredientKey key, long factor) {
		BookmarkItemMetadata metadata = metadata(recipeTypeUid, recipeUid, BookmarkItemType.INGREDIENT, key);
		return new BookmarkItemMetadata(
			metadata.groupId(),
			metadata.type(),
			metadata.multiplier(),
			factor,
			metadata.chance(),
			metadata.recipeTypeUid(),
			metadata.recipeUid(),
			metadata.permutations()
		);
	}

	private static BookmarkItemMetadata metadata(ResourceLocation recipeTypeUid, ResourceLocation recipeUid, BookmarkItemType type, BookmarkIngredientKey key) {
		return new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			type,
			1,
			1,
			BookmarkItemMetadata.CHANCE_FULL,
			recipeTypeUid,
			recipeUid,
			Set.of(key)
		);
	}

	private static BookmarkIngredientKey key(String uid) {
		return BookmarkIngredientKey.of(VanillaTypes.ITEM_STACK.getUid(), "minecraft:" + uid);
	}

	private static ITypedIngredient<ItemStack> item(net.minecraft.world.level.ItemLike item) {
		return typed(new ItemStack(item));
	}

	private static ITypedIngredient<ItemStack> typed(ItemStack stack) {
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

	private static ITypedIngredient<UnsupportedIngredient> unsupported(String name) {
		return new ITypedIngredient<>() {
			@Override
			public IIngredientType<UnsupportedIngredient> getType() {
				return UnsupportedIngredient.TYPE;
			}

			@Override
			public UnsupportedIngredient getIngredient() {
				return new UnsupportedIngredient(name);
			}
		};
	}

	private static TestRecipeLayout layout(
		RecipeType<?> recipeType,
		Object recipe,
		ResourceLocation recipeUid,
		List<@Nullable ITypedIngredient<?>> inputs,
		List<@Nullable ITypedIngredient<?>> outputs
	) {
		return new TestRecipeLayout(
			new TestRecipeCategory(recipeType, recipe, recipeUid),
			recipe,
			inputs.stream().map(ingredient -> new TestRecipeSlotView(RecipeIngredientRole.INPUT, ingredient)).toList(),
			outputs.stream().map(ingredient -> new TestRecipeSlotView(RecipeIngredientRole.OUTPUT, ingredient)).toList()
		);
	}

	private static RecipeHolder<ShapelessRecipe> craftingRecipeHolder(ResourceLocation recipeUid) {
		NonNullList<Ingredient> ingredients = NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STICK));
		ShapelessRecipe recipe = new ShapelessRecipe("test", CraftingBookCategory.MISC, new ItemStack(Items.DIAMOND), ingredients);
		return new RecipeHolder<>(recipeUid, recipe);
	}

	private static IIngredientManager ingredientManager() {
		return (IIngredientManager) java.lang.reflect.Proxy.newProxyInstance(
			RecipeChainPatternEncodeRequestFactoryTest.class.getClassLoader(),
			new Class<?>[]{IIngredientManager.class},
			(proxy, method, args) -> {
				if ("getIngredientHelper".equals(method.getName()) && args[0] == VanillaTypes.ITEM_STACK) {
					return itemHelper();
				}
				if ("createTypedIngredient".equals(method.getName()) && args[0] == VanillaTypes.ITEM_STACK) {
					return Optional.of(typed((ItemStack) args[1]));
				}
				if ("normalizeTypedIngredient".equals(method.getName())) {
					return args[0];
				}
				throw new UnsupportedOperationException(method.getName());
			}
		);
	}

	private static IIngredientHelper<ItemStack> itemHelper() {
		return new IIngredientHelper<>() {
			@Override
			public IIngredientType<ItemStack> getIngredientType() {
				return VanillaTypes.ITEM_STACK;
			}

			@Override
			public String getDisplayName(ItemStack ingredient) {
				return ingredient.getHoverName().getString();
			}

			@Override
			public String getUniqueId(ItemStack ingredient, UidContext context) {
				return BuiltInItemName.get(ingredient);
			}

			@Override
			public ResourceLocation getResourceLocation(ItemStack ingredient) {
				return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ingredient.getItem());
			}

			@Override
			public ItemStack copyIngredient(ItemStack ingredient) {
				return ingredient.copy();
			}

			@Override
			public String getErrorInfo(ItemStack ingredient) {
				return ingredient.toString();
			}
		};
	}

	private record TestRecipeCategory(RecipeType<?> recipeType, Object recipe, ResourceLocation recipeUid) implements IRecipeCategory<Object> {
		@Override
		@SuppressWarnings("unchecked")
		public RecipeType<Object> getRecipeType() {
			return (RecipeType<Object>) recipeType;
		}

		@Override
		public Component getTitle() {
			return Component.literal(recipeType.getUid().toString());
		}

		@Override
		public @Nullable mezz.jei.api.gui.drawable.IDrawable getIcon() {
			return null;
		}

		@Override
		public void setRecipe(mezz.jei.api.gui.builder.IRecipeLayoutBuilder builder, Object recipe, IFocusGroup focuses) {
		}

		@Override
		public @Nullable ResourceLocation getRegistryName(Object recipe) {
			return recipeUid;
		}
	}

	private record TestRecipeLayout(
		TestRecipeCategory category,
		Object recipe,
		List<TestRecipeSlotView> inputs,
		List<TestRecipeSlotView> outputs
	) implements IRecipeLayoutDrawable<Object> {
		private TestRecipeLayout withRecipeUid(ResourceLocation recipeUid) {
			return new TestRecipeLayout(new TestRecipeCategory(category.recipeType(), recipe, recipeUid), recipe, inputs, outputs);
		}

		@Override
		public void setPosition(int posX, int posY) {
		}

		@Override
		public void drawRecipe(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY) {
		}

		@Override
		public void drawOverlays(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY) {
		}

		@Override
		public boolean isMouseOver(double mouseX, double mouseY) {
			return true;
		}

		@Override
		public <T> Optional<T> getIngredientUnderMouse(int mouseX, int mouseY, IIngredientType<T> ingredientType) {
			return Optional.empty();
		}

		@Override
		public Optional<IRecipeSlotDrawable> getRecipeSlotUnderMouse(double mouseX, double mouseY) {
			return Optional.empty();
		}

		@Override
		public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
			return Optional.empty();
		}

		@Override
		public Rect2i getRect() {
			return new Rect2i(0, 0, 1, 1);
		}

		@Override
		public Rect2i getRectWithBorder() {
			return getRect();
		}

		@Override
		public Rect2i getSideButtonArea(int buttonIndex) {
			return getRect();
		}

		@Override
		public IRecipeSlotsView getRecipeSlotsView() {
			return () -> Stream.concat(
					inputs.stream(),
					outputs.stream()
				)
				.map(IRecipeSlotView.class::cast)
				.toList();
		}

		@Override
		public IRecipeCategory<Object> getRecipeCategory() {
			return category;
		}

		@Override
		public Object getRecipe() {
			return recipe;
		}

		@Override
		public IJeiInputHandler getInputHandler() {
			return () -> ScreenRectangle.empty();
		}

		@Override
		public void tick() {
		}
	}

	private static TestRecipeSlotView slot(List<ITypedIngredient<?>> ingredients, @Nullable ITypedIngredient<?> displayed) {
		return new TestRecipeSlotView(RecipeIngredientRole.INPUT, ingredients, displayed);
	}

	private record TestRecipeSlotView(
		RecipeIngredientRole role,
		List<ITypedIngredient<?>> ingredients,
		@Nullable ITypedIngredient<?> displayed
	) implements IRecipeSlotView {
		private TestRecipeSlotView(RecipeIngredientRole role, @Nullable ITypedIngredient<?> ingredient) {
			this(role, ingredient == null ? List.of() : List.of(ingredient), ingredient);
		}

		@Override
		public Stream<ITypedIngredient<?>> getAllIngredients() {
			return ingredients.stream();
		}

		@Override
		public List<@Nullable ITypedIngredient<?>> getAllIngredientsList() {
			return List.copyOf(ingredients);
		}

		@Override
		public Optional<ITypedIngredient<?>> getDisplayedIngredient() {
			return Optional.ofNullable(displayed);
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
	}

	private record UnsupportedIngredient(String name) {
		private static final IIngredientType<UnsupportedIngredient> TYPE = new IIngredientType<>() {
			@Override
			public Class<? extends UnsupportedIngredient> getIngredientClass() {
				return UnsupportedIngredient.class;
			}

			@Override
			public String getUid() {
				return "test:unsupported";
			}
		};
	}

	private static final class TestBridge implements Ae2RecipeChainPatternEncodingBridge {
		private final boolean available;
		private final boolean terminal;
		private final List<JeiPatternEncodeRequest> sentRequests = new ArrayList<>();

		private TestBridge(boolean available, boolean terminal) {
			this.available = available;
			this.terminal = terminal;
		}

		@Override
		public boolean isAvailable() {
			return available;
		}

		@Override
		public boolean isPatternEncodingTerminal(AbstractContainerMenu menu) {
			return terminal;
		}

		@Override
		public boolean sendRequests(AbstractContainerMenu menu, List<JeiPatternEncodeRequest> requests) {
			sentRequests.addAll(requests);
			return true;
		}
	}

	private static final class TestKeyMapping implements IJeiKeyMapping {
		private final int key;

		private TestKeyMapping(int key) {
			this.key = key;
		}

		@Override
		public boolean isActiveAndMatches(InputConstants.Key key) {
			return key.getType() == InputConstants.Type.KEYSYM && key.getValue() == this.key;
		}

		@Override
		public boolean isUnbound() {
			return false;
		}

		@Override
		public Component getTranslatedKeyMessage() {
			return Component.literal("Ctrl+Q");
		}
	}

	private static final class TestMenu extends AbstractContainerMenu {
		private TestMenu() {
			super((MenuType<?>) null, 0);
		}

		@Override
		public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) {
			return ItemStack.EMPTY;
		}

		@Override
		public boolean stillValid(net.minecraft.world.entity.player.Player player) {
			return true;
		}
	}

	private static final class BuiltInItemName {
		private static String get(ItemStack stack) {
			ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
			return key == null ? "minecraft:air" : key.toString();
		}
	}
}
