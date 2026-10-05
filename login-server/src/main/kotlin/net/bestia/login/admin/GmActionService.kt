package net.bestia.login.admin

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Authority
import net.bestia.account.KickReason
import net.bestia.login.account.Account
import net.bestia.login.account.AccountLoginGuard
import net.bestia.login.account.AccountRepository
import net.bestia.login.account.AccountSessionTerminator
import net.bestia.login.account.AccountStatus
import net.bestia.login.admin.GmActionRefusedException.Refusal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

/**
 * What a GM may do to another account. The actor is read from the database on every call, so a GM who was
 * demoted or banned a minute ago can do nothing more, even with a token that is still valid.
 */
@Service
class GmActionService(
  private val accounts: AccountRepository,
  private val accountLoginGuard: AccountLoginGuard,
  private val accountSessionTerminator: AccountSessionTerminator,
  private val gmActions: GmActionRepository
) {

  /** Ends every session of the target, so it has to sign in with its passkey again. A ban keeps it out. */
  @Transactional
  fun kick(actorId: Long, targetId: Long, note: String) {
    val (actor, target) = authorize(actorId, targetId, Authority.KICK)

    gmActions.save(GmAction(actor.id, target.id, GmActionType.KICK, note))
    accountSessionTerminator.terminate(target.id, KickReason.GM_KICK)

    LOG.info { "GM ${actor.id} kicked account ${target.id}: $note" }
  }

  /** A [duration] of null bans for good. */
  @Transactional
  fun ban(actorId: Long, targetId: Long, note: String, duration: Duration?) {
    val (actor, target) = authorize(actorId, targetId, Authority.BAN)
    val until = duration?.let { LocalDateTime.now().plus(it) }

    // The status first: the sessions ended below must not be replaced by a login the ban has not reached yet.
    target.status = if (until == null) AccountStatus.PERMA_BANNED else AccountStatus.BANNED_UNTIL
    target.bannedUntil = until
    accounts.save(target)

    gmActions.save(GmAction(actor.id, target.id, GmActionType.BAN, note, until))
    accountSessionTerminator.terminate(target.id, KickReason.BANNED)

    LOG.warn { "GM ${actor.id} banned account ${target.id} until ${until ?: "forever"}: $note" }
  }

  /** The actor is checked before the target is looked up, so nobody without the authority can probe for ids. */
  private fun authorize(actorId: Long, targetId: Long, authority: Authority): AuthorizedParties {
    val actor = accounts.findById(actorId).orElse(null)
      ?: throw GmActionRefusedException(Refusal.NOT_ALLOWED, "acting account $actorId does not exist")

    accountLoginGuard.denialReason(actor)?.let { reason ->
      throw GmActionRefusedException(Refusal.NOT_ALLOWED, reason)
    }

    if (authority !in actor.role.authorities) {
      throw GmActionRefusedException(Refusal.NOT_ALLOWED, "account $actorId (${actor.role}) lacks $authority")
    }

    val target = accounts.findById(targetId).orElse(null)
      ?: throw GmActionRefusedException(Refusal.NO_SUCH_ACCOUNT, "account $targetId does not exist")

    if (!actor.role.outranks(target.role)) {
      throw GmActionRefusedException(
        Refusal.NOT_ALLOWED,
        "account $actorId (${actor.role}) does not outrank account $targetId (${target.role})"
      )
    }

    return AuthorizedParties(actor, target)
  }

  private data class AuthorizedParties(
    val actor: Account,
    val target: Account
  )

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
