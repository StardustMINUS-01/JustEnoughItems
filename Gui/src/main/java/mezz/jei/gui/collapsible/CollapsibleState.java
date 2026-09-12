package mezz.jei.gui.collapsible;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Expand/collapse state for fold groups. Only non-default (expanded) values
 * are stored; the default state is collapsed.
 */
public final class CollapsibleState {
	private final Map<String, Boolean> expandedOverrides = new HashMap<>();
	private final List<Runnable> listeners = new ArrayList<>();

	public boolean isExpanded(String groupId) {
		return expandedOverrides.getOrDefault(groupId, false);
	}

	public void setExpanded(String groupId, boolean expanded) {
		if (isExpanded(groupId) == expanded) {
			return;
		}
		if (expanded) {
			expandedOverrides.put(groupId, true);
		} else {
			expandedOverrides.remove(groupId);
		}
		notifyListeners();
	}

	public void toggleGroup(String groupId) {
		setExpanded(groupId, !isExpanded(groupId));
	}

	public void toggleAll(List<CollapsibleGroup> groups, @Nullable Boolean force) {
		boolean target = force != null ? force : !groups.stream().anyMatch(g -> isExpanded(g.id()));
		boolean changed = false;
		for (CollapsibleGroup group : groups) {
			if (isExpanded(group.id()) != target) {
				if (target) {
					expandedOverrides.put(group.id(), true);
				} else {
					expandedOverrides.remove(group.id());
				}
				changed = true;
			}
		}
		if (changed) {
			notifyListeners();
		}
	}

	public void load(Map<String, Boolean> overrides) {
		expandedOverrides.clear();
		overrides.forEach((groupId, expanded) -> {
			if (Boolean.TRUE.equals(expanded)) {
				expandedOverrides.put(groupId, true);
			}
		});
	}

	public Map<String, Boolean> toMap() {
		return Map.copyOf(expandedOverrides);
	}

	public void prune(List<CollapsibleGroup> groups) {
		if (expandedOverrides.keySet().retainAll(groups.stream().map(CollapsibleGroup::id).toList())) {
			notifyListeners();
		}
	}

	public void addListener(Runnable listener) {
		listeners.add(listener);
	}

	private void notifyListeners() {
		for (Runnable listener : List.copyOf(listeners)) {
			listener.run();
		}
	}
}
