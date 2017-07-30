package net.bestia.model.web;

/**
 * POJO used to request if a username is available.
 * 
 * @author Thomas Felix
 *
 */
public class UserNameCheck {

	private String username;
	private String email;

	private boolean usernameAvailable;
	private boolean emailAvialable;
	
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
	
	public boolean isEmailAvialable() {
		return emailAvialable;
	}
	
	public void setEmailAvialable(boolean emailAvialable) {
		this.emailAvialable = emailAvialable;
	}
	
	@Override
	public String toString() {
		return String.format("UserNameCheck[%s, %s]", getUsername(), getEmail());
	}
}
