package mezz.jei.gui.recipes.filtering;

import java.util.BitSet;
import java.util.concurrent.ForkJoinTask;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

/** Blocks own disjoint words; workers never mutate a shared BitSet. */
final class RecipeRowFilter {
	static BitSet matching(BitSet candidates, IntPredicate test, Runnable checkpoint) {
		if (!ForkJoinTask.inForkJoinPool() || candidates.cardinality() < 4096) {
			var result = new BitSet();
			int checked = 0;
			for (int id = candidates.nextSetBit(0); id >= 0; id = candidates.nextSetBit(id + 1)) {
				if ((checked++ & 255) == 0)
					checkpoint.run();
				if (test.test(id))
					result.set(id);
			}
			return result;
		}
		int end = candidates.length();
		long[] words = new long[(end + 63) >>> 6];
		IntStream.range(0, (end + 4095) >>> 12).parallel().forEach(block -> {
			checkpoint.run();
			int limit = Math.min(end, (block + 1) * 4096);
			for (int row = candidates.nextSetBit(block * 4096); row >= 0 && row < limit; row = candidates.nextSetBit(row + 1))
				if (test.test(row))
					words[row >>> 6] |= 1L << row;
		});
		return BitSet.valueOf(words);
	}
}
