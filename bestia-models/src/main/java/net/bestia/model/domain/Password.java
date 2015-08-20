package net.bestia.model.domain;

import java.io.Serializable;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Transient;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Embeddable data class for storing and checking passwords securely hashed
 * inside a database.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Embeddable
public class Password implements Serializable {

	private static final long serialVersionUID = 1L;

	@Transient
	private final static Logger log = LogManager.getLogger(Password.class);

	@Transient
	private final static String HASH_FUNCTION = "PBKDF2WithHmacSHA1";

	@Transient
	private final static int ITERATIONS = 512;

	@Transient
	private final static int KEY_LENGTH = 128;

	@Transient
	private static final int SALT_LENGTH = 32;

	/**
	 * Length is key + salt + delimiter char.
	 */
	@Column(name = "password", length = KEY_LENGTH + SALT_LENGTH + 1, nullable = false)
	private String passwordHash = "";
	
	/**
	 * Paramless Ctor for Hibernate.
	 */
	public Password() {

	}

	/**
	 * Creates a new password with a random new salt.
	 * 
	 * @param password
	 */
	public Password(String password) {

		try {
			final byte[] salt = getNewSalt();
			final byte[] data = hash(password, salt);
			setPasswordHash(data, salt);
		} catch (Exception e) {
			log.fatal("Encryption algorithm not supported on this VM! Passwords can not be hashed!");
		}

	}

	/**
	 * Creates a new password with a given salt.
	 * 
	 * @param password
	 * @param salt
	 */
	public Password(String password, byte[] salt) {
		try {
			final byte[] data = hash(password, salt);
			setPasswordHash(data, salt);
		} catch (Exception e) {
			log.fatal("Encryption algorithm not supported on this VM! Passwords can not be hashed!");
		}
	}

	
	private void setPasswordHash(byte[] data, byte[] salt) {
		final Base64.Encoder enc = Base64.getEncoder();
		
		passwordHash = enc.encodeToString(data) + "$" + enc.encodeToString(salt);
	}


	private byte[] getNewSalt() throws Exception {
		final byte[] salt = SecureRandom.getInstance("SHA1PRNG").generateSeed(
				SALT_LENGTH);
		return salt;
	}

	/**
	 * Returns the saved salt from the password.
	 * 
	 * @return
	 */
	public byte[] getSalt() throws IllegalStateException {
		final String[] splitted = passwordHash.split("\\$");
		if (splitted.length < 2) {
			throw new IllegalStateException(
					"Password does not contain salt seperator symbol. Invalid data.");
		}
		return stringToByte(splitted[1]);
	}

	/**
	 * Calculates the hash of a given password.
	 * 
	 * @param str
	 * @return
	 */
	private byte[] hash(String str, byte[] salt)
			throws NoSuchAlgorithmException {
		
		// KEY_LENGTH is in byte. Function awaits bits.
		final KeySpec spec = new PBEKeySpec(str.toCharArray(), salt,
				ITERATIONS, KEY_LENGTH * 8);
		final SecretKeyFactory f = SecretKeyFactory.getInstance(HASH_FUNCTION);
		try {
			final SecretKey key = f.generateSecret(spec);
			final byte[] hash = key.getEncoded();
			
			return hash;
		} catch (InvalidKeySpecException ex) {

			log.fatal("Invalid key spec! Can not create hashed passwords!", ex);
		}

		return new byte[KEY_LENGTH];
	}

	private byte[] stringToByte(String data) {
		final byte[] decoded = Base64.getDecoder().decode(data);
		return decoded;
	}

	/**
	 * Checks if a password matches a given string.
	 * 
	 * @param password
	 * @return
	 */
	public boolean matches(String password) {

		// Extract current salt.
		final byte[] salt = getSalt();

		return equals(new Password(password, salt));
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
		return this.passwordHash.equals(that.passwordHash);
	}
	
	@Override
	public String toString() {
		return String.format("Password[Hash: %s...]", passwordHash.subSequence(0, 12));
	}
}
