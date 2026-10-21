package net.bestia.zone.companion.query

import net.bestia.bus.contract.ZoneAnswer
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * A master as the companion app sees it. A master being played is read where it stands; any other from its row,
 * which is as old as its last save.
 */
@Service
class MasterQueries(
  private val masters: MasterRepository,
  private val owned: OwnedMasters,
  private val connections: ConnectionInfoService,
  private val world: WorldView,
  private val plane: MapPlane,
) {

  @Transactional(readOnly = true)
  fun list(accountId: Long): ZoneAnswer.Masters {
    val playing = connections.activeMasterIdOf(accountId)

    return ZoneAnswer.Masters(
      masters.findAllByAccountIdOrderBySlot(accountId).map {
        ZoneAnswer.Masters.Master(it.id, it.slot, it.name, it.level, it.homeSettlementName, inWorld = it.id == playing)
      }
    )
  }

  @Transactional(readOnly = true)
  fun sheet(accountId: Long, masterId: Long): ZoneAnswer.Sheet? {
    val master = owned.find(accountId, masterId) ?: return null
    val live = if (connections.activeMasterIdOf(accountId) == masterId) livePosition(master.entityId) else null
    val position = live ?: master.currentPosition

    return ZoneAnswer.Sheet(
      id = master.id,
      name = master.name,
      level = master.level,
      exp = master.exp,
      skillPoints = master.skillPoints,
      statusPoints = master.statusPoints,
      strength = master.strength,
      vitality = master.vitality,
      intelligence = master.intelligence,
      dexterity = master.dexterity,
      willpower = master.willpower,
      agility = master.agility,
      currentHealth = master.currentHealth,
      home = master.homeSettlementName,
      eastMetres = plane.eastMetres(position),
      northMetres = plane.northMetres(position),
      inWorld = live != null,
      asOfEpochMs = if (live != null) System.currentTimeMillis() else master.persistedAtEpochMs,
    )
  }

  private fun livePosition(entityId: Long): Vec3L? {
    return world.modify(entityId) { id -> get(id, Position::class)?.toVec3L() }
  }
}
