package mezz.jei.forge.compat.ae2;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.me.items.PatternEncodingTermMenu;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.common.Internal;
import mezz.jei.gui.compat.ae2.JeiPatternCatalyst;
import mezz.jei.gui.compat.ae2.JeiPatternEncodeRequest;
import mezz.jei.gui.compat.ae2.JeiPatternStack;
import mezz.jei.forge.compat.ae2.patternencoding.JeiPatternCatalystWire;
import mezz.jei.forge.compat.ae2.patternencoding.JeiPatternEncodeRequestWire;
import mezz.jei.forge.compat.ae2.patternencoding.PacketEncodeRecipeChainPatterns;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Ae2RecipeChainPatternEncodingBridge implements mezz.jei.gui.compat.ae2.Ae2RecipeChainPatternEncodingBridge {
	private static final Logger LOGGER = LogManager.getLogger();

	private final Access access;

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
	public boolean sendRequests(AbstractContainerMenu menu, List<JeiPatternEncodeRequest> requests) {
		if (requests.isEmpty() || !isPatternEncodingTerminal(menu)) {
			return false;
		}
		return access.sendRequests(menu, requests);
	}

	public interface Access {
		boolean isPatternEncodingTerminal(AbstractContainerMenu menu);

		boolean sendRequests(AbstractContainerMenu menu, List<JeiPatternEncodeRequest> requests);
	}

	private static final class DirectAccess implements Access {
		@Override
		public boolean isPatternEncodingTerminal(AbstractContainerMenu menu) {
			return menu instanceof PatternEncodingTermMenu;
		}

		@Override
		public boolean sendRequests(AbstractContainerMenu menu, List<JeiPatternEncodeRequest> requests) {
			List<JeiPatternEncodeRequestWire> convertedRequests = new ArrayList<>(requests.size());
			for (JeiPatternEncodeRequest request : requests) {
				try {
					convertedRequests.add(convert(request));
				} catch (RuntimeException e) {
					LOGGER.warn("Failed to convert recipe chain pattern request {}", request.recipeUid(), e);
				}
			}
			if (convertedRequests.isEmpty()) {
				return false;
			}
			Internal.getServerConnection().sendPacketToServer(new PacketEncodeRecipeChainPatterns(convertedRequests));
			return true;
		}

		private JeiPatternEncodeRequestWire convert(JeiPatternEncodeRequest request) {
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

		private List<@Nullable GenericStack> convertStacks(List<@Nullable JeiPatternStack> stacks) {
			List<@Nullable GenericStack> converted = new ArrayList<>(stacks.size());
			for (JeiPatternStack stack : stacks) {
				converted.add(convertStack(stack));
			}
			return converted;
		}

		private @Nullable GenericStack convertStack(@Nullable JeiPatternStack stack) {
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
					ITypedIngredient<FluidStack> fluidIngredient = stack.ingredient().cast(ForgeTypes.FLUID_STACK);
					if (fluidIngredient == null || fluidIngredient.getIngredient().isEmpty()) {
						yield null;
					}
					yield new GenericStack(AEFluidKey.of(fluidIngredient.getIngredient()), stack.amount());
				}
			};
		}

		private List<JeiPatternCatalystWire> convertCatalysts(List<JeiPatternCatalyst> catalysts) {
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
