package mezz.jei.gui.collapsible;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates collapsible rules and state; the single entry point for
 * toggling and future global controls.
 */
public final class CollapsibleManager {
	private final CollapsibleState state;
	private final List<Runnable> rulesChangedListeners = new ArrayList<>();
	private CollapsibleRules rules;
	private volatile CollapsibleSettings settings;
	@Nullable
	private String lastToggledGroupId;

	public CollapsibleManager(CollapsibleRules rules, CollapsibleSettings settings, CollapsibleState state) {
		this.rules = rules;
		this.settings = settings;
		this.state = state;
	}

	public CollapsibleRules rules() {
		return rules;
	}

	public CollapsibleSettings settings() {
		return settings;
	}

	public CollapsibleState state() {
		return state;
	}

	public void reload(CollapsibleRules newRules) {
		reload(newRules, settings);
	}

	public void reload(CollapsibleRules newRules, CollapsibleSettings newSettings) {
		this.rules = newRules;
		this.settings = newSettings;
		this.state.prune(newRules.groups());
		for (Runnable listener : List.copyOf(rulesChangedListeners)) {
			listener.run();
		}
	}

	public void addRulesChangedListener(Runnable listener) {
		rulesChangedListeners.add(listener);
	}

	public void toggleGroup(String groupId) {
		this.lastToggledGroupId = groupId;
		this.state.toggleGroup(groupId);
	}

	public void toggleAll(@Nullable Boolean force) {
		this.lastToggledGroupId = null;
		this.state.toggleAll(rules.groups(), force);
	}

	@Nullable
	public String getLastToggledGroupId() {
		return lastToggledGroupId;
	}
}
