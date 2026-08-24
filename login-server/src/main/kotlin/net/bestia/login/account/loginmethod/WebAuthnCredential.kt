package net.bestia.login.account.loginmethod

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import net.bestia.login.account.Account
import java.time.LocalDateTime

/**
 * One WebAuthn credential - a passkey, or a security key - belonging to an account. An account may
 * have any number of them, and nothing here identifies a device: a credential that syncs through
 * iCloud Keychain or Google Password Manager is a single row that several machines can present.
 */
@Entity
@Table(
  name = "webauthn_credential",
  indexes = [
    Index(name = "idx_webauthn_credential_account", columnList = "account_id")
  ]
)
class WebAuthnCredential(
  @ManyToOne
  @JoinColumn(name = "account_id", nullable = false)
  override val account: Account,

  /**
   * Unique across the whole table rather than per account: a credential already claimed by one
   * account must never become registrable against a second one.
   */
  @Column(nullable = false, unique = true, length = 255)
  val credentialId: ByteArray,

  /** COSE_Key encoding, so the signing algorithm travels with the key. */
  @Column(nullable = false, length = 1024)
  val publicKeyCose: ByteArray,

  /**
   * Whether the authenticator is allowed to back this credential up. Fixed for the credential's
   * whole life, only reported at registration, and the only dependable way to tell a synced passkey
   * from a device-bound one afterwards.
   */
  @Column(nullable = false)
  val backupEligible: Boolean,

  /** Whether user verification had already been performed when the credential was created. */
  @Column(nullable = false)
  val uvInitialized: Boolean,

  /** Authenticator model identifier. Absent from assertions, so it cannot be backfilled later. */
  @Column(nullable = true, length = 16)
  val aaguid: ByteArray? = null,

  /** Comma separated transport hints (`usb`, `nfc`, `ble`, `internal`, `hybrid`). */
  @Column(nullable = true, length = 64)
  val transports: String? = null,

  /** Null when the authenticator declined to say - the credProps extension is optional. */
  @Column(nullable = true)
  val discoverable: Boolean? = null,

  @Column(nullable = false)
  override val createdAt: LocalDateTime = LocalDateTime.now()
) : LoginMethod {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  /**
   * Stays at zero for the rest of its life on every synced passkey, because a counter cannot be
   * kept consistent across copies. Only meaningful for device-bound authenticators.
   */
  @Column(nullable = false)
  var signatureCount: Long = 0

  /** Whether the credential is backed up right now. Unlike [backupEligible] this moves over time. */
  @Column(nullable = false)
  var backupState: Boolean = false

  /** What the player named this credential, e.g. "YubiKey" or "Work laptop". */
  @Column(nullable = true, length = 64)
  var label: String? = null

  @Column
  override var lastUsedAt: LocalDateTime? = null
}
