package net.bestia.zone.world.spoor

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.ecs.account.Master
import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.entity.EntityVisual
import net.bestia.zone.ecs.entity.VisualKind
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.util.EntityId
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Writes down what everything that is walking *is*, while it is still there to ask.
 *
 * ### `@Order(39)`, immediately ahead of `MoveSystem`
 *
 * Load bearing rather than tidy. `MoveSystem` (40) takes a `Path` off an entity the moment it arrives, and a
 * removal made while a query is iterating is applied at the next sync point - which can be before anything
 * later in the tick runs. Signing before the movement means even a single-tile walk, begun and finished inside
 * one tick, is signed before the print it leaves.
 *
 * ### It queries `Path`, which is exactly the set that leaves prints
 *
 * `MoveSystem` is the only thing that calls `GroundTrample`, and it only touches entities with a `Path`. So
 * this pass is bounded by how many things are walking, never by how many exist - and a world where nothing
 * moves costs one empty query.
 *
 * ### The reads are declared and the writes are empty, both honestly
 *
 * It really does read those four components, and it writes none - [ActorSignatures] is not a component. Read
 * declarations only conflict with writers, and the only writer of any of these is a level-up.
 */
@Component
@Order(39)
class ActorSignatureSystem(
  private val signatures: ActorSignatures,
  private val catalogue: BestiaCatalogue,
  private val clock: BestiaClock,
) : System {

  override val schedule: Schedule = Schedule.EveryTick

  override val reads: ComponentClassSet = setOf(
    Path::class, Master::class, Level::class, EntityVisual::class
  )

  override val writes: ComponentClassSet = emptySet()

  private var secondsSinceSweep = 0f

  override fun update(world: World, deltaTime: Float) {
    val now = clock.now().absoluteSecond

    world.query(Path::class).each { id ->
      if (!signatures.needsRefresh(id, now)) return@each

      signatureOf(world, id)?.let { signatures.remember(id, it, now) }
    }

    secondsSinceSweep += deltaTime
    if (secondsSinceSweep < SWEEP_SECONDS) return
    secondsSinceSweep = 0f

    val forgotten = signatures.sweep(now)
    if (forgotten > 0) {
      LOG.debug { "spoor: forgot $forgotten actor(s), ${signatures.size} still described" }
    }
  }

  /**
   * What one entity is.
   *
   * A master first, because a master also carries an [EntityVisual] and is not a species. Null for anything
   * that is neither - a dropped item and a spell effect both walk nowhere, but a ground effect drifting on a
   * path would otherwise be described as an unknown creature.
   */
  private fun signatureOf(world: World, entityId: EntityId): ActorSignature? {
    world.get(entityId, Master::class)?.let { master ->
      return ActorSignature(
        kind = ActorKind.MASTER,
        speciesId = master.masterId,
        level = world.get(entityId, Level::class)?.level ?: 0,
        identifier = "",
        masterName = master.name,
      )
    }

    val visual = world.get(entityId, EntityVisual::class) ?: return null
    if (visual.kind != VisualKind.BESTIA) return null

    val species = runCatching { catalogue.byId(visual.id) }.getOrNull() ?: return null

    return ActorSignature(
      kind = ActorKind.BESTIA,
      speciesId = species.id,
      // A wild mob carries no Level component at all, so the species row is the only answer there is.
      level = world.get(entityId, Level::class)?.level ?: species.level,
      identifier = species.identifier,
      masterName = null,
    )
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    const val SWEEP_SECONDS = 60f
  }
}
