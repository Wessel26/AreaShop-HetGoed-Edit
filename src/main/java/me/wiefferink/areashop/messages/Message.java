package me.wiefferink.areashop.messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A message that is built from MiniMessage templates.
 * Templates can contain variables that are replaced when the message is used:
 * <ul>
 *     <li><code>%0%</code>: the replacement with that index, as given to {@link #replacements(Object...)}</li>
 *     <li><code>%name%</code>: asked from the {@link ReplacementProvider}s that have been given as replacement</li>
 *     <li><code>%lang:key|argument|argument|%</code>: insert the message with that language key, arguments are used as replacements</li>
 * </ul>
 * Strings that are inserted are never parsed as MiniMessage, so user input cannot inject formatting.
 */
public class Message {

	// Define the symbols used for variables
	public static final String VARIABLE_START = "%";
	public static final String VARIABLE_END = "%";
	public static final String LANGUAGE_KEY_PREFIX = "lang:";
	// Language variable: key with optional arguments, an argument can contain other variables: %lang:key|argument|%0%|%
	private static final String LANGUAGE_VARIABLE = Pattern.quote(VARIABLE_START) + Pattern.quote(LANGUAGE_KEY_PREFIX) + "[a-zA-Z-]+"
			+ "(?:\\|(?:" + Pattern.quote(VARIABLE_START) + "[a-zA-Z0-9]+" + Pattern.quote(VARIABLE_END) + "|[^|" + Pattern.quote(VARIABLE_START) + "])*)*"
			+ Pattern.quote(VARIABLE_END);
	private static final Pattern VARIABLE_PATTERN = Pattern.compile(Pattern.quote(VARIABLE_START) + "(?<name>[a-zA-Z0-9]+)" + Pattern.quote(VARIABLE_END));
	private static final Pattern PREPROCESS_PATTERN = Pattern.compile("(?<lang>" + LANGUAGE_VARIABLE + ")|(?<variable>" + VARIABLE_PATTERN.pattern() + ")");
	private static final Pattern PLACEHOLDER_TAG = Pattern.compile("<p:([a-zA-Z0-9]+)>");
	private static final Pattern MESSAGE_TAG = Pattern.compile("<m:([0-9]+)>");

	// Language variable used to insert a prefix
	public static final String CHATLANGUAGEVARIABLE = "prefix";
	// Maximum depth of messages that insert other messages (protects against messages that include themselves)
	private static final int MAXIMUM_DEPTH = 30;
	private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

	private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
	private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
	private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

	// CONFIGURATION
	private static boolean useInteractiveMessages = true;
	private static boolean useColorsInConsole = false;
	private static MessageProvider messageProvider = null;
	private static Logger logger = Logger.getLogger("AreaShop");

	// INSTANCE VARIABLES
	private final List<Part> parts = new ArrayList<>();
	private Object[] replacements = null;
	private String key = null;
	private boolean doLanguageReplacements = true;
	private Message fallback = null;

	/**
	 * A piece of the message, either template lines or an already resolved component.
	 * @param lines     The lines of the template (concatenated, old markup uses separate lines for hover and click)
	 * @param miniMessage true if the lines are MiniMessage, false if they might contain the old markup
	 * @param component The resolved component, when this is not a template
	 */
	private record Part(List<String> lines, boolean miniMessage, Component component) {

		boolean isEmpty() {
			if(component != null) {
				return PLAIN.serialize(component).isEmpty();
			}
			return lines.stream().allMatch(line -> line == null || line.isEmpty());
		}

		String raw() {
			return String.join("", lines);
		}

		String miniMessageTemplate() {
			return miniMessage ? raw() : LegacyMarkup.convertIfLegacy(lines);
		}
	}

	/**
	 * Initialize the Message class.
	 * @param provider The provider to use for getting messages based on keys
	 * @param log      The logger to use for logging warning and error messages
	 */
	public static void init(MessageProvider provider, Logger log) {
		messageProvider = provider;
		logger = log;
	}

	/**
	 * Enable or disable the use of fancy messages (hover and click).
	 * @param enabled true to enable, false to disable
	 */
	public static void useFancyMessages(boolean enabled) {
		useInteractiveMessages = enabled;
	}

	/**
	 * Enable or disable the use of colors when sending a message to a target that is not a Player (console, log, etcetera).
	 * @param enabled true to enable, false to disable
	 */
	public static void useColorsInConsole(boolean enabled) {
		useColorsInConsole = enabled;
	}

	private Message() {
	}

	/**
	 * Empty message object.
	 * @return the message
	 */
	public static Message empty() {
		return new Message();
	}

	/**
	 * Construct a message from a language key.
	 * @param key The key of the message to use
	 * @return the message
	 */
	public static Message fromKey(String key) {
		Message result = new Message();
		result.key = key;
		if(messageProvider == null) {
			logger.severe("Tried to get message with key " + key + ", but there is no MessageProvider!");
		} else {
			result.parts.add(new Part(List.of(messageProvider.getMessage(key)), true, null));
		}
		return result;
	}

	/**
	 * Construct a message from a string, can use MiniMessage or the old markup.
	 * @param message The message to use
	 * @return the message
	 */
	public static Message fromString(String message) {
		Message result = new Message();
		if(message != null) {
			result.parts.add(new Part(List.of(message), false, null));
		}
		return result;
	}

	/**
	 * Construct a message from a string list, can use MiniMessage or the old markup.
	 * @param message The message to use
	 * @return the message
	 */
	public static Message fromList(List<String> message) {
		Message result = new Message();
		if(message != null) {
			result.parts.add(new Part(new ArrayList<>(message), false, null));
		}
		return result;
	}

	/**
	 * Get the key that has been used to initialize this message (if any).
	 * @return Key used to create this message, or null if none
	 */
	public String getKey() {
		return key;
	}

	/**
	 * Set the replacements to apply to the message.
	 * @param replacements The replacements to apply
	 *                     - ReplacementProvider: Its variables are available, like %region%
	 *                     - Message or Component: Is inserted
	 *                     - other: Inserted as text at its index, like %0%
	 * @return this
	 */
	public Message replacements(Object... replacements) {
		this.replacements = replacements;
		return this;
	}

	/**
	 * Check if the message is empty.
	 * @return true if the message is empty, otherwise false
	 */
	public boolean isEmpty() {
		return parts.stream().allMatch(Part::isEmpty);
	}

	/**
	 * Add the default prefix to the message (if the message is not empty).
	 * @param doIt true if the prefix should be added, otherwise false
	 * @return this
	 */
	public Message prefix(boolean doIt) {
		if(doIt && !isEmpty()) {
			parts.add(0, new Part(List.of(VARIABLE_START + LANGUAGE_KEY_PREFIX + CHATLANGUAGEVARIABLE + VARIABLE_END), true, null));
		}
		return this;
	}

	/**
	 * Add the default prefix to the message.
	 * @return this
	 */
	public Message prefix() {
		return prefix(true);
	}

	/**
	 * Append a message to this message, the appended message uses its own replacements.
	 * @param message The message to append
	 * @return this
	 */
	public Message append(Message message) {
		parts.add(new Part(List.of(), true, message.component()));
		return this;
	}

	/**
	 * Append text to the message, can use MiniMessage or the old markup.
	 * @param line The text to append
	 * @return this
	 */
	public Message append(String line) {
		parts.add(new Part(List.of(line), false, null));
		return this;
	}

	/**
	 * Prepend a message to this message, the prepended message uses its own replacements.
	 * @param message The message to prepend
	 * @return this
	 */
	public Message prepend(Message message) {
		parts.add(0, new Part(List.of(), true, message.component()));
		return this;
	}

	/**
	 * Prepend text to the message, can use MiniMessage or the old markup.
	 * @param line The text to prepend
	 * @return this
	 */
	public Message prepend(String line) {
		parts.add(0, new Part(List.of(line), false, null));
		return this;
	}

	/**
	 * Turn off language replacements for this message.
	 * @return this
	 */
	public Message noLanguageReplacements() {
		doLanguageReplacements = false;
		return this;
	}

	// GETTING THE MESSAGE

	/**
	 * Get the message as a component, with all replacements done.
	 * @return The component for the message
	 */
	public Component component() {
		int depth = DEPTH.get();
		if(depth >= MAXIMUM_DEPTH) {
			logger.severe("Too many recursive replacements for message with key: " + key + " (probably includes itself as replacement), start of the message: " + start());
			return Component.empty();
		}
		DEPTH.set(depth + 1);
		try {
			List<Component> result = new ArrayList<>();
			StringBuilder template = new StringBuilder();
			for(Part part : parts) {
				if(part.component != null) {
					flush(template, result);
					result.add(part.component);
				} else {
					template.append(part.miniMessageTemplate());
				}
			}
			flush(template, result);
			return Component.empty().append(result);
		} finally {
			DEPTH.set(depth);
		}
	}

	private void flush(StringBuilder template, List<Component> result) {
		if(template.length() > 0) {
			result.add(render(template.toString()));
			template.setLength(0);
		}
	}

	/**
	 * Get the message as plain text with legacy color codes, without hover and click.
	 * @return The message with replacements done
	 */
	public String getPlain() {
		return LEGACY.serialize(component());
	}

	/**
	 * Get the text of the message with only the variables replaced, without parsing any formatting.
	 * Intended for using messages from the config as templates.
	 * Inserted language messages are included as text with legacy color codes.
	 * @return The text with replacements done
	 */
	public String getSingle() {
		StringBuilder result = new StringBuilder();
		for(Part part : parts) {
			if(part.component != null) {
				result.append(LEGACY.serialize(part.component));
			} else {
				result.append(substitute(part.raw()));
			}
		}
		return result.toString();
	}

	@Override
	public String toString() {
		return "Message(key:" + key + ", message:" + start() + ")";
	}

	private String start() {
		String result = parts.stream().map(part -> part.component != null ? PLAIN.serialize(part.component) : part.raw()).reduce("", String::concat);
		return result.substring(0, Math.min(200, result.length()));
	}

	// SENDING

	/**
	 * Send the message to a target.
	 * @param target The target to send the message to (Player, CommandSender, Logger, BufferedWriter)
	 * @return this
	 */
	public Message send(Object target) {
		if(target == null || isEmpty()) {
			return this;
		}
		Component component = component();
		if(component.equals(Component.empty()) || PLAIN.serialize(component).isEmpty()) {
			return this;
		}

		if(target instanceof Player player) {
			if(useInteractiveMessages) {
				player.sendMessage(component);
			} else {
				player.sendMessage(LEGACY.deserialize(LEGACY.serialize(component)));
			}
		} else if(target instanceof CommandSender sender) {
			if(useColorsInConsole) {
				sender.sendMessage(component);
			} else {
				sender.sendMessage(PLAIN.serialize(component));
			}
		} else if(target instanceof Logger targetLogger) {
			targetLogger.info(PLAIN.serialize(component));
		} else if(target instanceof BufferedWriter writer) {
			try {
				writer.write(PLAIN.serialize(component));
				writer.newLine();
			} catch(IOException e) {
				logger.log(Level.WARNING, "Exception while writing to BufferedWriter:", e);
			}
		} else {
			logger.warning("Could not send message (key: " + key + ") because the target (" + target.getClass().getName() + ") is not recognized, message: " + PLAIN.serialize(component));
		}
		return this;
	}

	// REPLACING VARIABLES

	/**
	 * Prepared template: variables are turned into tags that the {@link Resolver} resolves.
	 * @param template     The MiniMessage string with variable tags
	 * @param messageCalls The language variables that have been replaced by tags
	 */
	private record Prepared(String template, List<String[]> messageCalls) {
	}

	private Prepared prepare(String text) {
		List<String[]> messageCalls = new ArrayList<>();
		StringBuilder result = new StringBuilder();
		Matcher matcher = PREPROCESS_PATTERN.matcher(text);
		int position = 0;
		while(matcher.find()) {
			result.append(text, position, matcher.start());
			position = matcher.end();
			String match = matcher.group();

			// Escaped variables are used as text
			if(result.length() > 0 && result.charAt(result.length() - 1) == '\\') {
				result.setLength(result.length() - 1);
				result.append(match);
				continue;
			}

			if(matcher.group("lang") != null) {
				if(!doLanguageReplacements) {
					// Keep the language variable as text, but the variables inside it (like %region%) are still replaced
					result.append(VARIABLE_PATTERN.matcher(match).replaceAll(inner -> "<p:" + inner.group("name") + ">"));
					continue;
				}
				String[] call = parseLanguageVariable(match);
				messageCalls.add(call);
				result.append("<m:").append(messageCalls.size() - 1).append('>');
			} else {
				result.append("<p:").append(matcher.group("name")).append('>');
			}
		}
		result.append(text.substring(position));
		return new Prepared(result.toString(), messageCalls);
	}

	/**
	 * Split a language variable into the key and arguments.
	 * @return array with the key followed by the arguments
	 */
	private static String[] parseLanguageVariable(String variable) {
		if(!variable.contains("|")) {
			return new String[]{variable.substring(VARIABLE_START.length() + LANGUAGE_KEY_PREFIX.length(), variable.length() - VARIABLE_END.length())};
		}
		String key = variable.substring(VARIABLE_START.length() + LANGUAGE_KEY_PREFIX.length(), variable.indexOf('|'));
		String[] arguments = variable.substring(variable.indexOf('|') + 1, variable.length() - VARIABLE_END.length()).split("\\|");
		String[] result = new String[arguments.length + 1];
		result[0] = key;
		System.arraycopy(arguments, 0, result, 1, arguments.length);
		return result;
	}

	private Component render(String template) {
		Prepared prepared = prepare(template);
		return MINI_MESSAGE.deserialize(prepared.template, new Resolver(prepared));
	}

	/**
	 * Replace variables in text without parsing it as MiniMessage.
	 */
	private String substitute(String text) {
		Prepared prepared = prepare(text);
		Resolver resolver = new Resolver(prepared);
		String result = PLACEHOLDER_TAG.matcher(prepared.template).replaceAll(match ->
				Matcher.quoteReplacement(resolver.placeholderText(match.group(1))));
		return MESSAGE_TAG.matcher(result).replaceAll(match ->
				Matcher.quoteReplacement(LEGACY.serialize(resolver.messageComponent(Integer.parseInt(match.group(1))))));
	}

	/**
	 * Get the value for an index variable (%0%) or a named variable (%region%).
	 * @return The value, or null if there is none
	 */
	private Object replacement(String name) {
		Object result = ownReplacement(name);
		if(result == null && fallback != null) {
			return fallback.replacement(name);
		}
		return result;
	}

	private Object ownReplacement(String name) {
		if(replacements == null) {
			return null;
		}
		boolean index = name.chars().allMatch(Character::isDigit);
		int number = 0;
		for(Object param : replacements) {
			if(param == null) {
				logger.warning("null replacement for message " + this + " at index " + number);
				param = "";
			}
			if(param instanceof ReplacementProvider provider) {
				if(!index) {
					Object value = provider.provideReplacement(name);
					if(value != null) {
						return value;
					}
				}
			} else {
				if(index && Integer.parseInt(name) == number) {
					return param;
				}
				number++;
			}
		}
		return null;
	}

	private static Component toComponent(Object value) {
		if(value instanceof Message message) {
			return message.component();
		} else if(value instanceof ComponentLike component) {
			return component.asComponent();
		}
		return Component.text(String.valueOf(value));
	}

	private static String toText(Object value) {
		if(value instanceof Message message) {
			return LEGACY.serialize(message.component());
		} else if(value instanceof ComponentLike component) {
			return LEGACY.serialize(component.asComponent());
		}
		return String.valueOf(value);
	}

	/**
	 * Resolves the variable tags of a prepared message.
	 */
	private class Resolver implements TagResolver {
		private final Prepared prepared;

		Resolver(Prepared prepared) {
			this.prepared = prepared;
		}

		String placeholderText(String name) {
			Object value = replacement(name);
			return value == null ? VARIABLE_START + name + VARIABLE_END : toText(value);
		}

		Component placeholderComponent(String name) {
			Object value = replacement(name);
			return value == null ? Component.text(VARIABLE_START + name + VARIABLE_END) : toComponent(value);
		}

		Component messageComponent(int index) {
			String[] call = prepared.messageCalls.get(index);
			// Arguments are resolved with the replacements of this message first
			Object[] arguments = new Object[call.length - 1];
			for(int i = 1; i < call.length; i++) {
				Message argument = new Message();
				argument.parts.add(new Part(List.of(call[i]), true, null));
				argument.fallback = Message.this;
				argument.key = key;
				arguments[i - 1] = argument.component();
			}
			// The inserted message can also use the replacements of this message for what it does not have itself (like %region%)
			Message child = Message.fromKey(call[0]).replacements(arguments);
			child.fallback = Message.this;
			return child.component();
		}

		@Override
		public Tag resolve(String name, ArgumentQueue arguments, Context context) {
			switch(name) {
				case "p":
					return Tag.inserting(placeholderComponent(arguments.popOr("variable name expected").value()));
				case "m":
					return Tag.inserting(messageComponent(Integer.parseInt(arguments.popOr("message index expected").value())));
				case "click":
					// Replaces the default click tag to be able to use variables in the value
					String action = arguments.popOr("click action expected").value();
					String value = arguments.popOr("click value expected").value();
					value = PLACEHOLDER_TAG.matcher(value).replaceAll(match -> Matcher.quoteReplacement(placeholderText(match.group(1))));
					value = MESSAGE_TAG.matcher(value).replaceAll(match ->
							Matcher.quoteReplacement(PLAIN.serialize(messageComponent(Integer.parseInt(match.group(1))))));
					ClickEvent event = switch(action.toLowerCase()) {
						case "run_command" -> ClickEvent.runCommand(value);
						case "suggest_command" -> ClickEvent.suggestCommand(value);
						case "open_url" -> ClickEvent.openUrl(value);
						case "copy_to_clipboard" -> ClickEvent.copyToClipboard(value);
						case "change_page" -> ClickEvent.changePage(value);
						default -> null;
					};
					return event == null ? null : Tag.styling(event);
				default:
					return null;
			}
		}

		@Override
		public boolean has(String name) {
			return name.equals("p") || name.equals("m") || name.equals("click");
		}
	}

}
