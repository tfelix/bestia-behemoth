package net.bestia.zone.metrics

import io.micrometer.core.instrument.FunctionCounter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.entity.ecs.Living
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.entity.ecs.WorldObjectIdentity
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.ecs.GroundItemStack
import net.bestia.zone.prop.ecs.construction.ConstructionSite
import net.bestia.zone.script.ecs.ScriptComponent
import net.bestia.zone.spawn.ecs.DenMember
import net.bestia.zone.spawn.ecs.Spawner
import net.bestia.zone.spawn.ecs.ambient.Ambient
import net.bestia.zone.townsfolk.ecs.Townsfolk
import org.springframework.stereotype.Component as SpringComponent
import kotlin.reflect.KClass

/**
 * What the world holds, read when scraped. Each kind is the size of one component's store: a count the ECS keeps
 * anyway, so reading it walks nothing and the tick is never asked. Which components stand for which kind is the
 * one place that says what a "mob" or a "prop" is.
 */
@SpringComponent
class WorldMeters(private val world: EcsWorld) : MeterBinder {

  override fun bindTo(registry: MeterRegistry) {
    for ((kind, marker) in MARKERS) {
      gauge(registry, kind) { world.componentCount(marker) }
    }

    // A mob is a creature nobody owns and nobody lives in a town as: there is no marker that says so.
    gauge(registry, "mob") {
      (world.componentCount(Living::class) - world.componentCount(Account::class) - world.componentCount(Townsfolk::class))
        .coerceAtLeast(0)
    }

    FunctionCounter.builder("zone.world.entities.lifecycle", world) { it.created.toDouble() }
      .tag("event", "created")
      .description("Entities created and destroyed since start; the gap between them is the world's growth")
      .register(registry)
    FunctionCounter.builder("zone.world.entities.lifecycle", world) { it.destroyed.toDouble() }
      .tag("event", "destroyed")
      .register(registry)
  }

  private fun gauge(registry: MeterRegistry, kind: String, count: () -> Int) {
    Gauge.builder("zone.world.entities", this) { count().toDouble() }
      .tag("kind", kind)
      .description("Entities in the world by what they are; a creature owned by a player is a player_bestia, not a mob")
      .register(registry)
  }

  private companion object {
    /** The component that marks each kind. A kind may overlap another: a player's bestia is also a creature. */
    val MARKERS: List<Pair<String, KClass<out Component>>> = listOf(
      "master" to Master::class,
      "player_bestia" to OwnedBestia::class,
      "townsfolk" to Townsfolk::class,
      "ambient" to Ambient::class,
      "den_member" to DenMember::class,
      "den" to Spawner::class,
      "ground_item" to GroundItemStack::class,
      "area_effect" to AreaEffect::class,
      "prop" to WorldObjectIdentity::class,
      "structure" to PlayerStructureIdentity::class,
      "construction_site" to ConstructionSite::class,
      "script" to ScriptComponent::class,
      "corpse" to Dead::class,
    )
  }
}
