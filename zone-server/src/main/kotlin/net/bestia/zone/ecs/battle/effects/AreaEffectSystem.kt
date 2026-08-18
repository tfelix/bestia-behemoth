package net.bestia.zone.ecs.battle.effects

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.ecs.AoiLayer
import net.bestia.zone.ecs.EntityAOIService
import net.bestia.zone.ecs.battle.damage.Damage
import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * The one system behind every ground effect, whatever its cadence or duration.
 *
 * Runs [Schedule.EveryTick] and lets each [AreaEffect] keep its own accumulator, rather than each
 * cadence needing a system scheduled at that cadence. A `Schedule.EverySeconds(1.2f)` system could
 * only ever serve effects that tick every 1.2 seconds; here a 1.2s fire patch and a 3s poison cloud
 * are the same code with different numbers, and the finest cadence anything can have is the tick
 * rate rather than a system's own schedule.
 *
 * Ordered before [net.bestia.zone.ecs.battle.damage.ReceivedDamageSystem] (50), which reads the
 * [Damage] this writes - that read/write overlap is what actually orders the two, `@Order` alone
 * would not (see `ChunkStreamSystem`'s note on the same trap).
 */
@SpringComponent
@Order(48)
class AreaEffectSystem(
  private val entityAOIService: EntityAOIService,
  private val outMessageProcessor: OutMessageProcessor
) : System {

  override val schedule: Schedule = Schedule.EveryTick

  override val reads: ComponentClassSet = setOf(Position::class, Health::class, Dead::class)
  override val writes: ComponentClassSet = setOf(AreaEffect::class, Damage::class)

  override fun update(world: World, deltaTime: Float) {
    // Collected first: destroying inside the query would mutate what it is iterating.
    var spent: MutableList<EntityId>? = null

    world.query(AreaEffect::class, Position::class).each { id ->
      val effect = get<AreaEffect>()
      val center = get<Position>().toVec3L()

      effect.sinceLastTick += deltaTime

      // A while loop rather than an if, so a long frame delivers every tick it swallowed instead of
      // silently dropping them.
      while (effect.sinceLastTick >= effect.tickIntervalSeconds && effect.remainingTicks > 0) {
        effect.sinceLastTick -= effect.tickIntervalSeconds
        effect.remainingTicks--
        applyTick(world, id, effect, center)
      }

      if (effect.remainingTicks <= 0) {
        (spent ?: mutableListOf<EntityId>().also { spent = it }).add(id)
      }
    }

    spent?.forEach { id ->
      LOG.debug { "Area effect $id burnt out" }
      world.destroy(id)
    }
  }

  private fun applyTick(world: World, effectEntityId: EntityId, effect: AreaEffect, center: Vec3L) {
    // `size` is the cube's edge and `Cube.collide` is inclusive on the upper bound, so an edge of
    // 2 * radius spans 2 * radius + 1 tiles per axis - a radius of 1 is the 3x3 that was aimed at.
    // AoiLayer.ALL on purpose: statics are in the same index, and a fire that spares the trees is
    // the defect.
    val edge = effect.radiusTiles * 2
    val inside = entityAOIService.queryEntitiesInCube(center, edge, AoiLayer.ALL)

    // Deferred for the reason SkillExecutionService.applyResult defers: `World.add` is itself
    // deferred while a system iterates, so staging inline would let two sources landing on one target
    // in the same tick each create their own Damage component and lose one of them. Inside a deferred
    // block structural changes apply immediately, making the get-or-create below sound.
    world.defer {
      for (victimId in inside) {
        if (victimId == effectEntityId) continue
        if (!effect.hitsCaster && victimId == effect.casterId) continue
        if (!world.isAlive(victimId)) continue
        if (world.has(victimId, Dead::class)) continue
        if (!world.has(victimId, Health::class)) continue

        val damage = world.get(victimId, Damage::class) ?: world.add(victimId, Damage())
        damage.add(effect.damagePerTick, effect.casterId)

        outMessageProcessor.sendToAllPlayersInRange(
          center,
          DamageEntitySMSG(
            entityId = victimId,
            sourceEntityId = effect.casterId,
            attackId = effect.skillId.toInt(),
            div = 1,
            damage = effect.damagePerTick,
            skillLevel = effect.skillLevel,
            type = DamageEntitySMSG.DamageType.NORMAL
          )
        )
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
