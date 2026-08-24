package net.bestia.login.gamelogin

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/**
 * One attempt to log a game client in through the browser.
 *
 * The session is what ties the three parties together: the game started it and holds the PKCE
 * verifier, the browser advances it by authenticating, and the authorization code that comes out
 * is only redeemable by whoever can produce the verifier. Neither the browser nor any other local
 * process ever sees enough on its own.
 */
@Entity
@Table(
  name = "login_session",
  indexes = [
    Index(name = "idx_login_session_expires", columnList = "expires_at")
  ]
)
class LoginSession(
  /** Base64url SHA-256 of the identifier the browser carries in its URL. */
  @Id
  @Column(name = "id_hash", length = 64)
  val idHash: String,

  /** Always a literal loopback address; see [RedirectUriValidator] for why that is enforced. */
  @Column(nullable = false, length = 255)
  val redirectUri: String,

  @Column(nullable = false, length = 128)
  val codeChallenge: String,

  @Column(nullable = false, length = 8)
  val challengeMethod: String,

  /** Echoed back on the redirect so the game can tell its own request from an injected one. */
  @Column(nullable = false, length = 128)
  val clientState: String,

  @Column(nullable = false)
  val createdAt: LocalDateTime = LocalDateTime.now(),

  @Column(nullable = false)
  val expiresAt: LocalDateTime
) {

  // VARCHAR, not the native ENUM the MariaDB dialect would otherwise pick: an ENUM column
  // makes adding a value a schema migration and makes the declared order load-bearing.
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  var status: LoginSessionStatus = LoginSessionStatus.PENDING

  @Column(nullable = true)
  var accountId: Long? = null

  fun isUsable(now: LocalDateTime): Boolean {
    return status == LoginSessionStatus.PENDING && expiresAt.isAfter(now)
  }
}
