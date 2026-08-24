package mezz.jei.forge.compat.ae2;

import appeng.client.gui.widgets.AETextField;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler.Target;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public class JeiSearchTextGhostIngredientHandler<T extends Screen> implements IGhostIngredientHandler<T> {
	@Override
	public <I> List<Target<I>> getTargetsTyped(T gui, ITypedIngredient<I> ingredient, boolean doStart) {
		Optional<String> searchText = toSearchText(ingredient);
		if (searchText.isEmpty()) {
			return List.of();
		}
		Optional<AETextField> searchField = findSearchField(gui);
		if (searchField.isEmpty() || !searchField.get().isTooltipAreaVisible()) {
			return List.of();
		}
		Rect2i area = searchField.get().getTooltipArea();
		return List.of(new SearchTextTarget<>(area, searchField.get(), gui, searchText.get()));
	}

	@Override
	public void onComplete() {
	}

	private static Optional<AETextField> findSearchField(Screen gui) {
		return gui.children().stream()
			.filter(AETextField.class::isInstance)
			.map(AETextField.class::cast)
			.findFirst();
	}

	private static Optional<String> toSearchText(ITypedIngredient<?> ingredient) {
		return ingredient.getItemStack()
			.filter(stack -> !stack.isEmpty())
			.map(ItemStack::getHoverName)
			.map(name -> name.getString().trim())
			.filter(text -> !text.isEmpty());
	}

	private record SearchTextTarget<I>(
		Rect2i area,
		AETextField searchField,
		Screen gui,
		String searchText
	) implements Target<I> {
		@Override
		public Rect2i getArea() {
			return area;
		}

		@Override
		public void accept(I ingredient) {
			searchField.setValue(searchText);
			searchField.setCursorPosition(0);
			searchField.setHighlightPos(0);
			gui.setFocused(searchField);
		}
	}
}
