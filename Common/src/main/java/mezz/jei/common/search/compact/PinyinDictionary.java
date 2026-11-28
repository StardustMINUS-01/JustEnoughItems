package mezz.jei.common.search.compact;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

final class PinyinDictionary {
	static final PinyinDictionary INSTANCE = load();
	final String[] sounds;
	final char[] multiple;
	private final char[][] pages = new char[0x1100][];
	private final int[] variants;
	private final int[] simplified;
	private final java.util.BitSet foldable = new java.util.BitSet();

	private PinyinDictionary(DataInputStream input) throws IOException {
		if (input.readInt() != 0x4a595031) {
			throw new IOException("Unsupported JEI pinyin dictionary");
		}
		sounds = new String[input.readInt() + 1];
		multiple = new char[input.readInt()];
		int[] points = new int[input.readInt()];
		variants = new int[input.readInt()];
		simplified = new int[variants.length];
		for (int i = 1; i < sounds.length; i++) {
			sounds[i] = new String(input.readNBytes(input.readUnsignedByte()), StandardCharsets.US_ASCII);
		}
		for (int i = 0; i < multiple.length; i++) {
			multiple[i] = input.readChar();
		}
		int cp = 0;
		for (int i = 0; i < points.length; i++) {
			points[i] = cp += readVarInt(input);
		}
		byte[] low = input.readNBytes(points.length);
		for (int i = 0; i < points.length; i++) {
			int page = points[i] >>> 8;
			if (pages[page] == null) {
				pages[page] = new char[256];
			}
			pages[page][points[i] & 255] = (char) ((input.readUnsignedByte() << 8) | (low[i] & 255));
		}
		cp = 0;
		for (int i = 0; i < variants.length; i++) {
			variants[i] = cp += readVarInt(input);
			int delta = readVarInt(input);
			simplified[i] = cp + ((delta >>> 1) ^ -(delta & 1));
			foldable.set(cp);
			foldable.set(simplified[i]);
		}
	}

	private static int readVarInt(DataInputStream input) throws IOException {
		int result = 0;
		for (int shift = 0; shift < 32; shift += 7) {
			int b = input.readUnsignedByte();
			result |= (b & 127) << shift;
			if (b < 128) {
				return result;
			}
		}
		throw new IOException("Invalid pinyin dictionary offset");
	}

	private static PinyinDictionary load() {
		try (var input = new DataInputStream(new BufferedInputStream(Objects.requireNonNull(
			PinyinDictionary.class.getResourceAsStream("/assets/jei/search/pinyin_dict.bin"))))) {
			return new PinyinDictionary(input);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	int entry(int cp) {
		char[] page = pages[cp >>> 8];
		return page == null ? 0 : page[cp & 255];
	}

	boolean canFold(int cp) {
		return foldable.get(cp);
	}

	int fold(int cp) {
		if (cp < 0x2e80) {
			return cp;
		}
		int i = Arrays.binarySearch(variants, cp);
		return i < 0 ? cp : simplified[i];
	}

	int initials(int entry) {
		if (entry == 0) {
			return 0;
		}
		if (entry < 0x8000) {
			return 1 << (sounds[entry].charAt(0) - 'a');
		}
		int offset = entry & 0x7fff;
		int bits = 0;
		for (int i = 1; i <= multiple[offset]; i++) {
			bits |= 1 << (sounds[multiple[offset + i]].charAt(0) - 'a');
		}
		return bits;
	}
}
