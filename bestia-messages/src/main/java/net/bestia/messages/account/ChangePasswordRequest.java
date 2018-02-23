package net.bestia.messages.account;

import java.io.Serializable;
import java.util.Objects;

/**
 * Password change request message which is send to the server. He will then
 * change the passwords of the user.
 * 
 * @author Thomas Felix
 *
 */
public final class ChangePasswordRequest implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String accountName;
	private final String oldPassword;
	private final String newPassword;

	public ChangePasswordRequest(String accountName, String oldPassword, String newPassword) {

		this.accountName = Objects.requireNonNull(accountName);
		this.oldPassword = Objects.requireNonNull(oldPassword);
		this.newPassword = Objects.requireNonNull(newPassword);
	}

	public String getAccountName() {
		return accountName;
	}

	public String getOldPassword() {
		return oldPassword;
	}

	public String getNewPassword() {
		return newPassword;
	}
}
