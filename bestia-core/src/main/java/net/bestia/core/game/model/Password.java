package net.bestia.core.game.model;

import javax.persistence.*;

import java.security.MessageDigest;

/**
 * Embedable data class for storing and checking passwords securely hashed inside
 * a database.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Embeddable
public class Password {

	@Transient
	private static final String SALT = "ABCDEFGSJDHFT!12345";
	@Column(length = 64, name = "password")
	private String passwordHash;

	public Password(String password) {
		passwordHash = hash(password);
	}
	
	/**
	 * Paramless Ctor for Hibernate.
	 */
	public Password() {
		passwordHash = "";
	}
	
	public void setHash(String hash) {
		this.passwordHash = hash;		
	}

	/**
	 * Calculates the hash of a given password.
	 * 
	 * @param str
	 * @return
	 */
	private String hash(String str) {
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("SHA-256");
			byte[] hash = md.digest((str + SALT).getBytes("UTF-8"));
			return new String(hash, "UTF-8");
		} catch (Exception e) {
			throw new IllegalStateException("Can not generate hash of password. Hash function not supported.");
		}
	}

	/**
	 * Checks if a password matches a given string.
	 * 
	 * @param password
	 * @return
	 */
	public boolean matches(String password) {
		return equals(new Password(password));
	}

	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof Password)) {
			return false;
		}
		Password that = (Password) o;
		return that.passwordHash.equals(this.passwordHash);
	}
}
