package net.bestia.zone.party

import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.identity.ecs.PartyMembership
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.place.ecs.Place
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.session.NoActiveSessionException
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * The one way a party roster reaches its members.
 *
 * Every member being played gets the whole roster whenever anything in it changes, so no client ever has to
 * patch one together. A roster is built off the tick on a job keyed by the party: requests that arrive while
 * one is queued fold into it, and a disband or removal queued behind it can never be overtaken by it.
 *
 * Safe to call from any thread. [sendTo] is the exception: it answers inline, so it belongs on the IO lane.
 */
@Component
class PartyRosterPublisher(
  private val reader: PartyRosterReader,
  private val sessions: ConnectionInfoService,
  private val world: WorldView,
  private val outMessageProcessor: OutMessageProcessor,
  private val jobs: AsyncJobExecutor,
) {

  /** Parties with a roster job queued and not yet started. */
  private val queued: MutableSet<Long> = ConcurrentHashMap.newKeySet()

  /** Sends [partyId]'s roster to every member being played. */
  fun request(partyId: Long) {
    if (!queued.add(partyId)) {
      return
    }

    jobs.submit(keyOf(partyId)) {
      // Cleared as the job starts, not when it ends: a change made while the roster is built needs a new one.
      queued.remove(partyId)
      publish(partyId)
    }
  }

  /** [request] for whatever party [masterId] is in, if any. */
  fun requestForMaster(masterId: Long) {
    jobs.submit(MASTER_KEY_PREFIX + masterId) {
      reader.partyIdOfMaster(masterId)?.let { request(it) }
    }
  }

  /** Answers [accountId] at once with its party's roster, or [PartyInfoSMSG.NONE]. IO lane. */
  fun sendTo(accountId: AccountId) {
    val masterId = sessions.activeMasterIdOf(accountId) ?: return
    val roster = reader.partyIdOfMaster(masterId)?.let { build(it) }

    outMessageProcessor.sendToPlayer(accountId, roster ?: PartyInfoSMSG.NONE)
  }

  /** [member] is out of [partyId], which goes on: tell it so, and give the rest the roster without it. */
  fun removed(partyId: Long, member: PartyMembership.Member) {
    jobs.submit(keyOf(partyId)) {
      if (sessions.isPlaying(member.accountId, member.masterId)) {
        outMessageProcessor.sendToPlayer(member.accountId, DisbandPartySMSG(partyId))
      }
      publish(partyId)
    }
  }

  /** [partyId] ended: tell each of its former [members] being played. */
  fun disbanded(partyId: Long, members: Set<PartyMembership.Member>) {
    jobs.submit(keyOf(partyId)) {
      val message = DisbandPartySMSG(partyId)
      members
        .filter { sessions.isPlaying(it.accountId, it.masterId) }
        .forEach { outMessageProcessor.sendToPlayer(it.accountId, message) }
    }
  }

  private fun publish(partyId: Long) {
    val roster = build(partyId) ?: return

    roster.member
      .filter { it.onlineData != null }
      .forEach { outMessageProcessor.sendToPlayer(it.accountId, roster) }
  }

  /** The roster as it stands, or null when the party is gone. */
  private fun build(partyId: Long): PartyInfoSMSG? {
    val rows = reader.read(partyId) ?: return null

    // Which entity each played member stands in, taken before the world lease: the session is not the world's.
    val played = rows.members.mapNotNull { row -> playedEntities(row)?.let { row.masterId to it } }.toMap()

    val members = world.read {
      rows.members.map { row ->
        val entities = played[row.masterId]
        val onlineData = entities?.let { onlineDataOf(this, it, row) }

        PartyInfoSMSG.PartyMember(
          accountId = row.accountId,
          masterId = row.masterId,
          masterName = row.name,
          level = entities?.let { get(it.master, Level::class)?.level } ?: row.level,
          onlineData = onlineData,
        )
      }
    }

    return PartyInfoSMSG(
      partyId = rows.partyId,
      partyName = rows.name,
      member = members,
      expShare = rows.expShare,
      ownerMasterId = rows.ownerMasterId,
    )
  }

  private data class PlayedEntities(val master: EntityId, val active: EntityId)

  private fun playedEntities(row: PartyRosterReader.MemberRow): PlayedEntities? {
    if (!sessions.isPlaying(row.accountId, row.masterId)) {
      return null
    }

    return try {
      PlayedEntities(
        master = sessions.getSelectedMasterEntityId(row.accountId),
        active = sessions.getActiveEntityId(row.accountId),
      )
    } catch (_: NoActiveSessionException) {
      null
    }
  }

  /** Null while the master's entity is not fully in the world yet; the member reads as offline until it is. */
  private fun onlineDataOf(
    world: World,
    entities: PlayedEntities,
    row: PartyRosterReader.MemberRow,
  ): PartyInfoSMSG.PartyMember.OnlineData? {
    val position = world.get(entities.master, Position::class) ?: return null
    val vitals = vitalsOf(world, entities.master) ?: return null

    return PartyInfoSMSG.PartyMember.OnlineData(
      entityId = entities.master,
      // Read off the member's own component rather than resolved here: PlaceSystem already did the work this
      // tick. Blank only for a member whose first resolve has not run yet.
      areaName = world.get(entities.master, Place::class)?.place?.name ?: "",
      position = position.toVec3L(),
      vitals = vitals,
      activeBestia = if (entities.active == entities.master) null else activeBestiaOf(world, entities.active, row),
    )
  }

  private fun activeBestiaOf(
    world: World,
    entityId: EntityId,
    row: PartyRosterReader.MemberRow,
  ): PartyInfoSMSG.ActiveBestia? {
    val ownedBestia = world.get(entityId, OwnedBestia::class) ?: return null
    val vitals = vitalsOf(world, entityId) ?: return null

    return PartyInfoSMSG.ActiveBestia(
      entityId = entityId,
      speciesId = world.get(entityId, EntityVisual::class)?.id ?: 0,
      name = row.bestiaNames[ownedBestia.playerBestiaId] ?: "",
      level = world.get(entityId, Level::class)?.level ?: 1,
      vitals = vitals,
    )
  }

  private fun vitalsOf(world: World, entityId: EntityId): PartyInfoSMSG.Vitals? {
    val health = world.get(entityId, Health::class) ?: return null
    val mana = world.get(entityId, Mana::class)

    return PartyInfoSMSG.Vitals(
      hpCurrent = health.current,
      hpMax = health.max,
      manaCurrent = mana?.current ?: 0,
      manaMax = mana?.max ?: 0,
    )
  }

  private fun keyOf(partyId: Long): String = PARTY_KEY_PREFIX + partyId

  companion object {
    private const val PARTY_KEY_PREFIX = "party:"
    private const val MASTER_KEY_PREFIX = "party-of-master:"
  }
}
