package mezz.jei.neoforge.compat.ae2;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.gui.compat.ae2.JeiPatternCatalyst;
import mezz.jei.gui.compat.ae2.JeiPatternEncodeRequest;
import mezz.jei.gui.compat.ae2.JeiPatternStack;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
		try {
			return Optional.of(new Ae2RecipeChainPatternEncodingBridge(new ReflectionAccess()));
		} catch (ReflectiveOperationException | LinkageError e) {
			LOGGER.warn("Failed to initialize AE2 recipe chain pattern encoding bridge", e);
			return Optional.empty();
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

		List<Object> convertedRequests = new ArrayList<>(requests.size());
		for (JeiPatternEncodeRequest request : requests) {
			try {
				convertedRequests.add(access.createRequest(request));
			} catch (ReflectiveOperationException | RuntimeException e) {
				LOGGER.warn("Failed to convert recipe chain pattern request {}", request.recipeUid(), e);
			}
		}
		if (convertedRequests.isEmpty()) {
			return false;
		}

		try {
			CustomPacketPayload packet = access.createPacket(convertedRequests);
			PacketDistributor.sendToServer(packet);
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.warn("Failed to send AE2 recipe chain pattern encoding packet", e);
			return false;
		}
	}

	public interface Access {
		boolean isPatternEncodingTerminal(AbstractContainerMenu menu);

		Object createRequest(JeiPatternEncodeRequest request) throws ReflectiveOperationException;

		CustomPacketPayload createPacket(List<Object> requests) throws ReflectiveOperationException;
	}

	private static final class ReflectionAccess implements Access {
		private static final String PATTERN_ENCODING_TERM_MENU = "appeng.menu.me.items.PatternEncodingTermMenu";
		private static final String PATTERN_ENCODE_REQUEST = "appeng.integration.jei.patternencoding.PatternEncodeRequest";
		private static final String PATTERN_ENCODE_MODE = "appeng.integration.jei.patternencoding.PatternEncodeMode";
		private static final String PATTERN_CATALYST = "appeng.crafting.pattern.PatternCatalyst";
		private static final String SERVERBOUND_PACKET = "appeng.core.network.serverbound.ServerboundEncodeRecipeChainPatternsPacket";
		private static final String GENERIC_STACK = "appeng.api.stacks.GenericStack";
		private static final String AE_KEY = "appeng.api.stacks.AEKey";
		private static final String AE_ITEM_KEY = "appeng.api.stacks.AEItemKey";
		private static final String AE_FLUID_KEY = "appeng.api.stacks.AEFluidKey";

		private final Class<?> menuClass;
		private final Class<?> modeClass;
		private final Constructor<?> requestConstructor;
		private final Constructor<?> packetConstructor;
		private final Constructor<?> genericStackConstructor;
		private final Constructor<?> catalystConstructor;
		private final Method itemKeyOfMethod;
		private final Method fluidKeyOfMethod;

		private ReflectionAccess() throws ReflectiveOperationException {
			this.menuClass = Class.forName(PATTERN_ENCODING_TERM_MENU);
			Class<?> requestClass = Class.forName(PATTERN_ENCODE_REQUEST);
			this.modeClass = Class.forName(PATTERN_ENCODE_MODE);
			Class<?> packetClass = Class.forName(SERVERBOUND_PACKET);
			Class<?> genericStackClass = Class.forName(GENERIC_STACK);
			Class<?> catalystClass = Class.forName(PATTERN_CATALYST);
			Class<?> aeKeyClass = Class.forName(AE_KEY);
			Class<?> itemKeyClass = Class.forName(AE_ITEM_KEY);
			Class<?> fluidKeyClass = Class.forName(AE_FLUID_KEY);

			this.requestConstructor = requestClass.getConstructor(
				ResourceLocation.class,
				ResourceLocation.class,
				modeClass,
				List.class,
				List.class,
				List.class,
				List.class,
				ResourceLocation.class,
				boolean.class,
				boolean.class
			);
			this.packetConstructor = packetClass.getConstructor(List.class);
			this.genericStackConstructor = genericStackClass.getConstructor(aeKeyClass, long.class);
			this.catalystConstructor = catalystClass.getConstructor(int.class, genericStackClass);
			this.itemKeyOfMethod = itemKeyClass.getMethod("of", ItemStack.class);
			this.fluidKeyOfMethod = fluidKeyClass.getMethod("of", FluidStack.class);
		}

		@Override
		public boolean isPatternEncodingTerminal(AbstractContainerMenu menu) {
			return menuClass.isInstance(menu);
		}

		@Override
		public Object createRequest(JeiPatternEncodeRequest request) throws ReflectiveOperationException {
			return requestConstructor.newInstance(
				request.recipeTypeUid(),
				request.recipeUid(),
				getMode(request.mode().name()),
				createGenericStacks(request.sparseInputs()),
				createGenericStacks(request.sparseOutputs()),
				createCatalysts(request.catalysts()),
				createGenericStacks(request.canonicalInputGuides()),
				request.canonicalRecipeId(),
				request.allowSubstitution(),
				request.allowFluidSubstitution()
			);
		}

		private List<Object> createCatalysts(List<JeiPatternCatalyst> catalysts) throws ReflectiveOperationException {
			List<Object> converted = new ArrayList<>(catalysts.size());
			for (JeiPatternCatalyst catalyst : catalysts) {
				Object stack = createGenericStack(catalyst.stack());
				if (stack == null) {
					throw new IllegalArgumentException("Unsupported JEI catalyst ingredient");
				}
				converted.add(catalystConstructor.newInstance(catalyst.sourceSlot(), stack));
			}
			return List.copyOf(converted);
		}

		@Override
		public CustomPacketPayload createPacket(List<Object> requests) throws ReflectiveOperationException {
			Object packet = packetConstructor.newInstance(requests);
			if (packet instanceof CustomPacketPayload payload) {
				return payload;
			}
			throw new ClassCastException("AE2 pattern encoding packet is not a CustomPacketPayload");
		}

		private Object getMode(String name) {
			@SuppressWarnings({"unchecked", "rawtypes"})
			Object mode = Enum.valueOf((Class<? extends Enum>) modeClass.asSubclass(Enum.class), name);
			return mode;
		}

		private List<@Nullable Object> createGenericStacks(List<@Nullable JeiPatternStack> stacks) throws ReflectiveOperationException {
			List<@Nullable Object> genericStacks = new ArrayList<>(stacks.size());
			for (JeiPatternStack stack : stacks) {
				genericStacks.add(createGenericStack(stack));
			}
			return genericStacks;
		}

		private @Nullable Object createGenericStack(@Nullable JeiPatternStack stack) throws ReflectiveOperationException {
			if (stack == null) {
				return null;
			}
			Object key = switch (stack.kind()) {
				case ITEM -> createItemKey(stack.ingredient());
				case FLUID -> createFluidKey(stack.ingredient());
			};
			if (key == null) {
				throw new IllegalArgumentException("Unsupported JEI pattern stack ingredient: " + stack.ingredient().getType().getUid());
			}
			return genericStackConstructor.newInstance(key, stack.amount());
		}

		private @Nullable Object createItemKey(ITypedIngredient<?> ingredient) throws InvocationTargetException, IllegalAccessException {
			Optional<ItemStack> itemStack = ingredient.getItemStack();
			if (itemStack.isEmpty() || itemStack.get().isEmpty()) {
				return null;
			}
			return itemKeyOfMethod.invoke(null, itemStack.get());
		}

		private @Nullable Object createFluidKey(ITypedIngredient<?> ingredient) throws InvocationTargetException, IllegalAccessException {
			ITypedIngredient<FluidStack> fluidIngredient = ingredient.cast(NeoForgeTypes.FLUID_STACK);
			if (fluidIngredient == null || fluidIngredient.getIngredient().isEmpty()) {
				return null;
			}
			return fluidKeyOfMethod.invoke(null, fluidIngredient.getIngredient());
		}
	}
}
