package net.bestia.zone.teleport

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.capture.BestiaTrapSystem
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.EntityFx
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.ActivePlayer
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.GroundHeight
import net.bestia.zone.movement.ecs.Grounded
import net.bestia.zone.movement.ecs.Path
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.movement.ecs.Teleporting
import net.bestia.zone.prop.ecs.construction.ConstructionSystem
import net.bestia.zone.respawn.ecs.RespawnSystem
import net.bestia.zone.travel.Journey
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.SafeGround
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.stream.ChunkSubscriptionService
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.random.Random
import org.springframework.stereotype.Component as SpringComponent

/**
 * Puts a [Teleporting] body down once its vanish has played, then lets it appear.
 *
 * The landing spot is picked here rather than in the item script, because the ground height and the client's
 * chunk subscriptions belong to the tick thread. In the actions phase, before `ChunkStreamSystem`, so the
 * manifest that follows already describes where the body landed.
 */
@SpringComponent
class TeleportSystem(
  private val groundHeight: GroundHeight,
  private val chunkService: ChunkService,
  private val subscriptions: ChunkSubscriptionService,
  private val settings: TeleportItemSettings,
  private val out: OutMessageProcessor,
  private val safeGround: SafeGround = SafeGround.NONE,
  private val random: Random = Random.Default,
) : System {
  override val phase = Phase.ACTIONS

  // A respawn, a construction and a trap all settle where a body stands this tick; a landing goes after them.
  override val after = setOf(RespawnSystem::class, ConstructionSystem::class, BestiaTrapSystem::class)

  override val reads: ComponentClassSet = setOf(Account::class, ActivePlayer::class)
  override val writes: ComponentClassSet = setOf(
    Teleporting::class, Position::class, Path::class, Grounded::class, Journey::class, EntityFx::class,
  )

  override fun update(world: World, deltaTime: Float) {
    world.query(Teleporting::class, Position::class).each { id ->
      val teleporting = get<Teleporting>()
      teleporting.secondsUntilMove -= deltaTime
      if (teleporting.secondsUntilMove > 0f) return@each

      when (val destination = teleporting.destination) {
        is Teleporting.Destination.Nearby -> hop(world, id, get<Position>(), destination)
        is Teleporting.Destination.Home -> goHome(world, id, get<Position>(), destination)
      }
    }
  }

  private fun hop(world: World, id: EntityId, position: Position, nearby: Teleporting.Destination.Nearby) {
    val landing = findLanding(world, id, position.toVec3L(), nearby.radiusMetres)
    if (landing == null) {
      LOG.debug { "Entity $id found no dry landing within ${nearby.radiusMetres} m; the wing fizzles" }
      refuse(world, id, OpError.TELEPORT_NO_LANDING)
    } else {
      place(world, id, position, landing)
    }
    appear(world, id, EntityFx.Kind.WING_APPEAR, settings.flyWingAppearMs)
  }

  private fun goHome(world: World, id: EntityId, position: Position, home: Teleporting.Destination.Home) {
    if (home.lookupFailed) {
      refuse(world, id, OpError.TELEPORT_NO_LANDING)
      appear(world, id, EntityFx.Kind.HOME_APPEAR, settings.butterflyWingAppearMs)
      return
    }
    // The lookup is still out; the body waits, vanished, until it answers.
    val savePoint = home.savePoint ?: return

    val target = safeGround.dryOr(savePoint)
    val z = groundHeight.standingZAt(target)
    if (z == null) {
      // Not generated or off the grid: keep the stored height and let the grounding sweep try again, as a
      // respawn does.
      place(world, id, position, target)
      world.remove(id, Grounded::class)
    } else {
      place(world, id, position, Vec3L(target.x, target.y, z))
    }
    appear(world, id, EntityFx.Kind.HOME_APPEAR, settings.butterflyWingAppearMs)
  }

  /**
   * A dry spot between the shortest hop and [radius] away. For a body a player is looking through, only
   * ground that player was already sent counts, so the hop lands on something drawn and needs no loading screen.
   */
  private fun findLanding(world: World, id: EntityId, from: Vec3L, radius: Long): Vec3L? {
    val sent = viewerOf(world, id)?.let { subscriptions.sentTo(it) }

    repeat(LANDING_TRIES) {
      val angle = random.nextDouble(0.0, 2 * Math.PI)
      val distance = random.nextDouble(settings.flyWingMinMetres.toDouble(), radius.toDouble())
      val x = from.x + (cos(angle) * distance).roundToLong()
      val y = from.y + (sin(angle) * distance).roundToLong()

      // Searched from the body's own height, so in a cave it lands on a floor near the one it left.
      val z = groundHeight.standingZAt(Vec3L(x, y, from.z)) ?: return@repeat
      if (!safeGround.isDry(x, y)) return@repeat

      val landing = Vec3L(x, y, z)
      if (sent != null && chunkService.normalise(ChunkCoords.chunkOf(chunkService.config, landing)) !in sent) {
        return@repeat
      }
      return landing
    }
    return null
  }

  private fun viewerOf(world: World, id: EntityId): Long? {
    if (!world.has(id, ActivePlayer::class)) return null
    return world.get(id, Account::class)?.accountId
  }

  private fun place(world: World, id: EntityId, position: Position, landing: Vec3L) {
    position.x = landing.x
    position.y = landing.y
    position.z = landing.z
    position.stepProgress = 0f

    // Whatever it was walking towards is not ahead of it any more.
    world.remove(id, Path::class)
    world.remove(id, Journey::class)

    LOG.debug { "Teleported entity $id to $landing" }
  }

  private fun appear(world: World, id: EntityId, kind: EntityFx.Kind, durationMs: Int) {
    world.remove(id, Teleporting::class)
    world.add(id, EntityFx(kind, durationMs))
  }

  private fun refuse(world: World, id: EntityId, code: OpError) {
    world.get(id, Account::class)?.let { out.sendToPlayer(it.accountId, OperationErrorSMSG(code)) }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** Over land nearly every try lands; this many all failing means open water or the world's edge. */
    private const val LANDING_TRIES = 12
  }
}
