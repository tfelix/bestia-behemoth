package bestia.messages.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import bestia.messages.JsonMessage;

/**
 * This message requests a translation from the server. The data is fetched via
 * our I18N interface and then delivered to the client with an translation
 * response message.
 * 
 * @author Thomas Felix
 *
 */
public class TranslationRequestMessage extends JsonMessage {

	@JsonIgnore
	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "translation.request";

	private final List<TranslationItem> items;

	/**
	 * The token is put in the answer of this message. Synce these requests are
	 * async the token can be used by the client to identify the answers of the
	 * request.
	 */
	private final String token;
	
	private TranslationRequestMessage() {
		super(0);
		
		this.token = null;
		this.items = null;
	}

	@JsonCreator
	public TranslationRequestMessage(long accId,
			@JsonProperty("t") String token,
			@JsonProperty("is") List<TranslationItem> items) {
		super(accId);

		this.token = Objects.requireNonNull(token);
		this.items = Collections.unmodifiableList(new ArrayList<>(items));
	}

	@JsonProperty("is")
	public List<TranslationItem> getItems() {
		return items;
	}

	@JsonProperty("t")
	public String getToken() {
		return token;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String toString() {
		return String.format("TranslationRequestMessage[items: %s]", items.toString());
	}

	@Override
	public TranslationRequestMessage createNewInstance(long accountId) {

		return new TranslationRequestMessage(accountId, token, items);
	}
}
