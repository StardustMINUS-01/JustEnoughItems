package mezz.jei.common.search.compact;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.function.IntConsumer;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;

/** Inline singletons, delta-coded ids, and nonempty 4096-bit blocks for dense rows. */
public final class HybridPostings {
	private static final int BLOCK_SHIFT = 12;
	private static final int MIN_IDS_PER_BLOCK = 256;
	private static final int WORDS_PER_BLOCK = 1 << (BLOCK_SHIFT - 6);
	private final int[] starts;
	private final int[] lengths;
	private final byte[] deltas;
	private final int[] blocks;
	private final long[] words;

	/** Packs nonempty rows of ascending ids. Row numbers are assigned by the caller. */
	public static HybridPostings fromSortedRows(List<IntArrayList> rows) {
		var first = new IntArrayList(rows.size());
		for (var row : rows)
			first.add(row.getInt(0));
		return new HybridPostings(first, rows, new BitSet());
	}

	HybridPostings(IntArrayList firstIds, List<IntArrayList> rows, BitSet unordered) {
		starts = new int[rows.size()];
		lengths = new int[rows.size()];
		var bytes = new ByteArrayList();
		var blockIds = new IntArrayList();
		var bits = new LongArrayList();
		for (int i = 0; i < rows.size(); i++) {
			IntArrayList row = rows.get(i);
			if (row == null) {
				starts[i] = firstIds.getInt(i);
				continue;
			}
			int[] ids = row.elements();
			int size = row.size();
			if (unordered.get(i)) {
				Arrays.sort(ids, 0, size);
			}
			if (ids[0] == ids[size - 1]) {
				starts[i] = ids[0];
				continue;
			}
			int blockCount = 1;
			if (size >= MIN_IDS_PER_BLOCK) {
				for (int j = 1; j < size; j++) {
					if ((ids[j] >> BLOCK_SHIFT) != (ids[j - 1] >> BLOCK_SHIFT)) {
						blockCount++;
					}
				}
			}
			if ((long) blockCount * MIN_IDS_PER_BLOCK <= size) {
				starts[i] = blockIds.size();
				lengths[i] = -blockCount;
				int lastBlock = -1;
				int firstWord = bits.size();
				bits.size(firstWord + blockCount * WORDS_PER_BLOCK);
				long[] packed = bits.elements();
				int blockOffset = firstWord - WORDS_PER_BLOCK;
				for (int j = 0; j < size; j++) {
					int id = ids[j];
					int block = id >> BLOCK_SHIFT;
					if (block != lastBlock) {
						blockIds.add(block);
						blockOffset += WORDS_PER_BLOCK;
						lastBlock = block;
					}
					int word = blockOffset + ((id >>> 6) % WORDS_PER_BLOCK);
					packed[word] |= 1L << id;
				}
			} else {
				starts[i] = bytes.size();
				int previous = -1;
				for (int j = 0; j < size; j++) {
					int id = ids[j];
					if (id != previous) {
						int delta = previous < 0 ? id : id - previous;
						while (delta >= 128) {
							bytes.add((byte) (delta | 128));
							delta >>>= 7;
						}
						bytes.add((byte) delta);
						previous = id;
					}
				}
				lengths[i] = bytes.size() - starts[i];
			}
		}
		deltas = bytes.toByteArray();
		blocks = blockIds.toIntArray();
		words = bits.toLongArray();
	}

	public void addTo(int row, long[] result) {
		int start = starts[row];
		int length = lengths[row];
		if (length == 0) {
			result[start >>> 6] |= 1L << start;
		} else if (length < 0) {
			for (int block = start; block < start - length; block++) {
				int out = blocks[block] * WORDS_PER_BLOCK;
				int count = Math.min(WORDS_PER_BLOCK, result.length - out);
				for (int i = 0; i < count; i++) {
					result[out + i] |= words[block * WORDS_PER_BLOCK + i];
				}
			}
		} else {
			decode(start, length, id -> result[id >>> 6] |= 1L << id);
		}
	}

	void forEachId(int row, IntConsumer consumer) {
		int start = starts[row];
		int length = lengths[row];
		if (length > 0) {
			decode(start, length, consumer);
		} else if (length == 0) {
			consumer.accept(start);
		} else {
			for (int block = start; block < start - length; block++) {
				for (int i = 0; i < WORDS_PER_BLOCK; i++) {
					visitWord(words[block * WORDS_PER_BLOCK + i], blocks[block] * WORDS_PER_BLOCK + i, consumer);
				}
			}
		}
	}

	private void decode(int start, int length, IntConsumer consumer) {
		int id = 0;
		for (int end = start + length; start < end;) {
			int next = deltas[start++];
			int delta = next & 127;
			for (int shift = 7; next < 0; shift += 7) {
				next = deltas[start++];
				delta |= (next & 127) << shift;
			}
			id += delta;
			consumer.accept(id);
		}
	}

	static void forEach(long[] bits, IntConsumer consumer) {
		for (int i = 0; i < bits.length; i++) {
			visitWord(bits[i], i, consumer);
		}
	}

	private static void visitWord(long word, int index, IntConsumer consumer) {
		while (word != 0) {
			consumer.accept(index * 64 + Long.numberOfTrailingZeros(word));
			word &= word - 1;
		}
	}
}
