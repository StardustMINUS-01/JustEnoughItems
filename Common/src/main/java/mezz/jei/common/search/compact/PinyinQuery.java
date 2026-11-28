package mezz.jei.common.search.compact;

import java.util.BitSet;
import java.util.HashSet;

import it.unimi.dsi.fastutil.ints.IntArrayList;

/** A compiled query. Positions are code points, so a supplementary ideograph consumes one literal. */
public final class PinyinQuery {
	private static volatile Lexicon lexicon;
	final int flags;
	final String literalToken;
	private final int[] query;
	private final byte[][] lengths;
	private final boolean fold;
	final int requiredInitials;
	final int[] fuzzyInitials;
	final int[][] anchors;
	private final PinyinDictionary dictionary = PinyinDictionary.INSTANCE;

	private static final class Spelling {
		final Spelling[] next = new Spelling[26];
		IntArrayList full = new IntArrayList();
		IntArrayList partial = new IntArrayList();
		byte[] internal;
		byte[] terminal;
		int internalSteps;
		int terminalSteps;

		void freeze(Spelling parent, int depth, int sounds) {
			internal = parent == null ? new byte[sounds] : full.isEmpty() ? parent.internal : parent.internal.clone();
			internalSteps = parent == null ? 2 : parent.internalSteps;
			if (!full.isEmpty()) {
				add(internal, full, depth);
				internalSteps |= 1 << depth;
			}
			terminal = internal;
			terminalSteps = internalSteps;
			if (!partial.isEmpty()) {
				terminal = internal.clone();
				add(terminal, partial, depth);
				terminalSteps |= 1 << depth;
			}
			full = partial = null;
			for (var child : next) {
				if (child != null) {
					child.freeze(this, depth + 1, sounds);
				}
			}
		}
	}

	private record Lexicon(int flags, Spelling root, int[] initials, int changedInitials) {
		static Lexicon create(int flags) {
			var root = new Spelling();
			var sounds = PinyinDictionary.INSTANCE.sounds;
			var options = java.util.Arrays.stream(PinyinOption.values()).filter(option -> (flags & option.bit) != 0).toList();
			for (int id = 1; id < sounds.length; id++) {
				for (String spelling : spellings(sounds[id], options)) {
					var node = root;
					for (int n = 1; n <= spelling.length(); n++) {
						int letter = spelling.charAt(n - 1) - 'a';
						if (node.next[letter] == null) {
							node.next[letter] = new Spelling();
						}
						node = node.next[letter];
						boolean initial = n == 1 || n == 2 && spelling.charAt(1) == 'h' && "zcs".indexOf(spelling.charAt(0)) >= 0;
						(initial || n == spelling.length() ? node.full : node.partial).add(id);
					}
				}
			}
			root.freeze(null, 0, sounds.length);
			// Derive initial equivalence from the same spellings as the matcher, including transitive fuzzy rules.
			int[] initials = new int[26];
			int changed = 0;
			for (int letter = 0; letter < initials.length; letter++) {
				int own = 1 << letter;
				int mask = own;
				var node = root.next[letter];
				if (node != null && flags != 0) {
					for (int id = 1; id < sounds.length; id++) {
						if ((node.internal[id] & 2) != 0) {
							mask |= 1 << (sounds[id].charAt(0) - 'a');
						}
					}
				}
				initials[letter] = mask;
				if (mask != own) {
					changed |= own;
				}
			}
			return new Lexicon(flags, root, initials, changed);
		}

		private static java.util.Collection<String> spellings(String sound, java.util.List<PinyinOption> options) {
			if (options.isEmpty()) {
				return java.util.List.of(sound);
			}
			var found = new HashSet<String>();
			var pending = new java.util.ArrayDeque<String>();
			found.add(sound);
			pending.add(sound);
			while (!pending.isEmpty()) {
				String current = pending.removeFirst();
				for (var option : options) {
					String alternate = option.alternate(current);
					if (found.add(alternate)) {
						pending.add(alternate);
					}
				}
			}
			return found;
		}
	}

	private static Lexicon getLexicon(int flags) {
		var current = lexicon;
		return current != null && current.flags == flags ? current : updateLexicon(flags);
	}

	private static synchronized Lexicon updateLexicon(int flags) {
		if (lexicon == null || lexicon.flags != flags) {
			lexicon = Lexicon.create(flags);
		}
		return lexicon;
	}

	public static PinyinQuery compile(String token, int flags) {
		if (flags == 0 || token.isEmpty()) {
			return null;
		}
		if ((flags & PinyinOption.ENABLED.bit) != 0) {
			token = lowerAscii(token);
		}
		boolean latin = token.chars().anyMatch(PinyinQuery::isLetter);
		boolean fold = (flags & PinyinOption.FOLD.bit) != 0 && token.codePoints().anyMatch(c -> c >= 0x2e80);
		if (!fold && (!latin || (flags & PinyinOption.ENABLED.bit) == 0)) {
			return null;
		}
		return new PinyinQuery(token, flags, fold);
	}

	private PinyinQuery(String token, int flags, boolean fold) {
		this.fold = fold;
		this.flags = flags;
		literalToken = token;
		query = token.codePoints().toArray();
		lengths = new byte[query.length][];
		int fuzzy = flags & ~(PinyinOption.ENABLED.bit | PinyinOption.FOLD.bit);
		var lex = (flags & PinyinOption.ENABLED.bit) == 0 ? null : getLexicon(fuzzy);
		int[] spans = new int[query.length];
		int[] required = new int[query.length + 1];
		for (int i = query.length - 1; i >= 0; i--) {
			spans[i] = 2;
			int cp = query[i];
			required[i] = required[i + 1];
			if (fold) {
				query[i] = dictionary.fold(cp);
			}
			if (lex == null || !isLetter(cp)) {
				continue;
			}
			int first = cp == 'ü' ? 'v' : cp;
			var node = lex.root;
			int j = i;
			while (j < query.length && isLetter(query[j])) {
				var child = node.next[letter(j)];
				if (child == null) {
					break;
				}
				node = child;
				j++;
			}
			lengths[i] = j == query.length ? node.terminal : node.internal;
			spans[i] = j == query.length ? node.terminalSteps : node.internalSteps;
			for (int choices = spans[i]; choices != 0; choices &= choices - 1) {
				required[i] &= required[i + Integer.numberOfTrailingZeros(choices)];
			}
			required[i] |= 1 << (first - 'a');
		}
		// Fuzzy initials may have different letters in the source text.
		requiredInitials = lex == null ? 0 : required[0] & ~lex.changedInitials;
		int affected = lex == null ? 0 : required[0] & lex.changedInitials;
		if (affected == 0) {
			fuzzyInitials = it.unimi.dsi.fastutil.ints.IntArrays.EMPTY_ARRAY;
		} else {
			var groups = new IntArrayList(2);
			while (affected != 0) {
				int alternatives = lex.initials[Integer.numberOfTrailingZeros(affected)];
				groups.add(alternatives);
				affected &= ~alternatives;
			}
			fuzzyInitials = groups.toIntArray();
		}
		anchors = lex != null ? buildAnchors(spans, lex.initials) : new int[0][];
	}

	private int letter(int pos) {
		return (query[pos] == 'ü' ? 'v' : query[pos]) - 'a';
	}

	private int[][] buildAnchors(int[] spans, int[] initials) {
		var plans = new java.util.ArrayList<int[]>();
		int crossedUntil = 0;
		for (int i = 0; i < query.length; i++) {
			if (i >= crossedUntil && isLetter(query[i])) {
				var rows = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
				boolean bounded = true;
				for (int steps = spans[i]; steps != 0; steps &= steps - 1) {
					int n = Integer.numberOfTrailingZeros(steps);
					int j = i + n;
					if (j == query.length || !isLetter(query[j])) {
						if (n == 1) {
							bounded = false;
							break;
						}
						for (int sound = 1; sound < lengths[i].length; sound++) {
							if ((lengths[i][sound] & (1 << n)) != 0) {
								rows.add(sound);
							}
						}
					} else {
						int first = initials[letter(i)], secondInitial = initials[letter(j)];
						for (int second = spans[j]; second != 0; second &= second - 1) {
							int k = j + Integer.numberOfTrailingZeros(second);
							int third = k == query.length || !isLetter(query[k]) ? 0 : initials[letter(k)];
							addInitialRows(rows, first, secondInitial, third);
						}
					}
				}
				if (bounded) {
					plans.add(rows.toIntArray());
				}
			}
			crossedUntil = Math.max(crossedUntil, i + 31 - Integer.numberOfLeadingZeros(spans[i]));
		}
		return plans.toArray(int[][]::new);
	}

	private static void addInitialRows(it.unimi.dsi.fastutil.ints.IntSet rows, int first, int second, int third) {
		for (int a = first; a != 0; a &= a - 1) {
			for (int b = second; b != 0; b &= b - 1) {
				int pair = Integer.numberOfTrailingZeros(a) * 26 + Integer.numberOfTrailingZeros(b);
				if (third == 0) {
					rows.add(PinyinRecallIndex.PAIRS + pair);
				} else {
					for (int c = third; c != 0; c &= c - 1) {
						rows.add(PinyinRecallIndex.TRIPLES + pair * 26 + Integer.numberOfTrailingZeros(c));
					}
				}
			}
		}
	}

	static void warmLexicon(int flags) {
		getLexicon(flags & ~(PinyinOption.ENABLED.bit | PinyinOption.FOLD.bit));
	}

	boolean exactInitial() {
		return query.length == 1 && requiredInitials != 0 && query[0] != 'ü' && query[0] != 'v';
	}

	int literalUnit(int[] offsets) {
		int best = -1;
		int cost = Integer.MAX_VALUE;
		if (!fold) {
			for (int cp : query) {
				if (isLetter(cp)) {
					continue;
				}
				char unit = cp < Character.MIN_SUPPLEMENTARY_CODE_POINT ? (char) cp : Character.highSurrogate(cp);
				int n = unit + 1 < offsets.length ? offsets[unit + 1] - offsets[unit] : 0;
				if (n < cost) {
					cost = n;
					best = unit;
				}
			}
		}
		return best;
	}

	static int lowerAscii(int cp) {
		return cp >= 'A' && cp <= 'Z' ? cp + ('a' - 'A') : cp;
	}

	private static String lowerAscii(String token) {
		for (int i = 0; i < token.length(); i++) {
			if (lowerAscii(token.charAt(i)) != token.charAt(i)) {
				char[] chars = token.toCharArray();
				for (int j = i; j < chars.length; j++) {
					chars[j] = (char) lowerAscii(chars[j]);
				}
				return new String(chars);
			}
		}
		return token;
	}

	// A label may separate its voltage tier or another Latin abbreviation from the Chinese name with a space.
	static boolean isScriptBoundary(String text, int space, int start, int end) {
		if (space <= start || space + 1 >= end) {
			return false;
		}
		int left = text.codePointBefore(space);
		int right = text.codePointAt(space + 1);
		if (Character.charCount(left) > space - start || Character.charCount(right) > end - space - 1) {
			return false;
		}
		return isLetter(lowerAscii(left)) && Character.isIdeographic(right) ||
			Character.isIdeographic(left) && isLetter(lowerAscii(right));
	}

	private static boolean isLetter(int cp) {
		return cp >= 'a' && cp <= 'z' || cp == 'ü';
	}

	private static void add(byte[] row, IntArrayList sounds, int n) {
		for (int i = 0; i < sounds.size(); i++) {
			row[sounds.getInt(i)] |= (byte) (1 << n);
		}
	}

	private int steps(int cp, int entry, int position) {
		int steps = cp == query[position] ? 2 : 0;
		var row = lengths[position];
		if (row == null || entry == 0) {
			return steps;
		}
		if (entry < 0x8000) {
			return steps | (row[entry] & 255);
		}
		int offset = entry & 0x7fff;
		for (int j = 1; j <= dictionary.multiple[offset]; j++) {
			steps |= row[dictionary.multiple[offset + j]] & 255;
		}
		return steps;
	}

	public boolean matches(String text) {
		return matches(text, 0, text.length());
	}

	boolean matches(String text, int start, int end) {
		if (query.length >= Long.SIZE) {
			return matchesLong(text, start, end);
		}
		long active = 1;
		long accepted = 1L << query.length;
		for (int i = start; i < end;) {
			int cp = Character.codePointAt(text, i);
			if (i + Character.charCount(cp) > end) {
				cp = text.charAt(i);
			}
			i += Character.charCount(cp);
			int entry = dictionary.entry(cp);
			boolean phonetic = (flags & PinyinOption.ENABLED.bit) != 0;
			if (phonetic) {
				cp = lowerAscii(cp);
			}
			if (fold) {
				cp = dictionary.fold(cp);
			}
			long next = cp == ' ' && active != 1 && phonetic && isScriptBoundary(text, i - 1, start, end) ? active : 1;
			while (active != 0) {
				int position = Long.numberOfTrailingZeros(active);
				next |= (long) steps(cp, entry, position) << position;
				active &= active - 1;
			}
			if ((next & accepted) != 0) {
				return true;
			}
			active = next;
		}
		return false;
	}

	private boolean matchesLong(String text, int start, int end) {
		BitSet active = new BitSet(query.length + 1);
		BitSet next = new BitSet(query.length + 1);
		active.set(0);
		for (int i = start; i < end;) {
			int cp = text.codePointAt(i);
			if (i + Character.charCount(cp) > end) {
				cp = text.charAt(i);
			}
			i += Character.charCount(cp);
			int entry = dictionary.entry(cp);
			boolean phonetic = (flags & PinyinOption.ENABLED.bit) != 0;
			if (phonetic) {
				cp = lowerAscii(cp);
			}
			next.clear();
			next.set(0);
			if (cp == ' ' && phonetic && isScriptBoundary(text, i - 1, start, end)) {
				next.or(active);
			}
			for (int pos = active.nextSetBit(0); pos >= 0; pos = active.nextSetBit(pos + 1)) {
				int lengths = steps(fold ? dictionary.fold(cp) : cp, entry, pos);
				while (lengths != 0) {
					next.set(pos + Integer.numberOfTrailingZeros(lengths));
					lengths &= lengths - 1;
				}
			}
			if (next.get(query.length)) {
				return true;
			}
			var swap = active;
			active = next;
			next = swap;
		}
		return false;
	}
}
