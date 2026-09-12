package mezz.jei.gui.match;

/** Tracks structural boundaries without interpreting values inside SNBT strings. */
public final class ExpressionSyntax {
	private int depth;
	private char quote;
	private boolean escaped;

	public boolean isTopLevel() {
		return depth == 0 && !isQuoted();
	}

	public boolean isQuoted() {
		return quote != 0;
	}

	public void accept(char c) {
		if (isQuoted()) {
			if (escaped) {
				escaped = false;
			} else if (c == '\\') {
				escaped = true;
			} else if (c == quote) {
				quote = 0;
			}
		} else if (c == '"' || c == '\'') {
			quote = c;
		} else if (c == '(' || c == '{' || c == '[') {
			depth++;
		} else if (c == ')' || c == '}' || c == ']') {
			depth--;
		}
	}
}
