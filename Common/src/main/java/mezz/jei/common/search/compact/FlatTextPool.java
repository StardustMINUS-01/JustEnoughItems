package mezz.jei.common.search.compact;

/** One compact String per field. Bounded indexOf prevents matches crossing text boundaries. */
final class FlatTextPool {
	private final int[] offsets;
	private final String text;
	private final long[] masks;

	FlatTextPool(String text, int[] offsets, long[] masks) {
		this.text = text;
		this.offsets = offsets;
		this.masks = masks;
	}

	static long mask(String token) {
		long mask = 0;
		for (int i = 0; i < token.length(); i++) {
			mask |= 1L << token.charAt(i);
		}
		return mask;
	}

	boolean contains(int id, String token, long mask) {
		return (masks[id] & mask) == mask && text.indexOf(token, offsets[id], offsets[id + 1]) >= 0;
	}

	long[][] buildRecall() {
		long[][] lanes = new long[0][];
		var dictionary = PinyinDictionary.INSTANCE;
		for (int id = 0; id < offsets.length - 1; id++) {
			int mask = 0;
			boolean convertible = false;
			for (int i = offsets[id]; i < offsets[id + 1];) {
				int cp = text.codePointAt(i);
				if (i + Character.charCount(cp) > offsets[id + 1]) {
					cp = text.charAt(i);
				}
				i += Character.charCount(cp);
				int lower = PinyinQuery.lowerAscii(cp);
				convertible |= lower != cp;
				cp = lower;
				if (cp >= 'a' && cp <= 'z' || cp == 'ü') {
					mask |= 1 << ((cp == 'ü' ? 'v' : cp) - 'a');
				} else if (cp >= 0x2e80) {
					int entry = dictionary.entry(cp);
					convertible |= entry != 0 || dictionary.canFold(cp);
					mask |= dictionary.initials(entry);
				}
			}
			if (convertible) {
				if (lanes.length == 0) {
					lanes = new long[27][(offsets.length + 62) >>> 6];
				}
				lanes[26][id >>> 6] |= 1L << id;
				while (mask != 0) {
					lanes[Integer.numberOfTrailingZeros(mask)][id >>> 6] |= 1L << id;
					mask &= mask - 1;
				}
			}
		}
		return lanes;
	}

	boolean matches(int id, PinyinQuery query) {
		return query.matches(text, offsets[id], offsets[id + 1]);
	}

	String text(int id) {
		return text.substring(offsets[id], offsets[id + 1]);
	}
}
