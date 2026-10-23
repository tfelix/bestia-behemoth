package net.bestia.zone.metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.skill.Casting
import net.bestia.zone.battle.ecs.skill.Crafting
import net.bestia.zone.battle.ecs.status.InCombat
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.movement.ecs.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import org.springframework.stereotype.Component as SpringComponent

/**
 * Counts what players are doing and which status effects are running, every few seconds, for the metrics.
 *
 * A system, because both questions need a query across entities, which only the tick thread may ask. What it
 * finds it publishes as plain integers the export reads, so a scrape never touches the world. Reads and
 * writes nothing else.
 */
@SpringComponent
class WorldStateSampler(private val registry: MeterRegistry) : System {
  override val phase = Phase.UPKEEP

  override val schedule: Schedule = Schedule.EverySeconds(SAMPLE_SECONDS)

  override val reads: ComponentClassSet = setOf(
    Master::class, InCombat::class, Casting::class, Crafting::class, Path::class, StatusEffects::class,
  )

  override val writes: ComponentClassSet = emptySet()

  private val activities: Map<String, Pair<KClass<out Component>, AtomicInteger>> = mapOf(
    "in_combat" to InCombat::class,
    "casting" to Casting::class,
    "crafting" to Crafting::class,
    "walking" to Path::class,
  ).mapValues { (_, marker) -> marker to AtomicInteger() }

  /** Running effects by definition id, as of the last sample. Replaced whole, never edited in place. */
  @Volatile
  private var effects: Map<Long, Int> = emptyMap()

  private val effectGauges = ConcurrentHashMap.newKeySet<Long>()

  init {
    for ((activity, entry) in activities) {
      Gauge.builder("zone.players.active", entry.second) { it.get().toDouble() }
        .tag("activity", activity)
        .description("Masters doing each thing, as of the last sample (every five seconds); one may do several")
        .register(registry)
    }
  }

  override fun update(world: World, deltaTime: Float) {
    for ((_, entry) in activities) {
      var count = 0
      world.query(Master::class, entry.first).each { count++ }
      entry.second.set(count)
    }

    val counts = HashMap<Long, Int>()
    world.query(StatusEffects::class).each {
      get<StatusEffects>().activeEffects.forEach { counts.merge(it.definitionId, 1, Int::plus) }
    }
    effects = counts
    counts.keys.forEach(::track)
  }

  /** A gauge per effect, made the first time it is seen and reading zero once it is gone. */
  private fun track(definitionId: Long) {
    if (!effectGauges.add(definitionId)) return

    Gauge.builder("zone.status.effects.active", this) { (it.effects[definitionId] ?: 0).toDouble() }
      .tag("effect", definitionId.toString())
      .description("Entities under each status effect, by the effect's id in status_effects.yml; made when first seen")
      .register(registry)
  }

  private companion object {
    const val SAMPLE_SECONDS = 5f
  }
}
