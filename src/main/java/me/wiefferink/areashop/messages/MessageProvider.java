package me.wiefferink.areashop.messages;

/**
 * Provide messages based on keys, for example from a language file.
 */
public interface MessageProvider {

	/**
	 * Get the MiniMessage template that is linked to the specified key.
	 * @param key The key of the message to get
	 * @return The template, empty if there is none
	 */
	String getMessage(String key);
}
