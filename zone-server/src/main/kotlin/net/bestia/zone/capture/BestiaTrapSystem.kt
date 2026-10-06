package net.bestia.zone.capture

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.aoi.AoiLayer
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.battle.ecs.damage.TakenDamage
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Invulnerable
import net.bestia.zone.battle.ecs.status.StatusValues
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.update
import net.bestia.zone.ecs.construction.ConstructionSystem
import net.bestia.zone.respawn.ecs.RespawnSystem
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.PersistedEntityDeletionQueue
import net.bestia.zone.persistence.Persistent
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.SkillRepository
import net.bestia.zone.skill.findByIdentifier
import net.bestia.zone.util.EntityId
import org.springframework.context.ApplicationEventPublisher
import java.util.Random
import org.springframework.stereotype.Component as SpringComponent

/**
 * Springs a [BestiaTrap] on the first wild bestia standing on its tile and rolls the catch.
 *
 * In the actions phase, which follows movement, so a creature is caught on the tick it steps in.
 */
@SpringComponent
class BestiaTrapSystem(
  private val entityAOIService: EntityAOIService,
  private val bestiaCatalogue: BestiaCatalogue,
  private val captureChanceCalculator: CaptureChanceCalculator,
  private val deletionQueue: PersistedEntityDeletionQueue,
  private val events: ApplicationEventPublisher,
  private val outMessageProcessor: OutMessageProcessor,
  private val skills: SkillRepository,
  private val random: Random = ThreadLocalRandomSource,
) : System {

  override val phase = Phase.ACTIONS
  override val after = setOf(RespawnSystem::class, ConstructionSystem::class)
  override val schedule: Schedule = Schedule.EveryTick

  override val reads: ComponentClassSet = setOf(
    Position::class, EntityVisual::class, Account::class, Dead::class, Invulnerable::class,
    Health::class, StatusValues::class, KnownSkills::class, Persistent::class
  )

  /** [TakenDamage] is written on the creature that broke free, so it turns on the trapper. */
  override val writes: ComponentClassSet = setOf(BestiaTrap::class, TakenDamage::class)

  /** Resolved by identifier, because the id in `skills.yml` is content and this is code. */
  private val beastfriendSkillId: Long? by lazy { skills.findByIdentifier(SkillId.BEASTFRIEND)?.id }

  private class Sprung(val trapId: EntityId, val trap: BestiaTrap, val at: Vec3L, val targetId: EntityId)

  override fun update(world: World, deltaTime: Float) {
    // Collected first: destroying inside the query would mutate what it is iterating.
    val expired = mutableListOf<EntityId>()
    val sprung = mutableListOf<Sprung>()

    world.query(BestiaTrap::class, Position::class).each { trapId ->
      val trap = get<BestiaTrap>()
      val at = get<Position>().toVec3L()

      trap.remainingSeconds -= deltaTime
      if (trap.remainingSeconds <= 0f) {
        expired.add(trapId)
        return@each
      }

      val target = wildBestiaOn(world, at) ?: return@each
      sprung.add(Sprung(trapId, trap, at, target))
    }

    expired.forEach { world.destroy(it) }

    if (sprung.isEmpty()) {
      return
    }

    // Deferred so the destroys and the TakenDamage below apply at once rather than being queued mid-iteration.
    world.defer {
      for (s in sprung) {
        if (!world.isAlive(s.trapId) || !isWildBestia(world, s.targetId)) continue
        resolve(world, s)
      }
    }
  }

  private fun resolve(world: World, s: Sprung) {
    val chance = chanceFor(world, s.trap, s.targetId)
    val caught = random.nextDouble() < chance

    LOG.debug { "Trap ${s.trapId} sprung on ${s.targetId} at ${s.at}: chance $chance, caught $caught" }

    world.destroy(s.trapId)
    outMessageProcessor.sendToAllPlayersInRange(
      s.at,
      BestiaCaptureSMSG(s.trapId, s.targetId, s.trap.trapperEntityId, caught)
    )

    if (caught) {
      capture(world, s)
    } else {
      // No damage, only a timestamp: what the creature's retaliation reads is who touched it last.
      world.update(s.targetId, { TakenDamage() }) { it.addDamage(s.trap.trapperEntityId, 0) }
    }
  }

  /** The creature leaves the world here, on the tick, so it cannot spring a second trap. */
  private fun capture(world: World, s: Sprung) {
    val speciesId = world.getOrThrow(s.targetId, EntityVisual::class).id

    // A den or ambient spawner notices the gap by itself; only the persisted row has to be told.
    if (world.has(s.targetId, Persistent::class)) {
      deletionQueue.enqueue(s.targetId)
    }
    world.destroy(s.targetId)

    LOG.info { "Master ${s.trap.masterId} caught bestia $speciesId (entity ${s.targetId}) at ${s.at}" }

    events.publishEvent(BestiaCapturedEvent(s.trap.ownerAccountId, s.trap.masterId, speciesId, s.at))
  }

  private fun chanceFor(world: World, trap: BestiaTrap, targetId: EntityId): Double {
    val species = bestiaCatalogue.byId(world.getOrThrow(targetId, EntityVisual::class).id)
    val health = world.getOrThrow(targetId, Health::class)

    // A trapper who has logged out since setting it adds nothing but the trap itself.
    val willpower = world.get(trap.trapperEntityId, StatusValues::class)?.willpower ?: 0
    val beastfriend = beastfriendSkillId
      ?.let { world.get(trap.trapperEntityId, KnownSkills::class)?.levelOf(it) }
      ?: 0

    return captureChanceCalculator.chance(
      CaptureChanceCalculator.Input(
        tier = trap.tier,
        targetLevel = species.level,
        targetHp = health.current,
        targetMaxHp = health.max,
        trapperWillpower = willpower,
        // Not in the skill catalogue yet.
        bestiaTrappingLevel = 0,
        beastfriendLevel = beastfriend,
      )
    )
  }

  private fun wildBestiaOn(world: World, at: Vec3L): EntityId? {
    // The trap stands at the height the client aimed at, not a server ground sample, so the cube allows a
    // couple of voxels either way; the x/y check below narrows it back to the trap's own tile.
    return entityAOIService.queryEntitiesInCube(at, HEIGHT_TOLERANCE * 2, AoiLayer.DYNAMIC_ONLY)
      .filter { id ->
        val pos = world.get(id, Position::class) ?: return@filter false
        pos.x == at.x && pos.y == at.y && isWildBestia(world, id)
      }
      .minOrNull()
  }

  private fun isWildBestia(world: World, id: EntityId): Boolean {
    if (!world.isAlive(id)) return false
    if (world.get(id, EntityVisual::class)?.kind != VisualKind.BESTIA) return false

    return !world.has(id, Account::class) &&
        !world.has(id, Dead::class) &&
        !world.has(id, Invulnerable::class) &&
        world.has(id, Health::class)
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    const val HEIGHT_TOLERANCE = 2L
  }
}
