package mezz.jei.gui.recipes.navigation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RecipeNavigationHistory<T> {
	public static final int DEFAULT_CAPACITY = 128;

	private final List<T> entries = new ArrayList<>();
	private int cursor = -1;

	public void push(T entry) {
		if (canNavigate(RecipeNavigationDirection.FORWARD)) {
			entries.subList(cursor + 1, entries.size()).clear();
		}
		entries.add(entry);
		cursor = entries.size() - 1;
		if (entries.size() > DEFAULT_CAPACITY) {
			entries.removeFirst();
			cursor--;
		}
	}

	public Optional<T> current() {
		return entryAt(cursor);
	}

	public Optional<T> peek(RecipeNavigationDirection direction) {
		return entryAt(cursor + getOffset(direction));
	}

	public boolean canNavigate(RecipeNavigationDirection direction) {
		return switch (direction) {
			case BACK -> cursor > 0;
			case FORWARD -> cursor >= 0 && cursor + 1 < entries.size();
		};
	}

	public Optional<T> navigate(RecipeNavigationDirection direction, boolean jumpToEnd) {
		if (!canNavigate(direction)) {
			return Optional.empty();
		}
		cursor = jumpToEnd ? getEndpoint(direction) : cursor + getOffset(direction);
		return current();
	}

	public void clear() {
		entries.clear();
		cursor = -1;
	}

	private int getEndpoint(RecipeNavigationDirection direction) {
		return direction == RecipeNavigationDirection.BACK ? 0 : entries.size() - 1;
	}

	private static int getOffset(RecipeNavigationDirection direction) {
		return direction == RecipeNavigationDirection.BACK ? -1 : 1;
	}

	private Optional<T> entryAt(int index) {
		if (index < 0 || index >= entries.size()) {
			return Optional.empty();
		}
		return Optional.of(entries.get(index));
	}
}
