package net.bestia.messages.web;

import java.io.Serializable;

/**
 * POJO used to request if a user name is available.
 * 
 * @author Thomas Felix
 *
 */
public class UserNameCheck implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private String username;
	private String email;

	private boolean usernameAvailable;
	private boolean emailAvialable;

	public UserNameCheck() {

	}

	public UserNameCheck(String username, String email) {
		
		this.username = username;
		this.email = email;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public boolean isUsernameAvailable() {
		return usernameAvailable;
	}

	public void setUsernameAvailable(boolean usernameAvailable) {
		this.usernameAvailable = usernameAvailable;
	}

	public boolean isEmailAvailable() {
		return emailAvialable;
	}

	public void setEmailAvailable(boolean emailAvialable) {
		this.emailAvialable = emailAvialable;
	}

	@Override
	public String toString() {
		return String.format("UserNameCheck[%s, %s]", getUsername(), getEmail());
	}
}
