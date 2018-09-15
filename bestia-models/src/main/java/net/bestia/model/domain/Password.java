package net.bestia.model.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.Marker
import org.slf4j.MarkerFactory

import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Transient
import java.io.Serializable
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import java.security.spec.KeySpec
import java.util.Base64

/**
 * Embeddable data class for storing and checking passwords securely hashed
 * inside a database.
 *
 * @author Thomas Felix
 */
@Embeddable
class Password : Serializable {

  /**
   * Length is key + salt + delimiter char.
   */
  @Column(name = "password", length = 255, nullable = false)
  private var passwordHash = ""

  private val newSalt: ByteArray
    @Throws(Exception::class)
    get() {
      val salt = SecureRandom.getInstance("SHA1PRNG").generateSeed(
              SALT_LENGTH)
      return salt
    }

  /**
   * Returns the saved salt from the password.
   *
   * @return
   */
  val salt: ByteArray
    @JsonIgnore
    @Throws(IllegalStateException::class)
    get() {
      val splitted = passwordHash.split("\\$".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
      if (splitted.size < 2) {
        throw IllegalStateException(
                "Password does not contain salt seperator symbol. Invalid data.")
      }
      return stringToByte(splitted[1])
    }

  /**
   * Paramless Ctor for Hibernate.
   */
  constructor() {

  }

  /**
   * Creates a new password with a random new salt.
   *
   * @param password
   */
  constructor(password: String) {

    try {
      val salt = newSalt
      val data = hash(password, salt)
      setPasswordHash(data, salt)
    } catch (e: Exception) {
      log.error(marker, "Encryption algorithm not supported on this VM! Passwords can not be hashed!")
    }

  }

  /**
   * Creates a new password with a given salt.
   *
   * @param password
   * @param salt
   */
  constructor(password: String, salt: ByteArray) {
    try {
      val data = hash(password, salt)
      setPasswordHash(data, salt)
    } catch (e: Exception) {
      log.error(marker, "Encryption algorithm not supported on this VM! Passwords can not be hashed!")
    }

  }

  private fun setPasswordHash(data: ByteArray, salt: ByteArray) {
    val enc = Base64.getEncoder()

    val dataStr = enc.encodeToString(data)
    val saltStr = enc.encodeToString(salt)

    passwordHash = "$dataStr$$saltStr"
  }

  /**
   * Calculates the hash of a given password.
   *
   * @param str
   * @return
   */
  @Throws(NoSuchAlgorithmException::class)
  private fun hash(str: String, salt: ByteArray): ByteArray {

    // KEY_LENGTH is in byte. Function awaits bits.
    val spec = PBEKeySpec(str.toCharArray(), salt,
            ITERATIONS, KEY_LENGTH * 8)
    val f = SecretKeyFactory.getInstance(HASH_FUNCTION)
    try {
      val key = f.generateSecret(spec)
      val hash = key.encoded

      return hash
    } catch (ex: InvalidKeySpecException) {

      log.error(marker, "Invalid key spec! Can not create hashed passwords!", ex)
    }

    return ByteArray(KEY_LENGTH)
  }

  private fun stringToByte(data: String): ByteArray {
    val decoded = Base64.getDecoder().decode(data)
    return decoded
  }

  /**
   * Checks if a password matches a given string.
   *
   * @param password
   * @return
   */
  fun matches(password: String): Boolean {

    // Extract current salt.
    val salt = salt

    return equals(Password(password, salt))
  }

  override fun hashCode(): Int {
    return passwordHash.hashCode()
  }

  override fun equals(o: Any?): Boolean {
    if (o === this) {
      return true
    }
    if (o !is Password) {
      return false
    }
    val that = o as Password?
    return this.passwordHash == that!!.passwordHash
  }

  override fun toString(): String {
    return String.format("Password[Hash: %s...]", passwordHash.subSequence(0, 12))
  }

  companion object {

    private const val serialVersionUID = 1L

    @Transient
    private val log = LoggerFactory.getLogger(Password::class.java)
    private val marker = MarkerFactory.getMarker("FATAL")

    @Transient
    private val HASH_FUNCTION = "PBKDF2WithHmacSHA1"

    @Transient
    private val ITERATIONS = 512

    @Transient
    private val KEY_LENGTH = 128

    @Transient
    private val SALT_LENGTH = 32
  }
}
