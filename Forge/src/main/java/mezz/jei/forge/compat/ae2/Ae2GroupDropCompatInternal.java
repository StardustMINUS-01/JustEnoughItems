package mezz.jei.forge.compat.ae2;

import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Reflection-based bridge that drops bookmark groups onto AE2 1.20.1 (15.4.x) terminal slots.
 * Ported from JEI 1.21.1 (neoforge Ae2GroupDropCompatInternal.java). Unlike the 1.21.1 original,
 * every AE2 type (AEBaseScreen, DropTargets/DropTarget, GenericStack, AEItemKey, AEFluidKey) is
 * accessed through reflection, so this file never hard-compiles against AE2 and safely no-ops when
 * AE2 is absent or its DropTargets API differs.
 */
final class Ae2GroupDropCompatInternal {
	private static final Logger LOGGER = LogManager.getLogger();

	private static final String AE_BASE_SCREEN = "appeng.client.gui.AEBaseScreen";
	private static final String DROP_TARGETS = "appeng.integration.modules.itemlists.DropTargets";
	private static final String DROP_TARGET = "appeng.integration.modules.itemlists.DropTarget";
	private static final String GENERIC_STACK = "appeng.api.stacks.GenericStack";
	private static final String AE_KEY = "appeng.api.stacks.AEKey";
	private static final String AE_ITEM_KEY = "appeng.api.stacks.AEItemKey";
	private static final String AE_FLUID_KEY = "appeng.api.stacks.AEFluidKey";

	private static final @Nullable Class<?> aeBaseScreenClass;
	private static final @Nullable Class<?> dropTargetsClass;
	private static final @Nullable Method getTargetsMethod;
	private static final @Nullable Method areaMethod;
	private static final @Nullable Method canDropMethod;
	private static final @Nullable Method dropMethod;
	private static final @Nullable Method aeItemKeyOfMethod;
	private static final @Nullable Method aeFluidKeyOfMethod;
	private static final @Nullable Constructor<?> genericStackConstructor;

	static {
		Class<?> baseScreen = null;
		Class<?> dropTargets = null;
		Method getTargets = null;
		Method area = null;
		Method canDrop = null;
		Method drop = null;
		Method itemKeyOf = null;
		Method fluidKeyOf = null;
		Constructor<?> stackConstructor = null;
		try {
			baseScreen = Class.forName(AE_BASE_SCREEN);
			dropTargets = Class.forName(DROP_TARGETS);
			Class<?> dropTarget = Class.forName(DROP_TARGET);
			Class<?> genericStack = Class.forName(GENERIC_STACK);
			Class<?> aeKey = Class.forName(AE_KEY);
			Class<?> aeItemKey = Class.forName(AE_ITEM_KEY);
			Class<?> aeFluidKey = Class.forName(AE_FLUID_KEY);
			getTargets = dropTargets.getMethod("getTargets", baseScreen);
			area = dropTarget.getMethod("area");
			canDrop = dropTarget.getMethod("canDrop", genericStack);
			drop = dropTarget.getMethod("drop", genericStack);
			itemKeyOf = aeItemKey.getMethod("of", ItemStack.class);
			fluidKeyOf = aeFluidKey.getMethod("of", FluidStack.class);
			stackConstructor = genericStack.getConstructor(aeKey, long.class);
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			LOGGER.debug("AE2 DropTargets API not available; bookmark group drop onto AE2 terminals is disabled", e);
			baseScreen = null;
			dropTargets = null;
			getTargets = null;
			area = null;
			canDrop = null;
			drop = null;
			itemKeyOf = null;
			fluidKeyOf = null;
			stackConstructor = null;
		}
		aeBaseScreenClass = baseScreen;
		dropTargetsClass = dropTargets;
		getTargetsMethod = getTargets;
		areaMethod = area;
		canDropMethod = canDrop;
		dropMethod = drop;
		aeItemKeyOfMethod = itemKeyOf;
		aeFluidKeyOfMethod = fluidKeyOf;
		genericStackConstructor = stackConstructor;
	}

	private Ae2GroupDropCompatInternal() {
	}

	static void register() {
		BookmarkOverlay.setGroupDropHandler(Ae2GroupDropCompatInternal::dropGroup);
		BookmarkOverlay.setGroupDropAvailabilityProvider(Ae2GroupDropCompatInternal::hasDropTargets);
		BookmarkOverlay.setGroupDropHighlightProvider(Ae2GroupDropCompatInternal::getDropAreas);
	}

	static boolean hasDropTargets(List<ITypedIngredient<?>> ingredients) {
		Screen screen = Minecraft.getInstance().screen;
		if (!isAvailable() || !aeBaseScreenClass.isInstance(screen)) {
			return false;
		}
		List<Object> targets = getTargets(screen);
		if (targets.isEmpty()) {
			return false;
		}
		return ingredients.stream().anyMatch(ingredient -> {
			Object stack = toGenericStack(ingredient);
			return stack != null && targets.stream().anyMatch(target -> canDrop(target, stack));
		});
	}

	private static boolean dropGroup(List<ITypedIngredient<?>> ingredients, double mouseX, double mouseY) {
		Screen screen = Minecraft.getInstance().screen;
		if (!isAvailable() || !aeBaseScreenClass.isInstance(screen)) {
			return false;
		}
		List<Object> targets = getTargets(screen);
		if (targets.isEmpty()) {
			return false;
		}
		int startIndex = -1;
		for (int i = 0; i < targets.size(); i++) {
			Rect2i targetArea = getArea(targets.get(i));
			if (targetArea != null && targetArea.contains((int) mouseX, (int) mouseY)) {
				startIndex = i;
				break;
			}
		}
		if (startIndex < 0) {
			return false;
		}
		Set<Object> usedTargets = Collections.newSetFromMap(new IdentityHashMap<>());
		boolean droppedAny = false;
		for (ITypedIngredient<?> ingredient : ingredients) {
			Object stack = toGenericStack(ingredient);
			if (stack == null) {
				continue;
			}
			for (int i = startIndex; i < targets.size(); i++) {
				Object target = targets.get(i);
				if (!usedTargets.contains(target) && canDrop(target, stack)) {
					drop(target, stack);
					usedTargets.add(target);
					droppedAny = true;
					break;
				}
			}
		}
		return droppedAny;
	}

	private static List<Rect2i> getDropAreas(List<ITypedIngredient<?>> ingredients) {
		Screen screen = Minecraft.getInstance().screen;
		if (!isAvailable() || !aeBaseScreenClass.isInstance(screen)) {
			return List.of();
		}
		List<Object> targets = getTargets(screen);
		List<Rect2i> areas = new ArrayList<>(targets.size());
		for (Object target : targets) {
			boolean canDropAny = ingredients.stream().anyMatch(ingredient -> {
				Object stack = toGenericStack(ingredient);
				return stack != null && canDrop(target, stack);
			});
			if (canDropAny) {
				Rect2i area = getArea(target);
				if (area != null) {
					areas.add(area);
				}
			}
		}
		return areas;
	}

	private static boolean isAvailable() {
		return aeBaseScreenClass != null &&
			dropTargetsClass != null &&
			getTargetsMethod != null &&
			areaMethod != null &&
			canDropMethod != null &&
			dropMethod != null &&
			aeItemKeyOfMethod != null &&
			aeFluidKeyOfMethod != null &&
			genericStackConstructor != null;
	}

	private static List<Object> getTargets(Screen screen) {
		try {
			Object result = getTargetsMethod.invoke(null, screen);
			if (result instanceof List<?> list) {
				return new ArrayList<>(list);
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.debug("Failed to query AE2 drop targets", e);
		}
		return List.of();
	}

	private static @Nullable Rect2i getArea(Object dropTarget) {
		try {
			Object result = areaMethod.invoke(dropTarget);
			if (result instanceof Rect2i rect2i) {
				return rect2i;
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.debug("Failed to get AE2 drop target area", e);
		}
		return null;
	}

	private static boolean canDrop(Object dropTarget, Object stack) {
		try {
			Object result = canDropMethod.invoke(dropTarget, stack);
			return result instanceof Boolean bool && bool;
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.debug("Failed to query AE2 drop target canDrop", e);
			return false;
		}
	}

	private static void drop(Object dropTarget, Object stack) {
		try {
			dropMethod.invoke(dropTarget, stack);
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.debug("Failed to drop bookmark group ingredient on AE2 drop target", e);
		}
	}

	private static @Nullable Object toGenericStack(ITypedIngredient<?> ingredient) {
		if (genericStackConstructor == null) {
			return null;
		}
		ItemStack itemStack = ingredient.getItemStack().orElse(ItemStack.EMPTY);
		if (!itemStack.isEmpty() && aeItemKeyOfMethod != null) {
			try {
				Object key = aeItemKeyOfMethod.invoke(null, itemStack);
				return genericStackConstructor.newInstance(key, 1L);
			} catch (ReflectiveOperationException | RuntimeException e) {
				LOGGER.debug("Failed to build AE2 GenericStack from item", e);
				return null;
			}
		}
		ITypedIngredient<FluidStack> fluidIngredient = ingredient.cast(ForgeTypes.FLUID_STACK);
		if (fluidIngredient != null && !fluidIngredient.getIngredient().isEmpty() && aeFluidKeyOfMethod != null) {
			try {
				Object key = aeFluidKeyOfMethod.invoke(null, fluidIngredient.getIngredient());
				return genericStackConstructor.newInstance(key, 1L);
			} catch (ReflectiveOperationException | RuntimeException e) {
				LOGGER.debug("Failed to build AE2 GenericStack from fluid", e);
				return null;
			}
		}
		return null;
	}
}
