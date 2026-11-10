package net.bestia.zone.breeding.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.bnet.proto.OperationSuccessProto.OpSuccess
import net.bestia.zone.account.OwnedBestiaPolicyViolationException
import net.bestia.zone.breeding.BreedingStateSender
import net.bestia.zone.breeding.Hatching
import net.bestia.zone.breeding.Incubator
import net.bestia.zone.breeding.Incubators
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.master.bestia.OwnedBestiasPublisher
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OperationSuccessSMSG
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.skill.ecs.KnownSkills
import java.time.Clock
import java.time.Duration
import org.springframework.stereotype.Component as SpringComponent

/** Hatches an egg that has warmed long enough, by [Hatching] off the tick, next to its incubator. */
@SpringComponent
class IncubatorSystem(
  private val structures: PlayerStructureRegistry,
  private val incubators: Incubators,
  private val hatching: Hatching,
  private val states: BreedingStateSender,
  private val ownedBestias: OwnedBestiasPublisher,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val worldView: WorldView,
  private val clock: Clock = Clock.systemUTC(),
) : System {

  override val phase = Phase.ACTIONS
  override val schedule: Schedule = Schedule.EverySeconds(1f)
  override val reads: ComponentClassSet = setOf(KnownSkills::class)
  override val writes: ComponentClassSet = emptySet()

  override fun update(world: World, deltaTime: Float) {
    val now = clock.instant()

    for (incubator in incubators.all().toList()) {
      val structure = structures.of(incubator.structureId)
      if (structure == null) {
        incubators.remove(incubator.structureId)
        continue
      }

      val egg = incubator.egg ?: continue
      if (incubator.hatching || !incubator.isReadyAt(now)) continue
      if (incubator.retryAt != null && now.isBefore(incubator.retryAt)) continue

      incubators.put(incubator.copy(hatching = true))
      val accountId = structure.ownerAccountId

      asyncJobExecutor.submit(structure.ownerMasterId) {
        val outcome = try {
          hatching.hatch(structure.ownerMasterId, egg.instanceId, besideOf(structure))
          accountId?.let(ownedBestias::publish)
          Outcome.HATCHED
        } catch (e: OwnedBestiaPolicyViolationException) {
          Outcome.NO_SLOT
        } catch (e: Exception) {
          LOG.error(e) { "Incubator ${structure.id} could not hatch egg ${egg.instanceId}" }
          Outcome.FAILED
        }
        worldView.post { hatched(this, incubator.structureId, outcome) }
      }
    }
  }

  private fun hatched(world: World, structureId: Long, outcome: Outcome) {
    val incubator = incubators.of(structureId)
    val retryAt = clock.instant().plus(RETRY_AFTER)

    when (outcome) {
      Outcome.HATCHED -> {
        incubators.put(incubator.empty())
        states.notify(structureId, OperationSuccessSMSG(OpSuccess.BESTIA_HATCHED))
      }
      Outcome.NO_SLOT -> {
        if (!incubator.waitingForSlot) {
          states.notify(structureId, OperationErrorSMSG(OpError.INCUBATOR_NO_FREE_SLOT))
        }
        incubators.put(incubator.copy(hatching = false, waitingForSlot = true, retryAt = retryAt))
      }
      Outcome.FAILED -> incubators.put(incubator.copy(hatching = false, retryAt = retryAt))
    }

    states.sendIncubator(world, structureId)
  }

  /** Outside the incubator's walls, so the hatchling does not stand inside them. */
  private fun besideOf(structure: StructureEntry): Vec3L {
    return Vec3L(structure.position.x + HATCH_OFFSET, structure.position.y, structure.position.z)
  }

  private enum class Outcome { HATCHED, NO_SLOT, FAILED }

  private companion object {
    val RETRY_AFTER: Duration = Duration.ofMinutes(1)
    const val HATCH_OFFSET = 3L

    val LOG = KotlinLogging.logger { }
  }
}
