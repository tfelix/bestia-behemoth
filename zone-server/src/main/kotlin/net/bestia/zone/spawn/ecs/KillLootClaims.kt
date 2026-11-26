package net.bestia.zone.spawn.ecs

import net.bestia.zone.battle.ecs.damage.TakenDamage
import net.bestia.zone.config.PartyRulesConfig
import net.bestia.zone.config.WorldRulesConfig
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.MasterEntityResolver
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.PartyLootShare
import net.bestia.zone.identity.ecs.PartyMembership
import net.bestia.zone.item.ecs.LootClaim
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.AccountId
import org.springframework.stereotype.Component

/**
 * Who may pick up a kill's drops first: the side that dealt the most damage, where a party is one side. So
 * members who fought together outweigh a stranger who hit harder than each of them alone. A party that takes
 * turns holds each drop for one member near the kill first.
 *
 * Tick thread: reads the world and the session map, nothing else.
 */
@Component
class KillLootClaims(
  private val sessions: ConnectionInfoService,
  private val worldRules: WorldRulesConfig,
  private val partyRules: PartyRulesConfig,
  private val masters: MasterEntityResolver = MasterEntityResolver(sessions),
  private val nearby: PartyMembersNearby = PartyMembersNearby(sessions, partyRules, masters),
  private val turns: LootTurns = LootTurns(),
) {

  /** [leadAccount] dealt the most damage on the side that won the loot. */
  class KillLoot(
    val leadAccount: AccountId,
    private val claimsPerDrop: () -> List<LootClaim>,
  ) {
    fun claimsForNextDrop(): List<LootClaim> {
      return claimsPerDrop()
    }
  }

  /** Accounts that fought on one side, and the party they fought for, if any. */
  private data class Side(val party: PartyMembership?, val accounts: Set<AccountId>)

  val reads: ComponentClassSet = setOf(Account::class, PartyMembership::class) + nearby.reads

  /** Null when no player hurt the kill. */
  fun of(world: World, damage: TakenDamage, killedAt: Vec3L): KillLoot? {
    val damageByAccount = damage.damageByAccount(world)
    if (damageByAccount.isEmpty()) {
      return null
    }

    val (winner, fighters) = damageByAccount.keys
      .groupBy { sideOf(world, it) }
      .maxBy { (_, fighters) -> fighters.sumOf { damageByAccount.getValue(it) } }
    val lead = fighters.maxBy { damageByAccount.getValue(it) }
    val sideClaim = LootClaim(winner.accounts, worldRules.lootProtectionSeconds)

    val party = winner.party
    if (party?.lootShare != PartyLootShare.TAKE_TURNS) {
      return KillLoot(lead) { listOf(sideClaim) }
    }

    val takers = party.members
      .filter { nearby.masterNear(world, it, killedAt) != null }
      .mapTo(hashSetOf(lead)) { it.accountId }

    return KillLoot(lead) {
      val turn = LootClaim(setOf(turns.next(party.partyId, takers)), partyRules.takeTurnsClaimSeconds)
      listOf(turn, sideClaim)
    }
  }

  private fun sideOf(world: World, accountId: AccountId): Side {
    val party = masters.selectedMasterEntityOf(accountId)?.let { world.get(it, PartyMembership::class) }
      ?: return Side(party = null, accounts = setOf(accountId))

    // A member playing another of its masters has left the party for now.
    val playedMembers = party.members.filter { sessions.isPlaying(it.accountId, it.masterId) }

    return Side(party, playedMembers.mapTo(HashSet()) { it.accountId } + accountId)
  }
}
