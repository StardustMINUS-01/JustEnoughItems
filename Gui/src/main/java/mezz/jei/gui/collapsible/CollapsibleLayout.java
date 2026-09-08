package mezz.jei.gui.collapsible;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.match.IngredientMatchInfo;
import mezz.jei.gui.overlay.elements.IElement;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.IntStream;

/** Groups the current filtered list independently of its expanded state. */
public final class CollapsibleLayout {
	private static final int PARALLEL_THRESHOLD = 1024;
	private final List<Bucket> buckets;
	private final boolean autoExpanded;

	private CollapsibleLayout(List<Bucket> buckets) {
		this.buckets = buckets;
		this.autoExpanded = buckets.size() == 1 && buckets.getFirst().group() != null && buckets.getFirst().members().size() > 1;
	}

	public record SlotInfo(CollapsibleGroup group, int groupSize, boolean expanded, boolean autoExpanded,
		List<IElement<?>> hiddenMembers) {}

	private record Bucket(@Nullable CollapsibleGroup group, List<IElement<?>> members) {}

	public static CollapsibleLayout prepare(List<IElement<?>> elements, CollapsibleRules rules,
		Function<ITypedIngredient<?>, Optional<IngredientMatchInfo>> ingredientToInfo) {
		if (rules.isEmpty()) {
			return new CollapsibleLayout(List.of(new Bucket(null, elements)));
		}
		int[] indices = new int[elements.size()];
		IntStream stream = IntStream.range(0, elements.size());
		if (elements.size() >= PARALLEL_THRESHOLD) {
			stream = stream.parallel();
		}
		stream.forEach(i -> indices[i] = ingredientToInfo.apply(elements.get(i).getTypedIngredient()).map(rules::resolve).orElse(-1));

		List<Bucket> buckets = new ArrayList<>();
		Map<Integer, Bucket> groups = new HashMap<>();
		for (int i = 0; i < elements.size(); i++) {
			int groupIndex = indices[i];
			Bucket bucket;
			if (groupIndex >= 0) {
				// Emit the group at its first occurrence, keeping members in source order like GTNH NEI.
				bucket = groups.computeIfAbsent(groupIndex, index -> {
					Bucket created = new Bucket(rules.groups().get(index), new ArrayList<>());
					buckets.add(created);
					return created;
				});
			} else if (!buckets.isEmpty() && buckets.getLast().group() == null) {
				bucket = buckets.getLast();
			} else {
				bucket = new Bucket(null, new ArrayList<>());
				buckets.add(bucket);
			}
			bucket.members().add(elements.get(i));
		}
		return new CollapsibleLayout(buckets);
	}

	public List<IElement<?>> project(CollapsibleState state, BiFunction<IElement<?>, SlotInfo, IElement<?>> wrap) {
		List<IElement<?>> visible = new ArrayList<>();
		for (Bucket bucket : buckets) {
			var members = bucket.members();
			var group = bucket.group();
			if (group == null || members.size() <= 1) {
				visible.addAll(members);
				continue;
			}
			boolean expanded = autoExpanded || state.isExpanded(group.id());
			List<IElement<?>> hidden = expanded ? List.of() : Collections.unmodifiableList(members.subList(1, members.size()));
			SlotInfo info = new SlotInfo(group, members.size(), expanded, autoExpanded, hidden);
			if (expanded) {
				for (IElement<?> member : members) {
					visible.add(wrap.apply(member, info));
				}
			} else {
				visible.add(wrap.apply(members.getFirst(), info));
			}
		}
		return List.copyOf(visible);
	}
}
