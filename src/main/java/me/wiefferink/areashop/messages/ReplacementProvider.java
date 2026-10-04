package me.wiefferink.areashop.messages;

/**
 * Provide replacements for a class to insert into messages.
 */
public interface ReplacementProvider {

	/**
	 * Get the replacement for a variable.
	 * @param variable The variable to replace
	 * @return The replacement for the variable (String, Number, Component or Message), or null if it has no replacement for it
	 */
	Object provideReplacement(String variable);
}
