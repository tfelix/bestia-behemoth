package net.bestia.messages.web;

import java.io.Serializable;

/**
 * Small helper object to send results of the account check to the client via
 * JSON.
 * 
 * @author Thomas Felix
 *
 */
public final class AccountCheckJson implements Serializable {

	private static final long serialVersionUID = 1L;

	private final boolean emailUsed;
	private final boolean nameUsed;

	public AccountCheckJson(boolean emailUsed, boolean nameUsed) {

		this.emailUsed = emailUsed;
		this.nameUsed = nameUsed;
	}

	public boolean isEmailUsed() {
		return emailUsed;
	}

	public boolean isNameUsed() {
		return nameUsed;
	}
}
