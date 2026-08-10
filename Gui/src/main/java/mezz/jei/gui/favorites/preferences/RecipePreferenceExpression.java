package mezz.jei.gui.favorites.preferences;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;

public final class RecipePreferenceExpression {
	private final List<Expr> tiers;
	private final Kind kind;

	private enum Kind {
		INGREDIENT,
		UID
	}

	private RecipePreferenceExpression(List<Expr> tiers, Kind kind) {
		this.tiers = tiers;
		this.kind = kind;
	}

	public static Optional<RecipePreferenceExpression> parseIngredient(String text) {
		return parse(text, Kind.INGREDIENT);
	}

	public static Optional<RecipePreferenceExpression> parseUid(String text) {
		return parse(text, Kind.UID);
	}

	public OptionalInt rank(List<RecipePreferenceIngredientInfo> ingredients) {
		if (kind != Kind.INGREDIENT) {
			throw new IllegalStateException("Cannot rank a uid expression with ingredients");
		}
		for (int i = 0; i < tiers.size(); i++) {
			if (eval(tiers.get(i), ingredients)) {
				return OptionalInt.of(i);
			}
		}
		return OptionalInt.empty();
	}

	public OptionalInt rank(ResourceLocation recipeUid) {
		if (kind != Kind.UID) {
			throw new IllegalStateException("Cannot rank an ingredient expression with a recipe uid");
		}
		for (int i = 0; i < tiers.size(); i++) {
			if (eval(tiers.get(i), recipeUid)) {
				return OptionalInt.of(i);
			}
		}
		return OptionalInt.empty();
	}

	private static Optional<RecipePreferenceExpression> parse(String text, Kind kind) {
		if (text == null || text.isBlank()) {
			return Optional.empty();
		}
		List<String> tierTexts = splitTiers(text);
		if (tierTexts.isEmpty()) {
			return Optional.empty();
		}
		List<Expr> tiers = new ArrayList<>(tierTexts.size());
		for (String tierText : tierTexts) {
			Optional<Expr> expr = new Parser(tierText, kind).parse();
			if (expr.isEmpty()) {
				return Optional.empty();
			}
			tiers.add(expr.get());
		}
		return Optional.of(new RecipePreferenceExpression(List.copyOf(tiers), kind));
	}

	private static List<String> splitTiers(String text) {
		List<String> tiers = new ArrayList<>();
		int depth = 0;
		int start = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '(') {
				depth++;
			} else if (c == ')') {
				depth--;
				if (depth < 0) {
					return List.of();
				}
			} else if (c == ';' && depth == 0) {
				tiers.add(text.substring(start, i));
				start = i + 1;
			}
		}
		if (depth != 0) {
			return List.of();
		}
		tiers.add(text.substring(start));
		return tiers.stream()
			.map(String::trim)
			.filter(tier -> !tier.isEmpty())
			.toList();
	}

	private boolean eval(Expr expr, List<RecipePreferenceIngredientInfo> ingredients) {
		if (expr instanceof IngredientAtom atom) {
			return ingredients.stream().anyMatch(atom.target()::matches);
		}
		if (expr instanceof Not not) {
			return !eval(not.inner(), ingredients);
		}
		if (expr instanceof And and) {
			return eval(and.left(), ingredients) && eval(and.right(), ingredients);
		}
		if (expr instanceof Or or) {
			return eval(or.left(), ingredients) || eval(or.right(), ingredients);
		}
		throw new IllegalStateException("Unexpected expression node: " + expr);
	}

	private boolean eval(Expr expr, ResourceLocation recipeUid) {
		if (expr instanceof UidAtom atom) {
			return atom.pattern().matcher(recipeUid.toString()).matches();
		}
		if (expr instanceof Not not) {
			return !eval(not.inner(), recipeUid);
		}
		if (expr instanceof And and) {
			return eval(and.left(), recipeUid) && eval(and.right(), recipeUid);
		}
		if (expr instanceof Or or) {
			return eval(or.left(), recipeUid) || eval(or.right(), recipeUid);
		}
		throw new IllegalStateException("Unexpected expression node: " + expr);
	}

	private sealed interface Expr permits IngredientAtom, UidAtom, Not, And, Or {
	}

	private record IngredientAtom(RecipePreferenceTarget target) implements Expr {
	}

	private record UidAtom(Pattern pattern) implements Expr {
	}

	private record Not(Expr inner) implements Expr {
	}

	private record And(Expr left, Expr right) implements Expr {
	}

	private record Or(Expr left, Expr right) implements Expr {
	}

	private static final class Parser {
		private final String text;
		private final Kind kind;
		private int index = 0;

		private Parser(String text, Kind kind) {
			this.text = text;
			this.kind = kind;
		}

		private Optional<Expr> parse() {
			try {
				Expr expr = parseOr();
				skipWhitespace();
				if (index != text.length()) {
					return Optional.empty();
				}
				return Optional.of(expr);
			} catch (RuntimeException e) {
				return Optional.empty();
			}
		}

		private Expr parseOr() {
			Expr left = parseAnd();
			while (true) {
				skipWhitespace();
				if (consume('|')) {
					left = new Or(left, parseAnd());
				} else {
					return left;
				}
			}
		}

		private Expr parseAnd() {
			Expr left = parseNot();
			while (true) {
				skipWhitespace();
				if (consume('&')) {
					left = new And(left, parseNot());
				} else {
					return left;
				}
			}
		}

		private Expr parseNot() {
			skipWhitespace();
			if (consume('!')) {
				return new Not(parseNot());
			}
			return parsePrimary();
		}

		private Expr parsePrimary() {
			skipWhitespace();
			if (consume('(')) {
				Expr inner = parseOr();
				skipWhitespace();
				if (!consume(')')) {
					throw new IllegalArgumentException("Expected ')'");
				}
				return inner;
			}
			if (consume(';')) {
				throw new IllegalArgumentException("';' is only allowed at the top level");
			}
			String atom = readAtom();
			if (atom.isEmpty()) {
				throw new IllegalArgumentException("Expected selector");
			}
			return switch (kind) {
				case INGREDIENT -> RecipePreferenceTarget.parse(atom)
					.map(IngredientAtom::new)
					.orElseThrow(() -> new IllegalArgumentException("Invalid selector: " + atom));
				case UID -> new UidAtom(RecipePreferenceTarget.compileWildcard(atom));
			};
		}

		private String readAtom() {
			int start = index;
			while (index < text.length() && !isOperator(text.charAt(index))) {
				index++;
			}
			return text.substring(start, index).trim();
		}

		private static boolean isOperator(char c) {
			return c == '!' || c == '&' || c == '|' || c == '(' || c == ')' || c == ';' || Character.isWhitespace(c);
		}

		private void skipWhitespace() {
			while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
				index++;
			}
		}

		private boolean consume(char expected) {
			if (index < text.length() && text.charAt(index) == expected) {
				index++;
				return true;
			}
			return false;
		}
	}
}
