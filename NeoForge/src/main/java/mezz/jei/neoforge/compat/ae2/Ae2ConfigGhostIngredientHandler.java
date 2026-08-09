package mezz.jei.neoforge.compat.ae2;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.AEBaseScreen;
import appeng.integration.modules.itemlists.DropTarget;
import appeng.integration.modules.itemlists.DropTargets;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;

import java.util.List;

public class Ae2ConfigGhostIngredientHandler<T extends AEBaseScreen<?>> implements IGhostIngredientHandler<T> {
	@Override
	public <I> List<Target<I>> getTargetsTyped(T gui, ITypedIngredient<I> ingredient, boolean doStart) {
		GenericStack stack = Ae2GroupDropCompat.toGenericStack(ingredient);
		if (stack == null) {
			return List.of();
		}
		return DropTargets.getTargets(gui).stream()
			.filter(target -> target.canDrop(stack))
			.map(target -> (Target<I>) (Target<?>) new DropTargetTarget<>(target, ingredient))
			.toList();
	}

	@Override
	public void onComplete() {
	}

	private record DropTargetTarget<I>(DropTarget dropTarget, ITypedIngredient<I> ingredient) implements Target<I> {
		@Override
		public Rect2i getArea() {
			return dropTarget.area();
		}

		@Override
		public void accept(I ignored) {
			GenericStack stack = Ae2GroupDropCompat.toGenericStack(ingredient);
			if (stack != null) {
				dropTarget.drop(stack);
			}
		}
	}
}
