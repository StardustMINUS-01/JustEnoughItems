package mezz.jei.gui.recipes.filtering;

/** Offsets into a flat array. Most recipe rows fit within 16 bits of their block's first offset. */
final class RecipeRowOffsets {
	private static final int BLOCK_SHIFT = 7;
	private final int[] bases;
	private final char[] relative;

	RecipeRowOffsets(int[] offsets) {
		int count = offsets.length;
		for (int i = 0; i < count; i++) {
			if (offsets[i] - offsets[(i >>> BLOCK_SHIFT) << BLOCK_SHIFT] > Character.MAX_VALUE) {
				bases = offsets;
				relative = null;
				return;
			}
		}
		bases = new int[((count - 1) >>> BLOCK_SHIFT) + 1];
		relative = new char[count];
		for (int i = 0; i < count; i++) {
			int block = i >>> BLOCK_SHIFT;
			if ((i & ((1 << BLOCK_SHIFT) - 1)) == 0)
				bases[block] = offsets[i];
			relative[i] = (char) (offsets[i] - bases[block]);
		}
	}

	int get(int row) {
		return relative == null ? bases[row] : bases[row >>> BLOCK_SHIFT] + relative[row];
	}
}
