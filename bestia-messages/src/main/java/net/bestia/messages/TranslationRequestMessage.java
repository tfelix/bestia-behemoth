package net.bestia.messages;

import java.util.List;

import net.bestia.model.domain.TranslationCategory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This message requests a translation from the server. The data is fetched via
 * our I18N interface and then delivered to the client with an translation
 * response message.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class TranslationRequestMessage extends Message {

	public class TranslationItem {
		private TranslationCategory category;
		private String key;
		private String value;

		public TranslationItem() {

		}

		public TranslationCategory getCategory() {
			return category;
		}

		public void setCategory(TranslationCategory type) {
			this.category = type;
		}

		public String getKey() {
			return key;
		}

		public void setKey(String key) {
			this.key = key;
		}
		
		public String getValue() {
			return value;
		}
		
		public void setValue(String value) {
			this.value = value;
		}

		@Override
		public String toString() {
			return String.format("TranslationItem[type: {}, key: {}]",
					category.toString(), key);
		}
	}

	@JsonIgnore
	private static final long serialVersionUID = 1L;
	
	public static final String MESSAGE_ID = "translation.request";

	@JsonProperty("is")
	private List<TranslationItem> items;

	/**
	 * The token is put in the answer of this message. Synce these requests are
	 * async the token can be used by the client to identify the answers of the
	 * request.
	 */
	@JsonProperty("t")
	private String token;

	public List<TranslationItem> getItems() {
		return items;
	}
	
	public void setToken(String token) {
		this.token = token;
	}
	
	public String getToken() {
		return token;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getZoneMessagePath();
	}

	@Override
	public String toString() {
		return String.format("TranslationRequestMessage[items: {}]",
				items.toString());
	}
}
