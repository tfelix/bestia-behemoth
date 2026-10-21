package net.bestia.zone.guild

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.util.AccountId
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Guild invitations waiting for an answer. In memory, like a party's: an invitation is a minute long, and one a
 * restart forgets is simply asked again.
 *
 * Expired ones are dropped when next touched, so no thread has to watch the clock.
 */
@Component
class GuildInvitations(
  private val config: GuildConfig,
) {

  inner class Open(
    val invitationId: Long,
    val guildId: Long,
    val guildName: String,
    val inviterAccountId: AccountId,
    val inviterName: String,
    val invitedAccountId: AccountId,
    val invitedMasterId: Long,
    val invitedMasterName: String,
    private val createdAtNanos: Long = System.nanoTime(),
  ) {
    fun isExpired(): Boolean {
      return System.nanoTime() - createdAtNanos > TimeUnit.SECONDS.toNanos(config.invitationTimeoutSeconds)
    }

    fun toMessage(): GuildInvitationSMSG = GuildInvitationSMSG(
      invitationId = invitationId,
      guildName = guildName,
      invitedByMaster = inviterName,
      expiresInSeconds = config.invitationTimeoutSeconds.toInt(),
    )
  }

  private val pending = ConcurrentHashMap<Long, Open>()
  private val nextInvitationId = AtomicLong(1L)

  /**
   * The open invitation of [guildId] to [invitedMasterId], or a new one when there is none and a seat is free:
   * an open invitation holds a seat, so no guild can invite more than it could take in.
   */
  fun open(
    guildId: Long,
    guildName: String,
    seatsFree: Int,
    inviterAccountId: AccountId,
    inviterName: String,
    invitedAccountId: AccountId,
    invitedMasterId: Long,
    invitedMasterName: String,
  ): Open {
    synchronized(pending) {
      pending.values.removeIf { it.isExpired() }

      pending.values.firstOrNull { it.guildId == guildId && it.invitedMasterId == invitedMasterId }?.let { return it }

      if (pending.values.count { it.guildId == guildId } >= seatsFree) {
        throw GuildFullException()
      }

      val invitation = Open(
        invitationId = nextInvitationId.getAndIncrement(),
        guildId = guildId,
        guildName = guildName,
        inviterAccountId = inviterAccountId,
        inviterName = inviterName,
        invitedAccountId = invitedAccountId,
        invitedMasterId = invitedMasterId,
        invitedMasterName = invitedMasterName,
      )
      pending[invitation.invitationId] = invitation

      return invitation
    }
  }

  /** Checks before removing: the ids are guessable, so anyone else could otherwise burn the invitation. */
  fun take(accountId: AccountId, invitationId: Long): Open {
    val invitation = pending[invitationId]?.takeUnless { it.isExpired() }
      ?: throw GuildInvitationExpiredException()

    if (invitation.invitedAccountId != accountId) {
      LOG.warn { "Account $accountId tried to answer guild invitation $invitationId, which is not addressed to it" }
      throw GuildRequestRefused("guild invitation $invitationId is not addressed to account $accountId")
    }

    // Two answers racing each other: only one of them takes it.
    if (!pending.remove(invitationId, invitation)) {
      throw GuildInvitationExpiredException()
    }

    return invitation
  }

  /** Forgets every invitation into [guildId], when it is disbanded. */
  fun dropFor(guildId: Long) {
    pending.values.removeIf { it.guildId == guildId }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
