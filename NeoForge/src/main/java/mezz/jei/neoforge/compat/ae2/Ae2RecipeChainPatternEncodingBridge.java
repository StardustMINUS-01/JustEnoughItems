package mezz.jei.neoforge.compat.ae2;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.common.Internal;
import mezz.jei.gui.compat.ae2.JeiPatternCatalyst;
import mezz.jei.gui.compat.ae2.JeiPatternEncodeRequest;
import mezz.jei.gui.compat.ae2.JeiPatternStack;
import mezz.jei.neoforge.compat.ae2.patternencoding.JeiPatternCatalystWire;
import mezz.jei.neoforge.compat.ae2.patternencoding.JeiPatternEncodeEntryResult;
import mezz.jei.neoforge.compat.ae2.patternencoding.JeiPatternEncodeEntryStatus;
import mezz.jei.neoforge.compat.ae2.patternencoding.JeiPatternEncodeRequestWire;
import mezz.jei.neoforge.compat.ae2.patternencoding.PacketEncodeRecipeChainPatterns;
import mezz.jei.neoforge.compat.ae2.patternencoding.PacketPatternEncodingResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Ae2RecipeChainPatternEncodingBridge implements mezz.jei.gui.compat.ae2.Ae2RecipeChainPatternEncodingBridge {
	private static final Logger LOGGER = LogManager.getLogger();

	private final Access access;
	private boolean selectedCraftingTransferAvailable = true;

	public Ae2RecipeChainPatternEncodingBridge(Access access) {
		this.access = access;
	}

	public static Optional<Ae2RecipeChainPatternEncodingBridge> createIfLoaded() {
		if (!isAe2Loaded()) {
			return Optional.empty();
		}
		try {
			return Optional.of(new Ae2RecipeChainPatternEncodingBridge(new DirectAccess()));
		} catch (LinkageError e) {
			LOGGER.warn("Failed to initialize AE2 recipe chain pattern encoding bridge", e);
			return Optional.empty();
		}
	}

	private static boolean isAe2Loaded() {
		try {
			Class.forName("appeng.api.stacks.GenericStack", false, Ae2RecipeChainPatternEncodingBridge.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException | LinkageError e) {
			return false;
		}
	}

	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public boolean isPatternEncodingTerminal(AbstractContainerMenu menu) {
		return access.isPatternEncodingTerminal(menu);
	}

	@Override
	public boolean transferSelectedCraftingRecipe(
		AbstractContainerMenu menu,
		Object recipe,
		mezz.jei.api.recipe.RecipeType<?> recipeType,
		IRecipeSlotsView slotsView
	) {
		if (!selectedCraftingTransferAvailable || !isPatternEncodingTerminal(menu)) {
			return false;
		}
		try {
			return access.transferSelectedCraftingRecipe(menu, recipe, recipeType, slotsView);
		} catch (LinkageError e) {
			// AE2's internal encoding helper may move or change signature between runtime versions.
			selectedCraftingTransferAvailable = false;
			LOGGER.warn("Disabling selected-candidate AE2 pattern transfer because AE2 internals are incompatible", e);
			return false;
		}
	}

	@Override
	public SendResult sendRequests(AbstractContainerMenu menu, List<JeiPatternEncodeRequest> requests) {
		if (requests.isEmpty() || !isPatternEncodingTerminal(menu)) {
			return SendResult.UNAVAILABLE;
		}
		return access.sendRequests(menu, requests);
	}

	private static List<List<GenericStack>> createCraftingIngredients(IRecipeSlotsView slotsView) {
		List<IRecipeSlotView> inputSlots = slotsView.getSlotViews(RecipeIngredientRole.INPUT);
		if (inputSlots.size() != 9) {
			return List.of();
		}
		List<List<GenericStack>> ingredients = new ArrayList<>(9);
		for (int i = 0; i < 9; i++) {
			List<GenericStack> slotIngredients = inputSlots.get(i)
				.getIngredients(VanillaTypes.ITEM_STACK)
				.map(GenericStack::fromItemStack)
				.filter(Objects::nonNull)
				.toList();
			ingredients.add(slotIngredients);
		}
		return ingredients;
	}

	public interface Access {
		boolean isPatternEncodingTerminal(AbstractContainerMenu menu);

		default boolean transferSelectedCraftingRecipe(
			AbstractContainerMenu menu,
			Object recipe,
			mezz.jei.api.recipe.RecipeType<?> recipeType,
			IRecipeSlotsView slotsView
		) {
			return false;
		}

		SendResult sendRequests(AbstractContainerMenu menu, List<JeiPatternEncodeRequest> requests);
	}

	static SendResult convertAndSend(List<JeiPatternEncodeRequest> requests,
		BiConsumer<List<JeiPatternEncodeRequestWire>, List<JeiPatternEncodeEntryResult>> send,
		Consumer<PacketPatternEncodingResult> reportInvalid) {
		List<JeiPatternEncodeRequestWire> converted = new ArrayList<>(requests.size());
		List<JeiPatternEncodeEntryResult> failures = new ArrayList<>();
		for (var request : requests) {
			try {
				converted.add(DirectAccess.convert(request));
			} catch (RuntimeException e) {
				failures.add(new JeiPatternEncodeEntryResult(request.recipeTypeUid(), request.recipeUid(), JeiPatternEncodeEntryStatus.SKIPPED_INVALID_RECIPE));
				LOGGER.warn("Failed to convert recipe chain pattern request {}", request.recipeUid(), e);
			}
		}
		if (converted.isEmpty()) {
			reportInvalid.accept(PacketPatternEncodingResult.invalidRecipes(failures));
			return SendResult.INVALID_RECIPES;
		}
		send.accept(converted, failures);
		return SendResult.SENT;
	}

	private static final class DirectAccess implements Access {
		@Override
		public boolean isPatternEncodingTerminal(AbstractContainerMenu menu) {
			return menu instanceof PatternEncodingTermMenu;
		}

		@Override
		public boolean transferSelectedCraftingRecipe(
			AbstractContainerMenu menu,
			Object recipe,
			mezz.jei.api.recipe.RecipeType<?> recipeType,
			IRecipeSlotsView slotsView
		) {
			if (!(menu instanceof PatternEncodingTermMenu patternMenu) ||
				!RecipeTypes.CRAFTING.equals(recipeType) ||
				!(recipe instanceof RecipeHolder<?> recipeHolder) ||
				!(recipeHolder.value() instanceof CraftingRecipe craftingRecipe) ||
				craftingRecipe.getType() != RecipeType.CRAFTING
			) {
				return false;
			}
			List<List<GenericStack>> ingredients = createCraftingIngredients(slotsView);
			if (ingredients.isEmpty()) {
				return false;
			}
			EncodingHelper.encodeCraftingRecipe(patternMenu, null, ingredients, stack -> true);
			return true;
		}

		@Override
		public SendResult sendRequests(AbstractContainerMenu menu, List<JeiPatternEncodeRequest> requests) {
			var connection = Internal.getServerConnection();
			if (!connection.canSendPacket(PacketEncodeRecipeChainPatterns.TYPE)) {
				return SendResult.UNAVAILABLE;
			}
			return convertAndSend(requests, (converted, failures) -> {
				boolean supportsFailures = failures.isEmpty() || connection.canSendPacket(PacketEncodeRecipeChainPatterns.WITH_FAILURES);
				var packet = PacketEncodeRecipeChainPatterns.create(converted, failures, supportsFailures);
				PacketDistributor.sendToServer(packet);
			}, PacketPatternEncodingResult::show);
		}

		private static JeiPatternEncodeRequestWire convert(JeiPatternEncodeRequest request) {
			return new JeiPatternEncodeRequestWire(
				request.recipeTypeUid(),
				request.recipeUid(),
				request.mode(),
				convertStacks(request.sparseInputs()),
				convertStacks(request.sparseOutputs()),
				convertCatalysts(request.catalysts()),
				convertStacks(request.canonicalInputGuides()),
				request.canonicalRecipeId(),
				request.allowSubstitution(),
				request.allowFluidSubstitution()
			);
		}

		private static List<@Nullable GenericStack> convertStacks(List<@Nullable JeiPatternStack> stacks) {
			List<@Nullable GenericStack> converted = new ArrayList<>(stacks.size());
			for (JeiPatternStack stack : stacks) {
				converted.add(convertStack(stack));
			}
			return converted;
		}

		private static @Nullable GenericStack convertStack(@Nullable JeiPatternStack stack) {
			if (stack == null) {
				return null;
			}
			return switch (stack.kind()) {
				case ITEM -> {
					Optional<ItemStack> itemStack = stack.ingredient().getItemStack();
					if (itemStack.isEmpty() || itemStack.get().isEmpty()) {
						yield null;
					}
					yield new GenericStack(AEItemKey.of(itemStack.get()), stack.amount());
				}
				case FLUID -> {
					ITypedIngredient<FluidStack> fluidIngredient = stack.ingredient().cast(NeoForgeTypes.FLUID_STACK);
					if (fluidIngredient == null || fluidIngredient.getIngredient().isEmpty()) {
						yield null;
					}
					yield new GenericStack(AEFluidKey.of(fluidIngredient.getIngredient()), stack.amount());
				}
			};
		}

		private static List<JeiPatternCatalystWire> convertCatalysts(List<JeiPatternCatalyst> catalysts) {
			List<JeiPatternCatalystWire> converted = new ArrayList<>(catalysts.size());
			for (JeiPatternCatalyst catalyst : catalysts) {
				GenericStack stack = convertStack(catalyst.stack());
				if (stack == null) {
					throw new IllegalArgumentException("Unsupported JEI catalyst ingredient");
				}
				converted.add(new JeiPatternCatalystWire(catalyst.sourceSlot(), stack));
			}
			return List.copyOf(converted);
		}
	}
}
