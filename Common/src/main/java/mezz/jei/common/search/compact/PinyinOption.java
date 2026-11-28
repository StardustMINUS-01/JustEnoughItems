package mezz.jei.common.search.compact;

public enum PinyinOption {
	ENABLED("pinyinSearch", "", ""),
	FOLD("pinyinFoldVariants", "", ""),
	Z_ZH("pinyinFuzzyZZh", "z", "zh"),
	C_CH("pinyinFuzzyCCh", "c", "ch"),
	S_SH("pinyinFuzzySSh", "s", "sh"),
	L_N("pinyinFuzzyLN", "l", "n"),
	R_L("pinyinFuzzyRL", "r", "l"),
	F_H("pinyinFuzzyFH", "f", "h"),
	AN_ANG("pinyinFuzzyAnAng", "an", "ang"),
	EN_ENG("pinyinFuzzyEnEng", "en", "eng"),
	IN_ING("pinyinFuzzyInIng", "in", "ing"),
	IAN_IANG("pinyinFuzzyIanIang", "ian", "iang"),
	UAN_UANG("pinyinFuzzyUanUang", "uan", "uang");

	public final String key;
	final String from;
	final String to;
	public final int bit;

	PinyinOption(String key, String from, String to) {
		this.key = key;
		this.from = from;
		this.to = to;
		bit = 1 << ordinal();
	}

	String alternate(String syllable) {
		if (ordinal() < AN_ANG.ordinal()) {
			String initial = syllable.startsWith("zh") || syllable.startsWith("ch") || syllable.startsWith("sh")
				? syllable.substring(0, 2) : syllable.substring(0, 1);
			return initial.equals(from) ? to + syllable.substring(from.length()) : initial.equals(to) ? from + syllable.substring(to.length()) : syllable;
		}
		String ending = syllable.replaceFirst("^[bpmfdtnlgkhjqxrzcsyw]h?", "");
		return ending.equals(from) ? syllable.substring(0, syllable.length() - from.length()) + to : ending.equals(to) ? syllable.substring(0, syllable.length() - to.length()) + from : syllable;
	}
}
