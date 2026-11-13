package net.bestia.zone.scry

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.potency.Potency
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.cartography.chart.ChartService
import net.bestia.zone.casting.ScrySight
import net.bestia.zone.casting.ScryTarget
import net.bestia.zone.casting.SkillScrying
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.PartyMembership
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.GroundHeight
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.session.NoActiveSessionException
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.stream.RemoteViewpoints
import org.springframework.stereotype.Service
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Opens a Sage's Scry: a hidden eye at the far spot, a trance on the body, and the far terrain streamed to the
 * caster's client besides its own. [ScryEnding] closes it again.
 */
@Service
class ScryService(
  private val viewpoints: RemoteViewpoints,
  private val statusEffects: StatusEffectService,
  private val groundHeight: GroundHeight,
  private val charts: ChartService,
  private val asyncJobs: AsyncJobExecutor,
  private val worldView: WorldView,
  private val messages: OutMessageProcessor,
  private val sessions: ConnectionInfoService,
  private val chunkService: ChunkService,
) : SkillScrying {

  private val random: Random = Random.Default

  override fun open(world: World, casterId: EntityId, target: ScryTarget, radiusMetres: Double, potency: Int) {
    val accountId = world.get(casterId, Account::class)?.accountId ?: return

    when (target) {
      is ScryTarget.PartyMember -> {
        val at = memberPosition(world, casterId, target.masterId)
        if (at == null) {
          messages.sendToPlayer(accountId, OperationErrorSMSG(OpError.SCRY_NO_SIGHT))
          return
        }
        world.defer { begin(world, casterId, accountId, at, radiusMetres, potency) }
      }

      is ScryTarget.Spot -> openIfCharted(world, casterId, accountId, target.at, radiusMetres, potency)
    }
  }

  /** Charts live in the database, which the tick may not read: the sight opens once the answer is back. */
  private fun openIfCharted(
    world: World,
    casterId: EntityId,
    accountId: AccountId,
    at: Vec3L,
    radiusMetres: Double,
    potency: Int
  ) {
    val masterId = world.get(casterId, Master::class)?.masterId ?: return
    val voxel = chunkService.config.voxelSize

    asyncJobs.submit(key = casterId) {
      val isCharted = charts.inventoryCoverage(masterId).contains(at.x * voxel, at.y * voxel)
      worldView.post {
        if (isCharted) {
          begin(this, casterId, accountId, at, radiusMetres, potency)
        } else {
          messages.sendToPlayer(accountId, OperationErrorSMSG(OpError.SCRY_UNCHARTED))
        }
      }
    }
  }

  /** Where the member's master stands, if it is in the caster's party and being played. */
  private fun memberPosition(world: World, casterId: EntityId, masterId: Long): Vec3L? {
    val member = world.get(casterId, PartyMembership::class)?.members?.firstOrNull { it.masterId == masterId }
      ?: return null
    if (!sessions.isPlaying(member.accountId, masterId)) return null

    val memberEntityId = try {
      sessions.getSelectedMasterEntityId(member.accountId)
    } catch (_: NoActiveSessionException) {
      return null
    }

    return world.get(memberEntityId, Position::class)?.toVec3L()
  }

  private fun begin(
    world: World,
    casterId: EntityId,
    accountId: AccountId,
    aimedAt: Vec3L,
    radiusMetres: Double,
    potency: Int
  ) {
    if (!world.isAlive(casterId) || world.has(casterId, ScrySight::class)) return
    val body = world.get(casterId, Position::class)?.toVec3L() ?: return

    val centre = onTheGround(drifted(aimedAt, driftMetres(body, aimedAt, potency)))
    val eyeId = world.createEntity { id ->
      add(id, Position.fromVec3(centre))
      add(id, EntityVisual(VisualKind.STRUCTURE, StaticEntityKind.SCRY_EYE.ordinal.toLong()))
      // What makes the eye a target a Dispel can pick, once Ruwach has uncovered it.
      add(id, CombatStats(CombatValues.OBJECT_BASELINE))
      add(id, ScryEye(casterId))
    }
    statusEffects.applyEffect(world, eyeId, StatusEffectId.SCRY_VEIL, level = 1, sourceEntityId = casterId, potency = potency)
    statusEffects.applyEffect(world, eyeId, StatusEffectId.SCRY_EYE, level = 1, sourceEntityId = casterId, potency = potency)

    world.add(casterId, ScrySight(centre, eyeId))
    statusEffects.applyEffect(world, casterId, StatusEffectId.SCRYING, level = 1, sourceEntityId = casterId, potency = potency)

    viewpoints.open(accountId, centre, chunksToCover(radiusMetres))
    messages.sendToPlayer(accountId, ScrySightSMSG(centre, radiusMetres, SECONDS))
  }

  /** How far off target a cast of [potency] may open, against the Disturbance of the distance it reaches. */
  private fun driftMetres(body: Vec3L, aimedAt: Vec3L, potency: Int): Double {
    val voxel = chunkService.config.voxelSize
    val distanceKm = hypot((aimedAt.x - body.x).toDouble(), (aimedAt.y - body.y).toDouble()) * voxel / 1000.0

    return MAX_DRIFT_METRES * (1 - Potency.edge(potency, disturbanceOf(distanceKm)))
  }

  /** Anywhere in the disc of [driftMetres] around [aimedAt], evenly. */
  private fun drifted(aimedAt: Vec3L, driftMetres: Double): Vec3L {
    if (driftMetres <= 0.0) return aimedAt

    val units = driftMetres * sqrt(random.nextDouble()) / chunkService.config.voxelSize
    val angle = random.nextDouble() * 2 * Math.PI

    return Vec3L(aimedAt.x + (units * cos(angle)).roundToLong(), aimedAt.y + (units * sin(angle)).roundToLong(), aimedAt.z)
  }

  /** A map spot carries no height, and a drift lands wherever it lands: start from the surface there. */
  private fun onTheGround(at: Vec3L): Vec3L {
    val surface = chunkService.surfaceElevationAt(at.x, at.y)?.let { chunkService.config.voxelZOf(it).toLong() } ?: at.z
    val z = groundHeight.standingZAt(Vec3L(at.x, at.y, surface)) ?: surface

    return Vec3L(at.x, at.y, z)
  }

  private fun chunksToCover(radiusMetres: Double): Int {
    val chunkMetres = chunkService.config.chunkSize * chunkService.config.voxelSize

    return ceil(radiusMetres / chunkMetres).toInt()
  }

  companion object {
    const val SECONDS = 20.0

    /** How far a sight opens off target at no Edge at all. Teleport's is 1.4 km. */
    const val MAX_DRIFT_METRES = 500.0

    /** See the docs' "Spell Potency", section "Against the World". Mana storms and rifts do not exist yet. */
    fun disturbanceOf(distanceKm: Double): Int {
      return (BASE_DISTURBANCE + DISTURBANCE_PER_KM * distanceKm).toInt()
    }

    private const val BASE_DISTURBANCE = 10
    private const val DISTURBANCE_PER_KM = 5
  }
}
