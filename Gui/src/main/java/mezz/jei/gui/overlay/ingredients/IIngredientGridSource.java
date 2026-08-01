package mezz.jei.gui.overlay.ingredients;

import mezz.jei.gui.overlay.elements.IElement;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public interface IIngredientGridSource {
	@Unmodifiable
	List<IElement<?>> getElements();

	@Unmodifiable
	default List<IElement<?>> getElements(int columns) {
		return getElements();
	}

	default boolean isEmpty() {
		return getElements().isEmpty();
	}

	void addSourceListChangedListener(SourceListChangedListener listener);

	interface SourceListChangedListener {
		void onSourceListChanged();
	}
}
