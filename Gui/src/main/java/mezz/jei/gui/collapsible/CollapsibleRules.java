package mezz.jei.gui.collapsible;

import mezz.jei.gui.match.IngredientMatchInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ordered collapsible groups; an ingredient belongs to the first matching group.
 */
public final class CollapsibleRules {
	public static final CollapsibleRules EMPTY = new CollapsibleRules(List.of());

	private final List<CollapsibleGroup> groups;
	private final Map<IngredientMatchInfo, Integer> cache = new ConcurrentHashMap<>();

	public CollapsibleRules(List<CollapsibleGroup> groups) {
		this.groups = List.copyOf(groups);
	}

	public int resolve(IngredientMatchInfo info) {
		return cache.computeIfAbsent(info, this::find);
	}

	private int find(IngredientMatchInfo info) {
		for (int i = 0; i < groups.size(); i++) {
			if (groups.get(i).matches(info)) {
				return i;
			}
		}
		return -1;
	}

	public List<CollapsibleGroup> groups() {
		return groups;
	}

	public boolean isEmpty() {
		return groups.isEmpty();
	}
}
