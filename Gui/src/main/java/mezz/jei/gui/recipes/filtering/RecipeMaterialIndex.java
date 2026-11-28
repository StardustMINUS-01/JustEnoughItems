package mezz.jei.gui.recipes.filtering;

import java.util.concurrent.CancellationException;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.common.search.compact.HybridPostings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import mezz.jei.gui.search.OrphanIngredientCorpus;
import mezz.jei.gui.search.IngredientSearchSnapshot;
import mezz.jei.common.ingredients.itemStacks.TypedItemStack;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.world.item.Item;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.util.SimpleBitStorage;
import java.util.concurrent.ForkJoinTask;
import java.util.stream.IntStream;

/** Forward and reverse associations for one category and one ingredient role. */
final class RecipeMaterialIndex {
	private final RecipeRowOffsets offsets;
	private final int recipeCount;
	private final int[] reverseKeys;
	private final long[] keyBits;
	private final int[] keyRanks;
	private final int firstKey;
	private final SimpleBitStorage ids;
	private final int[][] groups;
	private final HybridPostings reverse;
	private final OrphanIngredientCorpus orphans;
	private final int maxMaterial;

	boolean hasOrphans() { return maxMaterial >= orphans.firstId(); }

	static final class Resolver {
		private static final int MAX_CACHED_ITEMS = 32_768;
		final OrphanIngredientCorpus orphans;
		private final Reference2IntOpenHashMap<Item> plain = new Reference2IntOpenHashMap<>();
		private final Reference2IntOpenHashMap<Item> itemIds = new Reference2IntOpenHashMap<>(0);

		private final boolean idle;
		Resolver(OrphanIngredientCorpus orphans, boolean idle) {
			this.idle = idle;
			this.orphans = orphans;
			plain.defaultReturnValue(-1);
			itemIds.defaultReturnValue(-1);
		}

		int resolve(ITypedIngredient<?> ingredient) {
			if (!(ingredient instanceof TypedItemStack stack)) {
				if (idle)
					throw new CancellationException();
				return orphans.resolve(ingredient);
			}
			var item = stack.getPlainSingleItem().orElse(null);
			int id = item == null ? -1 : plain.getInt(item);
			if (id < 0) {
				id = resolveItem(stack);
				if (item != null)
					plain.put(item, id);
			}
			return id;
		}

		private int resolveItem(TypedItemStack stack) {
			Item item = stack.getBaseIngredient(VanillaTypes.ITEM_STACK);
			int id = itemIds.getInt(item);
			if (id >= 0)
				return id;
			if (idle) {
				id = orphans.findKnownItemId(stack);
				if (id < 0)
					throw new CancellationException();
				if (itemIds.size() < MAX_CACHED_ITEMS)
					itemIds.put(item, id);
				return id;
			}
			id = orphans.resolve(stack);
			if (itemIds.size() < MAX_CACHED_ITEMS && orphans.canUseItemAsUid(stack))
				itemIds.put(item, id);
			return id;
		}
	}

	static final class Builder {
		final int[] offsets;
		final IntArrayList values = new IntArrayList();
		final Object2IntOpenHashMap<IntList> groupIds = new Object2IntOpenHashMap<>();
		final List<IntList> groups = new ArrayList<>();
		final IntArrayList frequencies = new IntArrayList();
		final Resolver resolve;
		final Reference2ObjectOpenHashMap<List<ITypedIngredient<?>>, IntList> wholeSlots = new Reference2ObjectOpenHashMap<>();
		private record WideSlot(List<ITypedIngredient<?>> materials, int start, int end, IntList ids) {
			boolean sameReferences(List<ITypedIngredient<?>> other, int from, int to) {
				if (end - start != to - from)
					return false;
				for (int i = 0; i < to - from; i++)
					if (materials.get(start + i) != other.get(from + i))
						return false;
				return true;
			}
		}
		private final WideSlot[] recentSlots = new WideSlot[128];
		int maxMaterial = -1;
		final IntArrayList row = new IntArrayList(), slot = new IntArrayList();

		Builder(int count, Resolver resolve) {
			this.offsets = new int[count + 1];
			this.resolve = resolve;
			groupIds.defaultReturnValue(-1);
		}

		void add(int recipe, List<ITypedIngredient<?>> materials, int[] ranges) {
			row.clear();
			int range = 0;
			for (int i = 0; i < materials.size();) {
				boolean grouped = range < ranges.length && ranges[range] == i;
				int end = grouped ? (ranges.length == 1 ? materials.size() : ranges[range + 1]) : i + 1;
				slot.clear();
				boolean whole = grouped && i == 0 && end == materials.size();
				var cached = whole ? wholeSlots.get(materials) : null;
				int start = i;
				int bucket = -1;
				if (grouped && cached == null && end - start >= 16) {
					int hash = 31 * System.identityHashCode(materials.get(start)) + System.identityHashCode(materials.get(end - 1));
					bucket = (hash + end - start) & (recentSlots.length - 1);
					var previous = recentSlots[bucket];
					if (previous != null && previous.sameReferences(materials, start, end))
						cached = previous.ids;
				}
				if (cached != null) {
					slot.addAll(cached);
					i = end;
				} else {
					while (i < end) {
						int id = resolve.resolve(materials.get(i++));
						maxMaterial = Math.max(maxMaterial, id);
						slot.add(id);
					}
					if (grouped)
						unique(slot);
					if (bucket >= 0) {
						var resolved = new IntArrayList(slot);
						recentSlots[bucket] = new WideSlot(materials, start, end, resolved);
						if (whole && wholeSlots.size() < 1024)
							wholeSlots.put(materials, resolved);
					}
				}
				if (grouped)
					range += 2;
				if (grouped && slot.size() >= 4) {
					int id = groupIds.getInt(slot);
					if (id < 0) {
						id = groups.size();
						var key = new IntArrayList(slot);
						groups.add(key);
						groupIds.put(key, id);
						frequencies.add(0);
					}
					frequencies.set(id, frequencies.getInt(id) + 1);
					row.add(~id);
				} else
					row.addAll(slot);
			}
			unique(row);
			values.addAll(row);
			offsets[recipe + 1] = values.size();
		}

		private static void unique(IntArrayList list) {
			if (list.size() < 2)
				return;
			int[] a = list.elements();
			Arrays.sort(a, 0, list.size());
			int count = 1;
			for (int i = 1; i < list.size(); i++)
				if (a[i] != a[count - 1])
					a[count++] = a[i];
			list.size(count);
		}
	}

	RecipeMaterialIndex(Builder builder, Runnable checkCancelled) {
		var offsets = builder.offsets;
		recipeCount = offsets.length - 1;
		orphans = builder.resolve.orphans;
		maxMaterial = builder.maxMaterial;
		var common = new ArrayList<int[]>();
		int[] groupIds = new int[builder.groups.size()];
		for (int i = 0; i < groupIds.length; i++)
			if (builder.frequencies.getInt(i) >= 4) {
				groupIds[i] = ~common.size();
				common.add(builder.groups.get(i).toIntArray());
			}
		groups = common.toArray(int[][]::new);
		var flat = new IntArrayList(builder.values.size());
		var rows = new Int2ObjectOpenHashMap<IntArrayList>();
		int start = 0;
		for (int recipe = 0; recipe < offsets.length - 1; recipe++) {
			if ((recipe & 1023) == 0)
				checkCancelled.run();
			int end = offsets[recipe + 1];
			int begin = flat.size();
			for (int i = start; i < end; i++) {
				int id = builder.values.getInt(i);
				if (id < 0 && groupIds[~id] == 0)
					flat.addAll(builder.groups.get(~id));
				else
					flat.add(id < 0 ? groupIds[~id] : id);
			}
			for (int i = begin; i < flat.size(); i++) {
				var posting = rows.computeIfAbsent(flat.getInt(i), ignored -> new IntArrayList());
				if (posting.isEmpty() || posting.getInt(posting.size() - 1) != recipe)
					posting.add(recipe);
			}
			offsets[recipe + 1] = flat.size();
			start = end;
		}
		this.offsets = new RecipeRowOffsets(offsets);
		int max = groups.length;
		for (int i = 0; i < flat.size(); i++)
			max = Math.max(max, flat.getInt(i) + groups.length);
		ids = new SimpleBitStorage(Math.max(1, Integer.SIZE - Integer.numberOfLeadingZeros(max)), flat.size());
		for (int i = 0; i < flat.size(); i++) {
			int id = flat.getInt(i);
			ids.set(i, id < 0 ? ~id : id + groups.length);
		}
		int[] keys = rows.keySet().toIntArray();
		Arrays.sort(keys);
		var ordered = new ArrayList<IntArrayList>(keys.length);
		for (int key : keys)
			ordered.add(rows.get(key));
		reverse = HybridPostings.fromSortedRows(ordered);
		firstKey = keys.length == 0 ? 0 : keys[0];
		long range = keys.length == 0 ? 0 : (long) keys[keys.length - 1] - firstKey + 1;
		if (range > 0 && range <= 8L * keys.length && range <= Integer.MAX_VALUE) {
			reverseKeys = null;
			keyBits = new long[(int) ((range + 63) >>> 6)];
			keyRanks = new int[keyBits.length];
			for (int key : keys) {
				int index = key - firstKey;
				keyBits[index >>> 6] |= 1L << index;
			}
			int rank = 0;
			for (int i = 0; i < keyBits.length; i++) {
				keyRanks[i] = rank;
				rank += Long.bitCount(keyBits[i]);
			}
		} else {
			reverseKeys = keys;
			keyBits = null;
			keyRanks = null;
		}
	}

	private static boolean hit(long[] bits, int id) {
		return id >>> 6 < bits.length && (bits[id >>> 6] & 1L << id) != 0;
	}

	BitSet matches(long[] regular, BitSet candidates, boolean sparse, String token, IngredientSearchSnapshot snapshot, Runnable checkCancelled) {
		long[] cached = hasOrphans() ? orphans.cachedWords(token, snapshot, maxMaterial) : regular;
		if (candidates.cardinality() <= 256) {
			var found = new BitSet();
			var tested = new BitSet();
			var matched = new BitSet();
			for (int row = candidates.nextSetBit(0); row >= 0; row = candidates.nextSetBit(row + 1)) {
				checkCancelled.run();
				for (int i = offsets.get(row), end = offsets.get(row + 1); i < end; i++) {
					int encoded = ids.get(i);
					boolean match;
					if (encoded < groups.length) {
						if (!tested.get(encoded)) {
							tested.set(encoded);
							for (int j = 0; j < groups[encoded].length; j++) {
								if ((j & 63) == 0)
									checkCancelled.run();
								int id = groups[encoded][j];
								if (cached != null ? hit(cached, id) : matchesMaterial(regular, token, snapshot, id)) {
									matched.set(encoded);
									break;
								}
							}
						}
						match = matched.get(encoded);
					} else
						match = cached != null ? hit(cached, encoded - groups.length) : matchesMaterial(regular, token, snapshot, encoded - groups.length);
					if (match) {
						found.set(row);
						break;
					}
				}
			}
			return found;
		}
		long[] bits = cached == null ? orphans.words(token, snapshot, maxMaterial, checkCancelled) : cached;
		var groupHits = new BitSet(groups.length);
		for (int i = 0; i < groups.length; i++)
			for (int id : groups[i])
				if (hit(bits, id)) {
					groupHits.set(i);
					break;
				}

		BitSet found;
		if (sparse) {
			long[] words = new long[(recipeCount + 63) >>> 6];
			for (int i = 0; i < bits.length; i++) {
				if ((i & 127) == 0)
					checkCancelled.run();
				for (long word = bits[i]; word != 0; word &= word - 1)
					addPosting(i * 64 + Long.numberOfTrailingZeros(word), words);
			}
			for (int g = groupHits.nextSetBit(0); g >= 0; g = groupHits.nextSetBit(g + 1))
				addPosting(~g, words);
			found = BitSet.valueOf(words);
			found.and(candidates);
		} else {
			long[] words = new long[(candidates.length() + 63) >>> 6];
			var blocks = IntStream.range(0, (words.length + 127) / 128);
			if (words.length >= 1024 && ForkJoinTask.inForkJoinPool())
				blocks = blocks.parallel();
			blocks.forEach(block -> {
				checkCancelled.run();
				int end = Math.min((block + 1) * 8192, candidates.length());
				for (int row = candidates.nextSetBit(block * 8192); row >= 0 && row < end; row = candidates.nextSetBit(row + 1)) {
					for (int i = offsets.get(row), endRow = offsets.get(row + 1); i < endRow; i++) {
						int id = ids.get(i);
						if (id < groups.length ? groupHits.get(id) : hit(bits, id - groups.length)) {
							words[row >>> 6] |= 1L << row;
							break;
						}
					}
				}
			});
			found = BitSet.valueOf(words);
		}
		return found;
	}

	private boolean matchesMaterial(long[] regular, String token, IngredientSearchSnapshot snapshot, int id) {
		return id < orphans.firstId() ? hit(regular, id) : orphans.test(id, token, snapshot);
	}

	private void addPosting(int key, long[] words) {
		int row;
		if (reverseKeys != null)
			row = Arrays.binarySearch(reverseKeys, key);
		else {
			int index = key - firstKey;
			int word = index >>> 6;
			long mask = 1L << index;
			if (word >= keyBits.length || (keyBits[word] & mask) == 0)
				return;
			row = keyRanks[word] + Long.bitCount(keyBits[word] & (mask - 1));
		}
		if (row >= 0)
			reverse.addTo(row, words);
	}
}
