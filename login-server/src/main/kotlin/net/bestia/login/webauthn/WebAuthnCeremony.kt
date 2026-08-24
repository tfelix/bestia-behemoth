package net.bestia.login.webauthn

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Lob
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.hibernate.Length
import java.time.LocalDateTime

/**
 * A started but unfinished WebAuthn ceremony, holding the challenge the library generated.
 *
 * The challenge lives here and is never round-tripped through the browser. Handing it out and
 * taking it back on the finish call would let the caller choose its own, which removes the only
 * thing that makes the signature meaningful.
 */
@Entity
@Table(
  name = "webauthn_ceremony",
  indexes = [
    Index(name = "idx_webauthn_ceremony_expires", columnList = "expires_at")
  ]
)
class WebAuthnCeremony(
  @Id
  @Column(length = 64)
  val id: String,

  // VARCHAR, not the native ENUM the MariaDB dialect would otherwise pick: an ENUM column
  // makes adding a value a schema migration and makes the declared order load-bearing.
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  val ceremonyType: CeremonyType,

  /**
   * `PublicKeyCredentialCreationOptions.toJson()` or `AssertionRequest.toJson()`.
   *
   * The explicit length matters: without it the dialect picks a 255 byte `tinytext`, which is less
   * than one set of excluded credentials.
   */
  @Lob
  @Column(nullable = false, length = Length.LONG32)
  val requestJson: String,

  @Column(nullable = true, length = 64)
  val loginSessionIdHash: String? = null,

  /**
   * Set only while a brand new account is being registered. No account row exists yet at that
   * point - creating one before the credential verifies would turn the options endpoint into an
   * unauthenticated way to fill the account table - so the chosen name and the handle it is going
   * to get are parked here until the ceremony finishes.
   */
  @Column(nullable = true, length = 32)
  val pendingName: String? = null,

  @Column(nullable = true, length = 64)
  val pendingHandle: ByteArray? = null,

  /** Set only when an existing account is adding another credential. */
  @Column(nullable = true)
  val accountId: Long? = null,

  /**
   * Recovery replaces the whole code set once the new passkey verifies, so the intent has to
   * survive from the start of the ceremony through to its end.
   */
  @Column(nullable = false)
  val reissueRecoveryCodes: Boolean = false,

  @Column(nullable = false)
  val createdAt: LocalDateTime = LocalDateTime.now(),

  @Column(nullable = false)
  val expiresAt: LocalDateTime
)
