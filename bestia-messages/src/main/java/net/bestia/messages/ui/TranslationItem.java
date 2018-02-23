package net.bestia.messages.ui;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TranslationItem implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String key;
	private final String value;

	public TranslationItem() {
		key = "UNKNOWN";
		value = "NOT-SET";
	}

	@JsonCreator
	public TranslationItem(@JsonProperty("k") String key) {
		this.key = key;
		this.value = null;
	}

	@JsonCreator
	public TranslationItem(@JsonProperty("k") String key, @JsonProperty("v") String value) {
		this.key = key;
		this.value = value;
	}

	@JsonProperty("k")
	public String getKey() {
		return key;
	}

	@JsonProperty("v")
	public String getValue() {
		return value;
	}

	@Override
	public String toString() {
		return String.format("TranslationItem[key: %s, value: %s]", key, value);
	}
}