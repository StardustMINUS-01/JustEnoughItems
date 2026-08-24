package mezz.jei.forge.compat.ae2;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.AEBaseScreen;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;

public class Ae2ConfigGhostIngredientHandler<T extends AEBaseScreen> implements IGhostIngredientHandler<T> {
	private static final Logger LOGGER = LogManager.getLogger();

	@Override
	public <I> List<Target<I>> getTargetsTyped(T gui, ITypedIngredient<I> ingredient, boolean doStart) {
		GenericStack stack = toGenericStack(ingredient);
		if (stack == null || !ReflectionDropTargets.isAvailable()) {
			return List.of();
		}
		try {
			List<?> targets = ReflectionDropTargets.getTargets(gui);
			return targets.stream()
				.filter(target -> ReflectionDropTargets.canDrop(target, stack))
				.map(target -> (Target<I>) (Target<?>) new DropTargetTarget<>(target, ingredient))
				.toList();
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.debug("Failed to query AE2 drop targets", e);
			return List.of();
		}
	}

	@Override
	public void onComplete() {
	}

	private static @Nullable GenericStack toGenericStack(ITypedIngredient<?> ingredient) {
		ItemStack itemStack = ingredient.getItemStack().orElse(ItemStack.EMPTY);
		if (!itemStack.isEmpty()) {
			return new GenericStack(AEItemKey.of(itemStack), 1);
		}
		ITypedIngredient<FluidStack> fluidIngredient = ingredient.cast(ForgeTypes.FLUID_STACK);
		if (fluidIngredient != null && !fluidIngredient.getIngredient().isEmpty()) {
			return new GenericStack(AEFluidKey.of(fluidIngredient.getIngredient()), 1);
		}
		return null;
	}

	private record DropTargetTarget<I>(Object dropTarget, ITypedIngredient<I> ingredient) implements Target<I> {
		@Override
		public Rect2i getArea() {
			return ReflectionDropTargets.getArea(dropTarget);
		}

		@Override
		public void accept(I ignored) {
			GenericStack stack = toGenericStack(ingredient);
			if (stack != null) {
				ReflectionDropTargets.drop(dropTarget, stack);
			}
		}
	}

	private static final class ReflectionDropTargets {
		private static final String[] DROP_TARGET_PACKAGES = {
			"appeng.integration.modules.itemlists",
			"appeng.integration.modules.jeirei"
		};

		private static final @Nullable Class<?> dropTargetsClass;
		private static final @Nullable Method getTargetsMethod;
		private static final @Nullable Method areaMethod;
		private static final @Nullable Method canDropMethod;
		private static final @Nullable Method dropMethod;

		static {
			Class<?> dropTargets = null;
			Method getTargets = null;
			Method area = null;
			Method canDrop = null;
			Method drop = null;
			for (String packageName : DROP_TARGET_PACKAGES) {
				try {
					Class<?> dropTargetsCandidate = Class.forName(packageName + ".DropTargets");
					Class<?> dropTargetCandidate = Class.forName(packageName + ".DropTarget");
					Method getTargetsCandidate = dropTargetsCandidate.getMethod("getTargets", AEBaseScreen.class);
					Method areaCandidate = dropTargetCandidate.getMethod("area");
					Method canDropCandidate = dropTargetCandidate.getMethod("canDrop", GenericStack.class);
					Method dropCandidate = dropTargetCandidate.getMethod("drop", GenericStack.class);
					dropTargets = dropTargetsCandidate;
					getTargets = getTargetsCandidate;
					area = areaCandidate;
					canDrop = canDropCandidate;
					drop = dropCandidate;
					break;
				} catch (Exception | LinkageError e) {
					LOGGER.debug("AE2 DropTargets API not found in package {}", packageName, e);
				}
			}
			if (dropTargets == null) {
				LOGGER.debug("AE2 DropTargets API (appeng.integration.modules.itemlists / appeng.integration.modules.jeirei) is not available in this AE2 version; AE2 config ghost drop is disabled");
			}
			dropTargetsClass = dropTargets;
			getTargetsMethod = getTargets;
			areaMethod = area;
			canDropMethod = canDrop;
			dropMethod = drop;
		}

		static boolean isAvailable() {
			return dropTargetsClass != null
				&& getTargetsMethod != null
				&& areaMethod != null
				&& canDropMethod != null
				&& dropMethod != null;
		}

		static List<?> getTargets(AEBaseScreen gui) throws ReflectiveOperationException {
			Object result = getTargetsMethod.invoke(null, gui);
			if (result instanceof List<?> list) {
				return list;
			}
			return List.of();
		}

		static boolean canDrop(Object dropTarget, GenericStack stack) {
			try {
				Object result = canDropMethod.invoke(dropTarget, stack);
				return result instanceof Boolean bool && bool;
			} catch (ReflectiveOperationException | RuntimeException e) {
				LOGGER.debug("Failed to check AE2 drop target", e);
				return false;
			}
		}

		static Rect2i getArea(Object dropTarget) {
			try {
				Object result = areaMethod.invoke(dropTarget);
				if (result instanceof Rect2i rect2i) {
					return rect2i;
				}
			} catch (ReflectiveOperationException | RuntimeException e) {
				LOGGER.debug("Failed to get AE2 drop target area", e);
			}
			return new Rect2i(0, 0, 0, 0);
		}

		static void drop(Object dropTarget, GenericStack stack) {
			try {
				dropMethod.invoke(dropTarget, stack);
			} catch (ReflectiveOperationException | RuntimeException e) {
				LOGGER.debug("Failed to drop ghost ingredient on AE2 drop target", e);
			}
		}
	}
}
