package net.bestia.login.account.loginmethod

import net.bestia.login.account.Account
import java.time.LocalDateTime

/**
 * Shared domain contract for the different, otherwise unrelated ways an [Account] can authenticate
 * (NFT signature, static development token, later e.g. email/password, ...).
 *
 * Each login method is its own standalone JPA entity with its own table and its own authentication
 * code path. This interface only provides the common accessors so callers that want to treat any
 * login method uniformly (auditing, `lastUsedAt` bookkeeping) can do so without coupling the
 * persistence models together.
 */
interface LoginMethod {
  val account: Account
  val createdAt: LocalDateTime
  var lastUsedAt: LocalDateTime?
}
