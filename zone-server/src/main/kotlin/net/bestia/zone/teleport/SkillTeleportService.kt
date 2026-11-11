package net.bestia.zone.teleport

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.SavePointService
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillTeleports
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.EntityFx
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.movement.ecs.Path
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.movement.ecs.Teleporting
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.travel.Journey
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/** The Sage's Teleport and Warp Portal, on the same vanish-and-land path the wings take. See [TeleportSystem]. */
@Service
class SkillTeleportService(
  private val statusEffects: StatusEffectService,
  private val settings: TeleportItemSettings,
  private val savePoints: SavePointService,
  private val asyncJobs: AsyncJobExecutor,
  private val worldView: WorldView,
) : SkillTeleports {

  override fun teleport(world: World, casterId: EntityId, toHome: Boolean): OpError? {
    if (isSick(world, casterId) || world.has(casterId, Teleporting::class)) return OpError.TELEPORT_NOT_READY

    val destination = if (toHome) homeOf(world, casterId) else Teleporting.Destination.Nearby(NEARBY_METRES)
    send(world, casterId, destination)
    for (bestia in bestiasNear(world, casterId)) {
      send(world, bestia, Teleporting.Destination.Follow(casterId))
    }

    return null
  }

  override fun openPortal(world: World, casterId: EntityId, at: Vec3L, to: Vec3L?, passages: Int, seconds: Int): OpError? {
    if (to != null) {
      openBetween(world, at, to, passages, seconds)
      return null
    }

    // Home is a save point in the database, which the tick may not read: the portal opens once it is known.
    val masterId = world.get(casterId, Master::class)?.masterId ?: return OpError.PORTAL_NO_SPOT
    asyncJobs.submit(key = casterId) {
      val home = runCatching { savePoints.forMaster(masterId) }
        .onFailure { LOG.warn(it) { "No home for master $masterId; the portal does not open" } }
        .getOrNull() ?: return@submit
      worldView.post { openBetween(this, at, home, passages, seconds) }
    }

    return null
  }

  private fun openBetween(world: World, at: Vec3L, to: Vec3L, passages: Int, seconds: Int) {
    val shared = WarpPortalEnd.Passages(passages)
    val here = world.createEntity { id -> add(id, Position.fromVec3(at)); add(id, portalVisual()) }
    val there = world.createEntity { id -> add(id, Position.fromVec3(to)); add(id, portalVisual()) }
    world.add(here, WarpPortalEnd(there, besides(to), shared, seconds.toFloat()))
    world.add(there, WarpPortalEnd(here, besides(at), shared, seconds.toFloat()))
  }

  private fun send(world: World, id: EntityId, destination: Teleporting.Destination) {
    statusEffects.applyEffect(world, id, StatusEffectId.TELEPORT_SICKNESS.id, level = 1, sourceEntityId = id)
    world.remove(id, Path::class)
    world.remove(id, Journey::class)
    world.add(id, Teleporting(destination, secondsUntilMove = settings.butterflyWingVanishMs / 1000f))
    world.add(id, EntityFx(EntityFx.Kind.HOME_VANISH, settings.butterflyWingVanishMs))
  }

  /** The master's own bestias standing close by that are free to go. */
  private fun bestiasNear(world: World, casterId: EntityId): List<EntityId> {
    val masterId = world.get(casterId, Master::class)?.masterId ?: return emptyList()
    val at = world.get(casterId, Position::class)?.toVec3L() ?: return emptyList()

    return OwnedBestia.ownedBy(world, masterId).map { it.entityId }.filter { id ->
      val position = world.get(id, Position::class)?.toVec3L()
      position != null && position.distance(at) <= BESTIA_REACH_TILES && !isSick(world, id) && !world.has(id, Teleporting::class)
    }
  }

  /** The save point, looked up off the tick while the caster vanishes, as the butterfly wing does. */
  private fun homeOf(world: World, casterId: EntityId): Teleporting.Destination.Home {
    val home = Teleporting.Destination.Home()
    val masterId = world.get(casterId, Master::class)?.masterId
    asyncJobs.submit(key = casterId) {
      val savePoint = masterId?.let { runCatching { savePoints.forMaster(it) }.getOrNull() }
      worldView.post {
        home.savePoint = savePoint
        home.lookupFailed = savePoint == null
      }
    }

    return home
  }

  private fun isSick(world: World, id: EntityId): Boolean {
    return world.get(id, StatusEffects::class)?.hasEffect(StatusEffectId.TELEPORT_SICKNESS.id) == true
  }

  private fun portalVisual() = EntityVisual(VisualKind.STRUCTURE, StaticEntityKind.WARP_PORTAL.ordinal.toLong())

  /** A step beside the far end, so a body does not come out standing inside the portal it arrived through. */
  private fun besides(end: Vec3L) = Vec3L(end.x + EXIT_OFFSET_TILES, end.y, end.z)

  private companion object {
    private val LOG = KotlinLogging.logger { }

    const val NEARBY_METRES = 150L
    const val BESTIA_REACH_TILES = 10L
    const val EXIT_OFFSET_TILES = 2L
  }
}
