package net.bestia.messages.internal;

import net.bestia.messages.AccountMessage;

/**
 * Message coming from the client sometimes are redundant and thus need to me
 * marked to they can get routed the right way.
 */
public class FromClient extends AccountMessage {

	private static final long serialVersionUID = 1L;
	private final AccountMessage payload;

	public FromClient(AccountMessage payload) {
		super(payload.getAccountId());

		this.payload = payload;
	}

	public Object getPayload() {
		return payload;
	}

	@Override
	public AccountMessage createNewInstance(long accountId) {
		return new FromClient(payload.createNewInstance(accountId));
	}

}
