package mezz.jei.common.search.compact;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;

/** Text-level postings for syllables and adjacent initials. Element ids stay in the literal index. */
final class PinyinRecallIndex {
	static final int PAIRS = 512;
	static final int TRIPLES = PAIRS + 26 * 26;
	private static final int ROWS = TRIPLES + 26 * 26 * 26;
	private final long[][] lanes;
	private final int[] offsets;
	private final byte[] deltas;

	PinyinRecallIndex(FlatTextPool pool, int texts) {
		lanes = pool.buildRecall();
		if (lanes.length == 0) {
			offsets = new int[0];
			deltas = new byte[0];
			return;
		}
		IntArrayList[] rows = new IntArrayList[ROWS];
		var dictionary = PinyinDictionary.INSTANCE;
		for (int id = 0; id < texts; id++) {
			if ((lanes[26][id >>> 6] & (1L << id)) == 0) {
				continue;
			}
			String text = pool.text(id);
			int prev = 0, earlier = 0;
			for (int i = 0; i < text.length();) {
				int cp = text.codePointAt(i);
				i += Character.charCount(cp);
				if (cp == ' ' && PinyinQuery.isScriptBoundary(text, i - 1, 0, text.length())) {
					continue;
				}
				cp = PinyinQuery.lowerAscii(cp);
				int entry = dictionary.entry(cp);
				int current = dictionary.initials(entry);
				if (cp >= 'a' && cp <= 'z' || cp == 'ü') {
					current |= 1 << ((cp == 'ü' ? 'v' : cp) - 'a');
				}
				if (entry >= 0x8000) {
					int offset = entry & 0x7fff;
					for (int j = 1; j <= dictionary.multiple[offset]; j++) {
						append(rows, dictionary.multiple[offset + j], id);
					}
				} else if (entry != 0) {
					append(rows, entry, id);
				}
				for (int a = prev; a != 0; a &= a - 1) {
					for (int b = current; b != 0; b &= b - 1) {
						int pair = Integer.numberOfTrailingZeros(a) * 26 + Integer.numberOfTrailingZeros(b);
						append(rows, PAIRS + pair, id);
						for (int c = earlier; c != 0; c &= c - 1) {
							append(rows, TRIPLES + Integer.numberOfTrailingZeros(c) * 676 + pair, id >>> 2);
						}
					}
				}
				earlier = prev;
				prev = current;
			}
		}
		var bytes = new ByteArrayList();
		offsets = new int[ROWS + 1];
		for (int i = 0; i < rows.length; i++) {
			var row = rows[i];
			int previous = -1;
			if (row != null) {
				for (int j = 0; j < row.size();) {
					int id = row.getInt(j++);
					writeNumber(bytes, id - previous);
					int end = j;
					while (end < row.size() && row.getInt(end) == id + end - j + 1) {
						end++;
					}
					int consecutive = end - j;
					if (consecutive >= 3) {
						bytes.add((byte) 0);
						writeNumber(bytes, consecutive);
					} else {
						for (int k = 0; k < consecutive; k++) {
							bytes.add((byte) 1);
						}
					}
					previous = id + consecutive;
					j = end;
				}
			}
			offsets[i + 1] = bytes.size();
		}
		deltas = bytes.toByteArray();
	}

	private static void writeNumber(ByteArrayList bytes, int value) {
		while (value >= 128) {
			bytes.add((byte) (value | 128));
			value >>>= 7;
		}
		bytes.add((byte) value);
	}

	private static void append(IntArrayList[] rows, int key, int id) {
		var row = rows[key];
		if (row == null) {
			rows[key] = row = new IntArrayList();
		}
		if (row.isEmpty() || row.getInt(row.size() - 1) != id) {
			row.add(id);
		}
	}

	void collect(PinyinQuery query, FlatTextPool text, java.util.function.IntConsumer matches) {
		if (lanes.length == 0) {
			return;
		}
		int[] best = null, second = null;
		int cost = Integer.MAX_VALUE, secondCost = Integer.MAX_VALUE;
		for (int[] clause : query.anchors) {
			int bytes = 0;
			for (int row : clause) {
				bytes += offsets[row + 1] - offsets[row];
			}
			if (bytes == 0) {
				return;
			}
			if (bytes < cost) {
				second = best;
				secondCost = cost;
				best = clause;
				cost = bytes;
			} else if (bytes < secondCost) {
				second = clause;
				secondCost = bytes;
			}
		}
		long[] candidates = best == null ? lanes[26] : decode(best);
		long[] other = second != null && secondCost <= 4 * cost ? decode(second) : lanes[26];
		for (int i = 0; i < candidates.length; i++) {
			long word = candidates[i] & other[i] & lanes[26][i];
			for (int mask = query.requiredInitials; mask != 0 && word != 0; mask &= mask - 1) {
				word &= lanes[Integer.numberOfTrailingZeros(mask)][i];
			}
			for (int group : query.fuzzyInitials) {
				long allowed = 0;
				for (int mask = group; mask != 0; mask &= mask - 1) {
					allowed |= lanes[Integer.numberOfTrailingZeros(mask)][i];
				}
				word &= allowed;
			}
			while (word != 0) {
				int id = i * 64 + Long.numberOfTrailingZeros(word);
				if (query.exactInitial() || text.matches(id, query)) {
					matches.accept(id);
				}
				word &= word - 1;
			}
		}
	}

	private long[] decode(int[] clause) {
		long[] bits = new long[lanes[26].length];
		for (int row : clause) {
			int id = -1;
			for (int pos = offsets[row]; pos < offsets[row + 1];) {
				boolean run = deltas[pos] == 0;
				if (run) {
					pos++;
				}
				int next = deltas[pos++];
				int value = next & 127;
				for (int shift = 7; next < 0; shift += 7) {
					next = deltas[pos++];
					value |= (next & 127) << shift;
				}
				int first = id + (run ? 1 : value);
				id += value;
				int width = row >= TRIPLES ? 4 : 1;
				int start = first * width;
				int end = (id + 1) * width - 1;
				int a = start >>> 6, b = end >>> 6;
				long low = -1L << start, high = -1L >>> (63 - (end & 63));
				if (a == b) {
					bits[a] |= low & high;
				} else {
					bits[a] |= low;
					java.util.Arrays.fill(bits, a + 1, b, -1L);
					bits[b] |= high;
				}
			}
		}
		return bits;
	}
}
