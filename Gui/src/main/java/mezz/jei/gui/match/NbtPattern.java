package mezz.jei.gui.match;

import org.jetbrains.annotations.Nullable;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Structural SNBT matching; every actual field must be covered by the pattern. */
public final class NbtPattern {
	private final Predicate<Tag> pattern;
	private final boolean matchesEmpty;

	private NbtPattern(Predicate<Tag> pattern) {
		this.pattern = pattern;
		this.matchesEmpty = pattern.test(new CompoundTag());
	}

	public static NbtPattern parse(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		if (!reader.canRead() || reader.peek() != '{') {
			throw new IllegalArgumentException("Expected NBT compound");
		}
		return new NbtPattern(readValue(reader, 0));
	}

	public boolean matches(@Nullable CompoundTag tag) {
		return tag == null ? matchesEmpty : pattern.test(tag);
	}

	private record Field(StringPattern key, Predicate<Tag> value) {}
	private record StringPattern(String literal, Pattern wildcard) implements Predicate<String> {
		@Override
		public boolean test(String value) {
			return wildcard == null ? literal.equals(value) : wildcard.matcher(value).matches();
		}
	}

	private static Predicate<Tag> readValue(StringReader reader, int depth) throws CommandSyntaxException {
		// Match the vanilla NBT nesting limit for user-supplied structures.
		if (depth > 512) {
			throw new IllegalArgumentException("NBT pattern is too deeply nested");
		}
		reader.skipWhitespace();
		if (reader.peek() == '*') {
			reader.skip();
			return value -> true;
		}
		if (reader.peek() == '{') {
			reader.skip();
			List<Field> fields = new ArrayList<>();
			while (readNext(reader, '}', fields.isEmpty())) {
				StringPattern key = readStringPattern(reader);
				reader.skipWhitespace();
				reader.expect(':');
				fields.add(new Field(key, readValue(reader, depth + 1)));
			}
			if (fields.stream().allMatch(field -> field.key().wildcard() == null)) {
				long size = fields.stream().map(field -> field.key().literal()).distinct().count();
				return value -> value instanceof CompoundTag object && object.size() == size && fields.stream().allMatch(field -> {
					Tag entry = object.get(field.key().literal());
					return entry != null && field.value().test(entry);
				});
			}
			return value -> matchesObject(fields, value);
		}
		if (reader.peek() == '[') {
			reader.skip();
			int collectionType = Tag.TAG_LIST;
			if (reader.canRead(2) && reader.peek(1) == ';') {
				collectionType = switch (reader.read()) {
					case 'B' -> Tag.TAG_BYTE_ARRAY;
					case 'I' -> Tag.TAG_INT_ARRAY;
					case 'L' -> Tag.TAG_LONG_ARRAY;
					default -> throw new IllegalArgumentException("Invalid array type");
				};
				reader.skip();
			}
			int expectedType = collectionType;
			List<Predicate<Tag>> elements = new ArrayList<>();
			while (readNext(reader, ']', elements.isEmpty())) {
				elements.add(readValue(reader, depth + 1));
			}
			return value -> {
				if (!(value instanceof CollectionTag<?> list) || value.getId() != expectedType || list.size() != elements.size()) {
					return false;
				}
				for (int i = 0; i < elements.size(); i++) {
					if (!elements.get(i).test(list.get(i))) {
						return false;
					}
				}
				return true;
			};
		}
		if (StringReader.isQuotedStringStart(reader.peek())) {
			StringPattern pattern = readStringPattern(reader);
			return value -> value instanceof StringTag string && pattern.test(string.getAsString());
		}
		Tag exact = new TagParser(reader).readValue();
		return exact::equals;
	}

	private static boolean matchesObject(List<Field> fields, Tag value) {
		if (!(value instanceof CompoundTag object)) {
			return false;
		}
		for (String key : object.getAllKeys()) {
			boolean covered = false;
			for (Field field : fields) {
				if (field.key().test(key)) {
					if (!field.value().test(object.get(key))) {
						return false;
					}
					covered = true;
				}
			}
			if (!covered) {
				return false;
			}
		}
		return fields.stream().allMatch(field -> object.getAllKeys().stream().anyMatch(field.key()));
	}

	private static boolean readNext(StringReader reader, char end, boolean first) throws CommandSyntaxException {
		reader.skipWhitespace();
		if (!first && reader.canRead() && reader.peek() != end) {
			reader.expect(',');
			reader.skipWhitespace();
		}
		if (reader.canRead() && reader.peek() == end) {
			reader.skip();
			return false;
		}
		return true;
	}

	private static StringPattern readStringPattern(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		if (!StringReader.isQuotedStringStart(reader.peek())) {
			int start = reader.getCursor();
			while (reader.canRead() && (StringReader.isAllowedInUnquotedString(reader.peek()) || reader.peek() == '*')) {
				reader.skip();
			}
			String value = reader.getString().substring(start, reader.getCursor());
			if (value.isEmpty()) {
				throw new IllegalArgumentException("Expected field name");
			}
			return new StringPattern(value, value.contains("*") ? IngredientSelector.compileWildcard(value) : null);
		}
		char quote = reader.read();
		StringBuilder regex = new StringBuilder();
		StringBuilder literal = new StringBuilder();
		boolean wildcard = false;
		while (reader.canRead()) {
			char c = reader.read();
			if (c == quote) {
				return new StringPattern(literal.toString(), wildcard ? Pattern.compile(regex.toString(), Pattern.DOTALL) : null);
			}
			if (c == '\\') {
				char escaped = reader.read();
				if (escaped != '\\' && escaped != quote && escaped != '*') {
					throw new IllegalArgumentException("Invalid string escape");
				}
				regex.append(Pattern.quote(String.valueOf(escaped)));
				literal.append(escaped);
			} else {
				literal.append(c);
				wildcard |= c == '*';
				regex.append(c == '*' ? ".*" : Pattern.quote(String.valueOf(c)));
			}
		}
		throw new IllegalArgumentException("Unterminated string");
	}
}
