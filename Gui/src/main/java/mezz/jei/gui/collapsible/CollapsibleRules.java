package mezz.jei.gui.collapsible;

import mezz.jei.gui.match.IngredientMatchInfo;
import mezz.jei.gui.match.ComponentPattern;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ordered collapsible groups; an ingredient belongs to the first matching group.
 */
public final class CollapsibleRules {
	public static final CollapsibleRules EMPTY = new CollapsibleRules(List.of());

	private final List<CollapsibleGroup> groups;
	private final boolean components;
	private final Map<IngredientMatchInfo, Integer> cache = new ConcurrentHashMap<>();

	public CollapsibleRules(List<CollapsibleGroup> groups) {
		this.groups = List.copyOf(groups);
		this.components = groups.stream().anyMatch(CollapsibleGroup::hasComponents);
	}

	public boolean hasComponents() {
		return components;
	}

	public int resolve(IngredientMatchInfo info) {
		IngredientMatchInfo key = components ? new IngredientMatchInfo(info.kind(), info.id(), info.tagIds()) : info;
		int match = cache.computeIfAbsent(key, this::find);
		if (components) {
			var lookup = ComponentPattern.createLookup(info.components());
			int limit = match < 0 ? groups.size() : match;
			for (int i = 0; i < limit; i++) {
				CollapsibleGroup group = groups.get(i);
				if (group.hasComponents() && group.matches(info, lookup)) {
					return i;
				}
			}
		}
		return match;
	}

	private int find(IngredientMatchInfo info) {
		for (int i = 0; i < groups.size(); i++) {
			if (!groups.get(i).hasComponents() && groups.get(i).matches(info)) {
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
