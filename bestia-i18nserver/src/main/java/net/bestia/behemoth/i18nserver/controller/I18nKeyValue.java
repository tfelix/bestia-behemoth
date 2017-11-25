package net.bestia.behemoth.i18nserver.controller;

public class I18nKeyValue {

	private final String key;
	private final String value;

	public I18nKeyValue(String key, String value) {

		this.key = key;
		this.value = value;
	}

	public String getKey() {
		return key;
	}

	public String getValue() {
		return value;
	}
}