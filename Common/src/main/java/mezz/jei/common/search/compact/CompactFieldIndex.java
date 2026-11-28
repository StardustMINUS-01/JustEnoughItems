package mezz.jei.common.search.compact;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.ObjIntConsumer;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/** Immutable substring index. Element ids belong to the caller and can be shared across fields. */
public final class CompactFieldIndex {
	private static final int PHONETIC_LIMIT = 256;
	private final FlatTextPool text;
	private volatile PinyinRecallIndex recall;
	private final HybridPostings postings;
	private final int[] offsets;
	private final int[] candidates;
	private final long[][] hotCharacters;
	private final int textCount;
	private final int wordCount;
	private final int associations;

	private CompactFieldIndex(Builder builder, HybridPostings postings, SearchText searchText) {
		this.postings = postings;
		text = searchText.text;
		offsets = searchText.offsets;
		candidates = searchText.candidates;
		textCount = builder.keys.size();
		associations = builder.associations;
		wordCount = (builder.maxId >>> 6) + 1;
		hotCharacters = new long[searchText.degrees.length][];
		for (int ch = 0; ch < hotCharacters.length; ch++) {
			if (searchText.degrees[ch] > 32768) {
				long[] bits = new long[wordCount];
				for (int i = offsets[ch]; i < offsets[ch + 1]; i++) {
					postings.addTo(candidates[i], bits);
				}
				hotCharacters[ch] = bits;
			}
		}
	}

	private record SearchText(FlatTextPool text, int[] offsets, int[] candidates, int[] degrees) {}

	private static SearchText prepareText(Builder builder) {
		var keys = builder.keys;
		int textCount = keys.size();
		long[] masks = new long[textCount];
		int[] boundaries = new int[textCount + 1];
		char[] distinct = new char[builder.textLength];
		for (int i = 0; i < textCount; i++) {
			String key = keys.get(i);
			boundaries[i + 1] = boundaries[i] + key.length();
			key.getChars(0, key.length(), distinct, boundaries[i]);
		}
		var text = new FlatTextPool(new String(distinct), boundaries, masks);
		if (textCount <= 1024) {
			for (int i = 0; i < textCount; i++) {
				masks[i] = FlatTextPool.mask(keys.get(i));
			}
			return new SearchText(text, new int[0], new int[0], new int[0]);
		}
		// Compact unique units in place. The write cursor never passes the unit being read.
		int[] lastText = new int[Character.MAX_VALUE + 1];
		int[] counts = new int[Character.MAX_VALUE + 1];
		int[] ends = new int[textCount];
		int size = 0;
		boolean highFanout = textCount <= 32768 && builder.associations / textCount >= 16;
		int[] degrees = new int[highFanout ? 128 : 0];
		for (int id = 0; id < textCount; id++) {
			long mask = 0;
			int start = size;
			for (int j = boundaries[id]; j < boundaries[id + 1]; j++) {
				char ch = distinct[j];
				if (lastText[ch] != id + 1) {
					lastText[ch] = id + 1;
					distinct[size++] = ch;
					counts[ch]++;
					mask |= 1L << ch;
				}
			}
			if (highFanout) {
				var more = builder.moreIds.get(id);
				int degree = more == null ? 1 : more.size();
				for (int j = start; j < size; j++) {
					char ch = distinct[j];
					if (ch < degrees.length) {
						degrees[ch] += degree;
					}
				}
			}
			ends[id] = size;
			masks[id] = mask;
		}
		int highest = counts.length - 1;
		while (highest > 0 && counts[highest] == 0) {
			highest--;
		}
		int[] offsets = new int[highest + 2];
		for (int ch = 0; ch <= highest; ch++) {
			offsets[ch + 1] = offsets[ch] + counts[ch];
			counts[ch] = offsets[ch];
		}
		int[] candidates = new int[size];
		int cursor = 0;
		for (int id = 0; id < textCount; id++) {
			while (cursor < ends[id]) {
				candidates[counts[distinct[cursor++]]++] = id;
			}
		}
		return new SearchText(text, offsets, candidates, degrees);
	}

	public static final class Builder {
		private final Object2IntOpenHashMap<String> ids;
		private final List<String> keys;
		private final IntArrayList firstIds;
		private final List<IntArrayList> moreIds;
		private final BitSet unordered = new BitSet();
		private int associations;
		private int textLength;
		private int maxId;
		private boolean frozen;

		public Builder() {
			this(16);
		}

		public Builder(int expectedTexts) {
			ids = new Object2IntOpenHashMap<>(expectedTexts);
			keys = new ArrayList<>(expectedTexts);
			firstIds = new IntArrayList(expectedTexts);
			moreIds = new ArrayList<>(expectedTexts);
			ids.defaultReturnValue(-1);
		}

		public void put(String key, int elementId) {
			if (frozen) {
				throw new IllegalStateException("Field builder has already been frozen");
			}
			if (elementId < 0) {
				throw new IllegalArgumentException("Negative element id");
			}
			int id = ids.putIfAbsent(key, keys.size());
			if (id == -1) {
				textLength = Math.addExact(textLength, key.length());
				keys.add(key);
				firstIds.add(elementId);
				moreIds.add(null);
			} else {
				IntArrayList row = moreIds.get(id);
				if (row == null) {
					row = new IntArrayList(4);
					row.add(firstIds.getInt(id));
					moreIds.set(id, row);
				}
				if (elementId < row.getInt(row.size() - 1)) {
					unordered.set(id);
				}
				row.add(elementId);
			}
			maxId = Math.max(maxId, elementId);
			associations++;
		}

		public CompactFieldIndex build() {
			freeze();
			return new CompactFieldIndex(this, preparePostings(), prepareText(this));
		}

		/** Freezes collected strings and ids while the caller extracts the next field on the game thread. */
		public CompletableFuture<CompactFieldIndex> buildAsync(Executor executor) {
			freeze();
			if (keys.isEmpty()) {
				return CompletableFuture.completedFuture(new CompactFieldIndex(this, preparePostings(), prepareText(this)));
			}
			if (keys.size() > 1024 && associations / keys.size() >= 16) {
				var rows = CompletableFuture.supplyAsync(this::preparePostings, executor);
				var searchText = CompletableFuture.supplyAsync(() -> prepareText(this), executor);
				return rows.thenCombine(searchText, (postings, text) -> new CompactFieldIndex(this, postings, text));
			}
			return CompletableFuture.supplyAsync(() -> new CompactFieldIndex(this, preparePostings(), prepareText(this)), executor);
		}

		private HybridPostings preparePostings() {
			return new HybridPostings(firstIds, moreIds, unordered);
		}

		private void freeze() {
			if (frozen) {
				throw new IllegalStateException("Field builder has already been frozen");
			}
			frozen = true;
		}
	}

	/** The caller owns one session per field and input stream. Do not share between threads. */
	public static final class Session {
		private record Step(String token, int[] ids, int start, int end) {}
		private final ArrayDeque<Step> steps = new ArrayDeque<>();
		private CompactFieldIndex field;
		private final ArrayDeque<Step> phoneticSteps = new ArrayDeque<>();
		private int phoneticFlags;

		private Step phoneticPrefix(String token, int flags) {
			if (flags != phoneticFlags) {
				phoneticFlags = flags;
				phoneticSteps.clear();
			}
			while (!phoneticSteps.isEmpty()) {
				var step = phoneticSteps.getLast();
				if (token.startsWith(step.token)) {
					return step;
				}
				phoneticSteps.removeLast();
			}
			return null;
		}

		private void rememberPhonetic(String token, IntArrayList ids) {
			if (ids.size() <= PHONETIC_LIMIT && !Character.isHighSurrogate(token.charAt(token.length() - 1))) {
				if (phoneticSteps.size() == 4) {
					phoneticSteps.removeFirst();
				}
				phoneticSteps.addLast(new Step(token, ids.toIntArray(), 0, ids.size()));
			}
		}

		private Step previous(CompactFieldIndex index, String token) {
			if (field != index) {
				steps.clear();
				phoneticSteps.clear();
				field = index;
			}
			while (!steps.isEmpty()) {
				Step step = steps.getLast();
				if (token.contains(step.token)) {
					return step;
				}
				steps.removeLast();
			}
			return null;
		}

		private void remember(String token, int[] ids, int start, int end) {
			if (end - start <= 16384 && (steps.isEmpty() || !steps.getLast().token.equals(token))) {
				if (steps.size() == 8) {
					steps.removeFirst();
				}
				steps.addLast(new Step(token, ids, start, end));
			}
		}
	}

	/** Unions matching ids into a caller-owned bitmap covering this field's largest id. */
	public void orMatches(String token, long[] result, Session session) {
		var previous = session.previous(this, token);
		if (token.length() == 1 && token.charAt(0) < hotCharacters.length) {
			long[] hot = hotCharacters[token.charAt(0)];
			if (hot != null) {
				for (int i = 0; i < hot.length; i++) {
					result[i] |= hot[i];
				}
				return;
			}
		}
		int[] ids = candidates;
		int start = 0;
		int end = textCount;
		boolean linear = offsets.length == 0 || token.isEmpty();
		if (!linear) {
			int rarest = Integer.MAX_VALUE;
			for (int i = 0; i < token.length(); i++) {
				int ch = token.charAt(i);
				if (ch + 1 >= offsets.length || offsets[ch] == offsets[ch + 1]) {
					return;
				}
				int size = offsets[ch + 1] - offsets[ch];
				if (size < rarest) {
					rarest = size;
					start = offsets[ch];
					end = offsets[ch + 1];
				}
			}
		}
		boolean exact = !linear && token.length() == 1;
		if (previous != null && previous.end - previous.start < end - start) {
			ids = previous.ids;
			start = previous.start;
			end = previous.end;
			linear = false;
			exact = previous.token.equals(token);
		}
		if (exact) {
			for (int i = start; i < end; i++) {
				postings.addTo(ids[i], result);
			}
			session.remember(token, ids, start, end);
			return;
		}
		long mask = FlatTextPool.mask(token);
		var matches = new IntArrayList(Math.min(end - start, 128));
		for (int i = start; i < end; i++) {
			int id = linear ? i : ids[i];
			if (text.contains(id, token, mask)) {
				postings.addTo(id, result);
				if (matches.size() <= 16384) {
					matches.add(id);
				}
			}
		}
		if (matches.size() <= 16384) {
			session.remember(token, matches.toIntArray(), 0, matches.size());
		}
	}

	public void orMatches(String token, long[] result, Session session, PinyinQuery pinyin) {
		orMatches(pinyin == null ? token : pinyin.literalToken, result, session);
		if (pinyin == null) {
			return;
		}
		var prefix = session.phoneticPrefix(token, pinyin.flags);
		if (prefix != null && prefix.token.equals(token)) {
			for (int id : prefix.ids) {
				postings.addTo(id, result);
			}
			return;
		}
		var matches = new IntArrayList();
		if (prefix != null) {
			for (int id : prefix.ids) {
				if (text.matches(id, pinyin)) {
					addPhoneticMatch(id, result, matches);
				}
			}
			session.rememberPhonetic(token, matches);
			return;
		}
		int unit = offsets.length == 0 ? -1 : pinyin.literalUnit(offsets);
		if (unit >= 0) {
			if (unit + 1 < offsets.length) {
				for (int pos = offsets[unit]; pos < offsets[unit + 1]; pos++) {
					int id = candidates[pos];
					if (text.matches(id, pinyin)) {
						addPhoneticMatch(id, result, matches);
					}
				}
			}
			session.rememberPhonetic(token, matches);
			return;
		}
		if ((pinyin.flags & PinyinOption.ENABLED.bit) != 0) {
			ensureRecall().collect(pinyin, text, id -> addPhoneticMatch(id, result, matches));
		} else {
			for (int id = 0; id < textCount; id++) {
				if (text.matches(id, pinyin)) {
					addPhoneticMatch(id, result, matches);
				}
			}
		}
		session.rememberPhonetic(token, matches);
	}

	public void warmPinyin(int flags) {
		PinyinQuery.warmLexicon(flags);
		ensureRecall();
	}

	private PinyinRecallIndex ensureRecall() {
		var current = recall;
		if (current == null) {
			synchronized (this) {
				current = recall;
				if (current == null) {
					recall = current = new PinyinRecallIndex(text, textCount);
				}
			}
		}
		return current;
	}

	private void addPhoneticMatch(int id, long[] result, IntArrayList matches) {
		postings.addTo(id, result);
		if (matches.size() <= PHONETIC_LIMIT) {
			matches.add(id);
		}
	}

	public int associations() {
		return associations;
	}

	void forEachAssociation(ObjIntConsumer<String> consumer) {
		for (int i = 0; i < textCount; i++) {
			String key = text.text(i);
			postings.forEachId(i, id -> consumer.accept(key, id));
		}
	}
}
