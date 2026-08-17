package mezz.jei.gui.collapsible;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.match.IngredientMatchInfo;
import mezz.jei.gui.overlay.elements.IElement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * Pure list transform: folds filtered elements into groups. Keeps the first
 * member of each collapsed group, shows all members when expanded, and
 * auto-expands when the filtered list contains only one group.
 */
public final class CollapsibleLayout {
	private CollapsibleLayout() {}

	public record SlotInfo(
		CollapsibleGroup group,
		int groupSize,
		boolean expanded,
		boolean autoExpanded,
		List<IElement<?>> hiddenMembers
	) {}

	public record LayoutResult(
		List<IElement<?>> visibleElements,
		Map<IElement<?>, SlotInfo> slotInfo,
		boolean autoExpanded
	) {}

	public interface RepresentativeSelector {
		IElement<?> select(List<IElement<?>> members, CollapsibleGroup group);
	}

	public static LayoutResult compute(
		List<IElement<?>> elements,
		CollapsibleRules rules,
		CollapsibleState state,
		RepresentativeSelector representativeSelector,
		Function<ITypedIngredient<?>, Optional<IngredientMatchInfo>> ingredientToInfo
	) {
		if (rules.isEmpty()) {
			return new LayoutResult(elements, Map.of(), false);
		}

		Map<IElement<?>, Integer> elementGroup = new LinkedHashMap<>();
		Map<Integer, List<IElement<?>>> members = new LinkedHashMap<>();
		int[] groupIndices = resolveGroupIndices(elements, rules, ingredientToInfo);
		boolean hasOutsideGroup = false;
		for (int i = 0; i < elements.size(); i++) {
			int groupIndex = groupIndices[i];
			if (groupIndex < 0) {
				hasOutsideGroup = true;
			} else {
				IElement<?> element = elements.get(i);
				elementGroup.put(element, groupIndex);
				members.computeIfAbsent(groupIndex, k -> new ArrayList<>()).add(element);
			}
		}

		// Reorder like GTNH NEI: every member of a group shares the order key
		// of the group's first occurrence, so group members stay contiguous.
		Map<IElement<?>, Integer> orderKeys = new LinkedHashMap<>();
		Map<Integer, Integer> groupFirstKeys = new LinkedHashMap<>();
		int[] orderIndex = {0};
		for (IElement<?> element : elements) {
			Integer groupIndex = elementGroup.get(element);
			if (groupIndex == null) {
				orderKeys.put(element, orderIndex[0]++);
			} else {
				Integer firstKey = groupFirstKeys.computeIfAbsent(groupIndex, k -> orderIndex[0]++);
				orderKeys.put(element, firstKey);
			}
		}
		List<IElement<?>> orderedElements = new ArrayList<>(elements);
		orderedElements.sort(Comparator.comparingInt(orderKeys::get));

		boolean autoExpanded = !hasOutsideGroup && members.size() == 1 &&
			members.values().iterator().next().size() > 1;

		List<IElement<?>> visible = new ArrayList<>();
		Map<IElement<?>, SlotInfo> slotInfo = new LinkedHashMap<>();
		Map<Integer, Boolean> emitted = new LinkedHashMap<>();

		for (IElement<?> element : orderedElements) {
			Integer groupIndex = elementGroup.get(element);
			if (groupIndex == null) {
				visible.add(element);
				continue;
			}
			List<IElement<?>> groupMembers = members.get(groupIndex);
			if (groupMembers.size() <= 1) {
				visible.add(element);
				continue;
			}
			if (autoExpanded) {
				visible.add(element);
				slotInfo.put(element, new SlotInfo(
					rules.groups().get(groupIndex), groupMembers.size(), true, true, List.of()));
				continue;
			}
			CollapsibleGroup group = rules.groups().get(groupIndex);
			boolean expanded = state.isExpanded(group.id());
			if (!emitted.containsKey(groupIndex)) {
				emitted.put(groupIndex, true);
				IElement<?> representative = representativeSelector.select(groupMembers, group);
				visible.add(representative);
				List<IElement<?>> hidden = groupMembers.stream()
					.filter(m -> m != representative)
					.toList();
				slotInfo.put(representative, new SlotInfo(
					group, groupMembers.size(), expanded, false,
					expanded ? List.of() : hidden));
			} else if (expanded) {
				visible.add(element);
				slotInfo.put(element, new SlotInfo(
					group, groupMembers.size(), true, false, List.of()));
			}
		}
		return new LayoutResult(List.copyOf(visible), Map.copyOf(slotInfo), autoExpanded);
	}

	private static final int PARALLEL_THRESHOLD = 1024;

	private static int[] resolveGroupIndices(
		List<IElement<?>> elements,
		CollapsibleRules rules,
		Function<ITypedIngredient<?>, Optional<IngredientMatchInfo>> ingredientToInfo
	) {
		int[] groupIndices = new int[elements.size()];
		IntStream stream = IntStream.range(0, elements.size());
		if (elements.size() >= PARALLEL_THRESHOLD) {
			stream = stream.parallel();
		}
		stream.forEach(i -> groupIndices[i] = resolveGroupIndex(elements.get(i), rules, ingredientToInfo));
		return groupIndices;
	}

	private static int resolveGroupIndex(
		IElement<?> element,
		CollapsibleRules rules,
		Function<ITypedIngredient<?>, Optional<IngredientMatchInfo>> ingredientToInfo
	) {
		return ingredientToInfo.apply(element.getTypedIngredient()).map(rules::resolve).orElse(-1);
	}
}
