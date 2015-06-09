package net.bestia.model.domain;

import javax.persistence.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.MessageDigest;
import java.util.Arrays;

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
	private final static Logger log = LogManager.getLogger(Password.class);
	
	// TODO Bessere KEY Hashfunktion.
	@Transient
	private final static String HASH_FUNCTION = "SHA-256";

	// TODO Eigenes Salt für jedes Passwort.
	@Transient
	private static final String SALT = "ABCDEFGSJDHFT!12345";
	
	@Column(length = 64, name = "password")
	private byte[] passwordHash;

	public Password(String password) {
		passwordHash = hash(password);
	}
	
	/**
	 * Paramless Ctor for Hibernate.
	 */
	public Password() {
		passwordHash = new byte[64];
	}
	
	public void setHash(byte[] hash) {
		this.passwordHash = hash;	
	}

	/**
	 * Calculates the hash of a given password.
	 * 
	 * @param str
	 * @return
	 */
	private byte[] hash(String str) {
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance(HASH_FUNCTION);
			byte[] hash = md.digest((str + SALT).getBytes("UTF-8"));
			return hash;
		} catch (Exception e) {
			log.error("Hash function {} not supported.", HASH_FUNCTION);
			throw new IllegalStateException("Can not generate hash of password. Hash function not supported.", e);
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
	
	@Override
	public int hashCode() {
		return passwordHash.hashCode();
	};

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof Password)) {
			return false;
		}
		Password that = (Password) o;
		return Arrays.equals(this.passwordHash, that.passwordHash);
	}
}
