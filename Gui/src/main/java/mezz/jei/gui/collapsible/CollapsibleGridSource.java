package mezz.jei.gui.collapsible;

import mezz.jei.common.Internal;
import mezz.jei.gui.match.IngredientMatchInfo;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.ingredients.IIngredientGridSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps the main ingredient list source and applies collapsible folding.
 * Delegate source changes notify listeners; state changes only mark the
 * layout dirty, and the assembly layer triggers the anchored update.
 */
public final class CollapsibleGridSource implements IIngredientGridSource {
	private final IIngredientGridSource delegate;
	private final CollapsibleManager manager;
	private final List<SourceListChangedListener> listeners = new ArrayList<>();

	private List<IElement<?>> foldedElements = List.of();
	private boolean dirty = true;
	private @Nullable CollapsibleLayout layout;
	private boolean includeBlockTags;

	public CollapsibleGridSource(IIngredientGridSource delegate, CollapsibleManager manager) {
		this.delegate = delegate;
		this.manager = manager;
		delegate.addSourceListChangedListener(() -> {
			invalidateGroups();
			notifyListeners();
		});
		manager.state().addListener(this::markDirty);
		manager.addRulesChangedListener(() -> {
			invalidateGroups();
			notifyListeners();
		});
	}

	@Override
	public List<IElement<?>> getElements() {
		updateIfDirty();
		return foldedElements;
	}

	@Override
	public List<IElement<?>> getElements(int columns, List<Integer> columnsPerRow) {
		return getElements();
	}

	@Override
	public void addSourceListChangedListener(SourceListChangedListener listener) {
		listeners.add(listener);
	}

	public void removeSourceListChangedListener(SourceListChangedListener listener) {
		listeners.remove(listener);
	}

	@Nullable
	public IElement<?> getAnchorElementForGroup(String groupId) {
		updateIfDirty();
		return foldedElements.stream()
			.filter(element -> element instanceof CollapsedGroupElement<?> groupElement &&
				groupElement.group().id().equals(groupId))
			.findFirst()
			.orElse(null);
	}

	private void markDirty() {
		this.dirty = true;
	}

	private void invalidateGroups() {
		layout = null;
		markDirty();
	}

	private void updateIfDirty() {
		boolean includeBlockTags = Internal.getJeiClientConfigs().getClientConfig().lookupBlockTagsEnabled().getValue();
		if (this.includeBlockTags != includeBlockTags) {
			this.includeBlockTags = includeBlockTags;
			invalidateGroups();
		}
		if (!dirty) {
			return;
		}
		if (layout == null) {
			layout = CollapsibleLayout.prepare(delegate.getElements(), manager.rules(),
				typedIngredient -> IngredientMatchInfo.fromIngredient(typedIngredient, includeBlockTags, manager.rules().hasComponents()));
		}
		this.foldedElements = layout.project(manager.state(), (element, info) -> new CollapsedGroupElement<>(
			element, info.group(), manager, !info.expanded(), !info.autoExpanded(), info.groupSize(), info.hiddenMembers()));
		this.dirty = false;
	}

	private void notifyListeners() {
		for (SourceListChangedListener listener : List.copyOf(listeners)) {
			listener.onSourceListChanged();
		}
	}
}
