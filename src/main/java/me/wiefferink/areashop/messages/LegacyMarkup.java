package me.wiefferink.areashop.messages;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts the old InteractiveMessenger markup to MiniMessage.
 * Old markup: <code>[gold]</code>, <code>[bold]...[/bold]</code>, <code>&amp;6</code> and separate <code>hover:</code>/<code>command:</code> lines.
 * Used to migrate language files and config values of existing installations.
 */
public final class LegacyMarkup {

	private static final char COLOR_CHAR = '§';
	private static final Pattern TAG_PATTERN = Pattern.compile("(\\[[/a-zA-Z1-9_]+?])|([&" + COLOR_CHAR + "][0-9a-zA-Z])|(\\\\n)");
	private static final Pattern INTERACTIVE_KEY = Pattern.compile("^[ \\t]*(hover|command|suggest|link): ?(.*)$", Pattern.DOTALL);

	/** Marker used for formatting changes that are not a color */
	private static final String FORMAT = "format";

	private static final Map<String, String> COLORS = new HashMap<>();
	private static final Map<String, String> FORMATS = new HashMap<>();
	private static final Map<Character, String> NATIVE = new HashMap<>();

	static {
		for(String color : Arrays.asList("white", "black", "blue", "dark_blue", "green", "dark_green", "aqua", "dark_aqua", "red", "dark_red",
				"light_purple", "dark_purple", "yellow", "gold", "gray", "dark_gray")) {
			COLORS.put(color, color);
			COLORS.put(color.replace("_", ""), color);
		}
		COLORS.put("grey", "gray");
		COLORS.put("darkgrey", "dark_gray");
		COLORS.put("dark_grey", "dark_gray");

		for(String format : Arrays.asList("bold", "italic", "underline", "strikethrough", "obfuscate")) {
			String target = switch(format) {
				case "underline" -> "underlined";
				case "obfuscate" -> "obfuscated";
				default -> format;
			};
			FORMATS.put(format, target);
			FORMATS.put(format.substring(0, 1), target);
		}
		FORMATS.put("strike", "strikethrough");

		String[] nativeColors = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray", "dark_gray", "blue"};
		for(int i = 0; i < nativeColors.length; i++) {
			NATIVE.put(Character.forDigit(i, 10), nativeColors[i]);
		}
		NATIVE.put('a', "green");
		NATIVE.put('b', "aqua");
		NATIVE.put('c', "red");
		NATIVE.put('d', "light_purple");
		NATIVE.put('e', "yellow");
		NATIVE.put('f', "white");
		NATIVE.put('l', "bold");
		NATIVE.put('m', "strikethrough");
		NATIVE.put('o', "italic");
		NATIVE.put('n', "underlined");
		NATIVE.put('k', "obfuscated");
		NATIVE.put('r', "reset");
	}

	private LegacyMarkup() {
	}

	/**
	 * Check if lines use the old markup.
	 * @param lines The lines to check
	 * @return true if one of the lines contains old markup
	 */
	public static boolean isLegacy(List<String> lines) {
		for(String line : lines) {
			if(line == null) {
				continue;
			}
			if(INTERACTIVE_KEY.matcher(line).matches()) {
				return true;
			}
			Matcher matcher = TAG_PATTERN.matcher(line);
			while(matcher.find()) {
				if(!isEscaped(line, matcher.start()) && lookup(matcher.group()) != null) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Convert lines to a MiniMessage string if they use the old markup, otherwise just join them.
	 * @param lines The lines to convert
	 * @return MiniMessage string
	 */
	public static String convertIfLegacy(List<String> lines) {
		if(isLegacy(lines)) {
			return convert(lines);
		}
		return String.join("", lines);
	}

	/**
	 * Convert lines with old markup to a MiniMessage string.
	 * @param lines The lines to convert (list items of a language file, or a single line)
	 * @return MiniMessage string
	 */
	public static String convert(List<String> lines) {
		// Group the lines: text lines with the interactive lines that follow them
		List<Segment> segments = new ArrayList<>();
		for(String line : lines) {
			if(line == null) {
				continue;
			}
			Matcher interactive = INTERACTIVE_KEY.matcher(line);
			if(interactive.matches()) {
				if(segments.isEmpty()) {
					continue;
				}
				Segment segment = segments.get(segments.size() - 1);
				String value = interactive.group(2);
				switch(interactive.group(1)) {
					case "hover" -> segment.hover.add(value);
					case "command" -> segment.click = new String[]{"run_command", value};
					case "suggest" -> segment.click = new String[]{"suggest_command", value};
					case "link" -> segment.click = new String[]{"open_url", value};
					default -> {
					}
				}
			} else {
				segments.add(new Segment(line));
			}
		}

		State state = new State();
		StringBuilder result = new StringBuilder();
		for(Segment segment : segments) {
			List<Token> tokens = tokenize(segment.text, state, false);
			if(segment.hover.isEmpty() && segment.click == null) {
				for(Token token : tokens) {
					result.append(token.value);
				}
				continue;
			}

			boolean hasText = tokens.stream().anyMatch(token -> token.text);
			if(!hasText) {
				for(Token token : tokens) {
					result.append(token.value);
				}
				continue;
			}

			String open = "";
			String close = "";
			if(segment.click != null) {
				open += "<click:" + segment.click[0] + ":'" + quote(segment.click[1]) + "'>";
				close = "</click>" + close;
			}
			if(!segment.hover.isEmpty()) {
				open += "<hover:show_text:'" + quote(hoverContent(segment.hover)) + "'>";
				close = "</hover>" + close;
			}

			// Wrap everything up to the last text, formatting after it only changes the state.
			// Formatting that is still active at the end does not continue after the wrapper, so it is restored below.
			int last = -1;
			for(int i = 0; i < tokens.size(); i++) {
				if(tokens.get(i).text) {
					last = i;
				}
			}
			State before = state.copy();
			result.append(open);
			for(int i = 0; i <= last; i++) {
				String value = tokens.get(i).value;
				if(value.equals("<reset>")) { // A reset also removes hover and click, so close and reopen them
					result.append(close).append(value).append(open);
				} else {
					result.append(value);
				}
			}
			result.append(close);
			if(!state.equals(before)) {
				result.append("<reset>");
				if(state.color != null) {
					result.append('<').append(state.color).append('>');
				}
				for(String format : state.formats) {
					result.append('<').append(format).append('>');
				}
			}
		}
		return result.toString();
	}

	/**
	 * Convert a single string with old markup.
	 * @param line The line to convert
	 * @return MiniMessage string
	 */
	public static String convert(String line) {
		return convert(List.of(line));
	}

	private static String hoverContent(List<String> hoverLines) {
		StringBuilder content = new StringBuilder();
		for(int i = 0; i < hoverLines.size(); i++) {
			State state = new State();
			List<Token> tokens = tokenize(hoverLines.get(i), state, true);
			if(i > 0) {
				content.append("<newline>");
			}
			for(Token token : tokens) {
				content.append(token.value);
			}
			// Each hover line starts without formatting
			if(i < hoverLines.size() - 1 && (state.color != null || !state.formats.isEmpty())) {
				content.append("<reset>");
			}
		}
		return content.toString();
	}

	/**
	 * Escape a value for use in a quoted MiniMessage argument.
	 */
	private static String quote(String value) {
		return value.replace("\\\\", "\\\\\\\\").replace("'", "\\'");
	}

	private static List<Token> tokenize(String line, State state, boolean inHover) {
		List<Token> tokens = new ArrayList<>();
		Matcher matcher = TAG_PATTERN.matcher(line);
		int position = 0;
		while(matcher.find()) {
			if(isEscaped(line, matcher.start())) {
				continue;
			}
			String[] tag = lookup(matcher.group());
			if(tag == null) {
				continue;
			}
			if(matcher.start() > position) {
				tokens.add(new Token(escapeText(line.substring(position, matcher.start())), true));
			}
			position = matcher.end();

			switch(tag[0]) {
				case "break" -> tokens.add(new Token("<newline>", true));
				case "reset" -> {
					state.color = null;
					state.formats.clear();
					tokens.add(new Token("<reset>", false));
				}
				case "color" -> {
					state.color = tag[1];
					tokens.add(new Token("<" + tag[1] + ">", false));
				}
				default -> { // format
					if(tag[2] != null) {
						state.formats.remove(tag[1]);
						tokens.add(new Token("</" + tag[1] + ">", false));
					} else {
						state.formats.add(tag[1]);
						tokens.add(new Token("<" + tag[1] + ">", false));
					}
				}
			}
		}
		if(position < line.length()) {
			tokens.add(new Token(escapeText(line.substring(position)), true));
		}
		return tokens;
	}

	/**
	 * Resolve a matched tag.
	 * @return null if it is no tag, otherwise {type, name, closing}
	 */
	private static String[] lookup(String match) {
		if(match.equals("\\n")) {
			return new String[]{"break", null, null};
		}
		if(match.charAt(0) == '&' || match.charAt(0) == COLOR_CHAR) {
			String name = NATIVE.get(Character.toLowerCase(match.charAt(1)));
			return name == null ? null : classify(name, false);
		}
		String content = match.substring(1, match.length() - 1).toLowerCase(Locale.ROOT);
		boolean closing = false;
		if(content.startsWith("/")) {
			content = content.substring(1);
			closing = true;
		}
		if(content.equals("break") || content.equals("reset")) {
			return closing ? null : new String[]{content, null, null};
		}
		String color = COLORS.get(content);
		if(color != null) {
			return closing ? null : new String[]{"color", color, null};
		}
		String format = FORMATS.get(content);
		if(format != null) {
			return new String[]{FORMAT, format, closing ? "closing" : null};
		}
		return null;
	}

	private static String[] classify(String name, boolean closing) {
		if(name.equals("reset")) {
			return new String[]{"reset", null, null};
		}
		if(COLORS.containsKey(name)) {
			return new String[]{"color", name, null};
		}
		return new String[]{FORMAT, name, closing ? "closing" : null};
	}

	/**
	 * Check if a match is escaped, backslashes escape backslashes so an uneven count escapes the match.
	 */
	private static boolean isEscaped(String line, int start) {
		int backslashes = 0;
		int index = start - 1;
		while(index >= 0 && line.charAt(index) == '\\') {
			backslashes++;
			index--;
		}
		return backslashes % 2 == 1;
	}

	/**
	 * Turn literal old-markup text into literal MiniMessage text.
	 */
	private static String escapeText(String text) {
		StringBuilder result = new StringBuilder();
		for(int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if(c == '\\' && i + 1 < text.length()) {
				char next = text.charAt(i + 1);
				if(next == '[' || next == '&' || next == COLOR_CHAR) {
					result.append(next);
					i++;
					continue;
				}
				if(next == '%' || next == '\\') { // handled when replacing variables
					result.append(c).append(next);
					i++;
					continue;
				}
			}
			if(c == '<') {
				result.append("\\<");
			} else {
				result.append(c);
			}
		}
		return result.toString();
	}

	private static final class Segment {
		final String text;
		final List<String> hover = new ArrayList<>();
		String[] click = null;

		Segment(String text) {
			this.text = text;
		}
	}

	private static final class Token {
		final String value;
		final boolean text;

		Token(String value, boolean text) {
			this.value = value;
			this.text = text;
		}
	}

	private static final class State {
		String color = null;
		final Set<String> formats = new LinkedHashSet<>();

		@Override
		public boolean equals(Object obj) {
			return obj instanceof State other && java.util.Objects.equals(color, other.color) && formats.equals(other.formats);
		}

		@Override
		public int hashCode() {
			return java.util.Objects.hash(color, formats);
		}

		State copy() {
			State copy = new State();
			copy.color = color;
			copy.formats.addAll(formats);
			return copy;
		}
	}
}
