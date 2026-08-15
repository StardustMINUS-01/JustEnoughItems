package mezz.jei.neoforge.compat.ae2;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.AEBaseScreen;
import appeng.integration.modules.itemlists.DropTarget;
import appeng.integration.modules.itemlists.DropTargets;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

final class Ae2GroupDropCompatInternal {
	private Ae2GroupDropCompatInternal() {
	}

	static void register() {
		BookmarkOverlay.setGroupDropHandler(Ae2GroupDropCompatInternal::dropGroup);
		BookmarkOverlay.setGroupDropAvailabilityProvider(Ae2GroupDropCompatInternal::hasDropTargets);
		BookmarkOverlay.setGroupDropHighlightProvider(Ae2GroupDropCompatInternal::getDropAreas);
	}

	static boolean hasDropTargets(List<ITypedIngredient<?>> ingredients) {
		Screen screen = Minecraft.getInstance().screen;
		if (!(screen instanceof AEBaseScreen<?> aeScreen)) {
			return false;
		}
		List<DropTarget> targets = DropTargets.getTargets(aeScreen);
		if (targets.isEmpty()) {
			return false;
		}
		return ingredients.stream().anyMatch(ingredient -> {
			GenericStack stack = toGenericStack(ingredient);
			return stack != null && targets.stream().anyMatch(target -> target.canDrop(stack));
		});
	}

	private static boolean dropGroup(List<ITypedIngredient<?>> ingredients, double mouseX, double mouseY) {
		Screen screen = Minecraft.getInstance().screen;
		if (!(screen instanceof AEBaseScreen<?> aeScreen)) {
			return false;
		}
		List<DropTarget> targets = DropTargets.getTargets(aeScreen);
		if (targets.isEmpty()) {
			return false;
		}
		int startIndex = -1;
		for (int i = 0; i < targets.size(); i++) {
			if (targets.get(i).area().contains((int) mouseX, (int) mouseY)) {
				startIndex = i;
				break;
			}
		}
		if (startIndex < 0) {
			return false;
		}
		Set<DropTarget> usedTargets = Collections.newSetFromMap(new IdentityHashMap<>());
		boolean droppedAny = false;
		for (ITypedIngredient<?> ingredient : ingredients) {
			GenericStack stack = toGenericStack(ingredient);
			if (stack == null) {
				continue;
			}
			for (int i = startIndex; i < targets.size(); i++) {
				DropTarget target = targets.get(i);
				if (!usedTargets.contains(target) && target.canDrop(stack)) {
					target.drop(stack);
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
		if (!(screen instanceof AEBaseScreen<?> aeScreen)) {
			return List.of();
		}
		return DropTargets.getTargets(aeScreen).stream()
			.filter(target -> ingredients.stream().anyMatch(ingredient -> {
				GenericStack stack = toGenericStack(ingredient);
				return stack != null && target.canDrop(stack);
			}))
			.map(DropTarget::area)
			.toList();
	}

	@Nullable
	static GenericStack toGenericStack(ITypedIngredient<?> ingredient) {
		ItemStack itemStack = ingredient.getItemStack().orElse(ItemStack.EMPTY);
		if (!itemStack.isEmpty()) {
			return new GenericStack(AEItemKey.of(itemStack), 1);
		}
		ITypedIngredient<FluidStack> fluidIngredient = ingredient.cast(NeoForgeTypes.FLUID_STACK);
		if (fluidIngredient != null && !fluidIngredient.getIngredient().isEmpty()) {
			return new GenericStack(AEFluidKey.of(fluidIngredient.getIngredient()), 1);
		}
		return null;
	}
}
