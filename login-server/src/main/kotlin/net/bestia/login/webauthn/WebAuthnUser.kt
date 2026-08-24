package net.bestia.login.webauthn

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * The WebAuthn identity of an account: one handle, shared by every credential registered to it.
 *
 * This is the join that makes multi-device work. An assertion carries the user handle, not anything
 * that identifies a machine, so a synced passkey created on one device and used from another lands
 * on the same account with nothing extra to reconcile.
 */
@Entity
@Table(name = "webauthn_user")
class WebAuthnUser(
  @Id
  @Column(name = "account_id")
  val accountId: Long,

  /**
   * 32 bytes of CSPRNG output. Deliberately not derived from the account id or the display name:
   * the handle is handed to every relying party the credential is used against, the spec caps it at
   * 64 bytes and forbids personal data in it, and a sequential value would leak account ordering.
   */
  @Column(nullable = false, unique = true, length = 64)
  val userHandle: ByteArray,

  @Column(nullable = false)
  val createdAt: LocalDateTime = LocalDateTime.now()
)
