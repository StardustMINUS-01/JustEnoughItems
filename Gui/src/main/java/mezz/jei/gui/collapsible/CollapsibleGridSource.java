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

	public CollapsibleGridSource(IIngredientGridSource delegate, CollapsibleManager manager) {
		this.delegate = delegate;
		this.manager = manager;
		delegate.addSourceListChangedListener(() -> {
			markDirty();
			notifyListeners();
		});
		manager.state().addListener(this::markDirty);
		manager.addRulesChangedListener(() -> {
			markDirty();
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

	private void updateIfDirty() {
		if (!dirty) {
			return;
		}
		this.dirty = false;
		boolean includeBlockTags = Internal.getJeiClientConfigs().getClientConfig().isLookupBlockTagsEnabled();
		CollapsibleLayout.LayoutResult result = CollapsibleLayout.compute(
			delegate.getElements(),
			manager.rules(),
			manager.state(),
			(members, group) -> members.get(0),
			typedIngredient -> IngredientMatchInfo.fromIngredient(typedIngredient, includeBlockTags)
		);
		List<IElement<?>> wrapped = new ArrayList<>(result.visibleElements().size());
		for (IElement<?> element : result.visibleElements()) {
			CollapsibleLayout.SlotInfo info = result.slotInfo().get(element);
			if (info == null) {
				wrapped.add(element);
			} else {
				wrapped.add(new CollapsedGroupElement<>(
					element,
					info.group(),
					manager,
					!info.expanded(),
					!info.autoExpanded(),
					info.groupSize(),
					info.hiddenMembers()
				));
			}
		}
		this.foldedElements = List.copyOf(wrapped);
	}

	private void notifyListeners() {
		for (SourceListChangedListener listener : List.copyOf(listeners)) {
			listener.onSourceListChanged();
		}
	}
}
