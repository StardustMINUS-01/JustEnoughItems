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
	private final boolean nbt;
	private final Map<IngredientMatchInfo, Integer> cache = new ConcurrentHashMap<>();

	public CollapsibleRules(List<CollapsibleGroup> groups) {
		this.groups = List.copyOf(groups);
		this.nbt = groups.stream().anyMatch(CollapsibleGroup::hasNbt);
	}

	public boolean hasNbt() {
		return nbt;
	}

	public int resolve(IngredientMatchInfo info) {
		IngredientMatchInfo key = info.nbt() == null ? info : new IngredientMatchInfo(info.kind(), info.id(), info.tagIds());
		int match = cache.computeIfAbsent(key, this::find);
		if (nbt) {
			int limit = match < 0 ? groups.size() : match;
			for (int i = 0; i < limit; i++) {
				CollapsibleGroup group = groups.get(i);
				if (group.hasNbt() && group.matches(info)) {
					return i;
				}
			}
		}
		return match;
	}

	private int find(IngredientMatchInfo info) {
		for (int i = 0; i < groups.size(); i++) {
			if (!groups.get(i).hasNbt() && groups.get(i).matches(info)) {
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
