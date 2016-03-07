package net.bestia.messages;

import java.util.ArrayList;
import java.util.List;

import net.bestia.messages.TranslationRequestMessage.TranslationItem;

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
public class TranslationResponseMessage extends AccountMessage {

	@JsonIgnore
	private static final long serialVersionUID = 1L;
	
	public static final String MESSAGE_ID = "translation.response";

	@JsonProperty("is")
	private List<TranslationItem> items = new ArrayList<>();

	/**
	 * The token is put in the answer of this message. Sync these requests are
	 * async the token can be used by the client to identify the answers of the
	 * request.
	 */
	@JsonProperty("t")
	private String token;
	
	public TranslationResponseMessage() {
		
	}
	
	public TranslationResponseMessage(TranslationRequestMessage msg) {
		super(msg);
		this.token = msg.getToken();
	}
	
	public void setItems(List<TranslationItem> items) {
		this.items.clear();
		this.items.addAll(items);
	}

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
		return getClientMessagePath();
	}

	@Override
	public String toString() {
		return String.format("TranslationResponseMessage[items: %s]",
				items.toString());
	}
}
