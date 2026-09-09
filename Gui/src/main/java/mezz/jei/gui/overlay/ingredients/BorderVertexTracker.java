package mezz.jei.gui.overlay.ingredients;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/** Reuses primitive storage for this frame's visible border vertices, not browsing history. */
final class BorderVertexTracker {
	private final Object2IntOpenHashMap<String> frames = new Object2IntOpenHashMap<>();
	private final Long2IntOpenHashMap vertices = new Long2IntOpenHashMap();
	private final LongOpenHashSet drawn = new LongOpenHashSet();

	BorderVertexTracker() {
		frames.defaultReturnValue(-1);
		vertices.defaultReturnValue(-1);
	}

	void clear() {
		frames.clear();
		vertices.clear();
		drawn.clear();
	}

	boolean claim(String frameKey, int x, int y) {
		int frame = frames.getInt(frameKey);
		if (frame < 0) {
			frame = frames.size();
			frames.put(frameKey, frame);
		}
		long position = pair(x, y);
		int vertex = vertices.get(position);
		if (vertex < 0) {
			vertex = vertices.size();
			vertices.put(position, vertex);
		}
		return drawn.add(pair(frame, vertex));
	}

	private static long pair(int first, int second) {
		return ((long) first << 32) | (second & 0xFFFFFFFFL);
	}
}
