package net.bestia.zone.account.master

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.environment.weather.WeatherPublisher
import net.bestia.zone.item.equip.EquipmentRevalidationService
import net.bestia.zone.message.HandlerLane
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

@Component
class SelectMasterHandler(
  private val masterEntitySpawner: MasterEntitySpawner,
  private val world: WorldView,
  private val weatherPublisher: WeatherPublisher,
  private val equipmentRevalidationService: EquipmentRevalidationService,
  private val asyncJobExecutor: AsyncJobExecutor,
) : InMessageProcessor.IncomingMessageHandler<SelectMasterCMSG> {
  override val handles = SelectMasterCMSG::class
  override val lane = HandlerLane.IO

  override fun handle(msg: SelectMasterCMSG): Boolean {
    // The row is read below; a logout or a grant for this master may still be on its way to it.
    asyncJobExecutor.awaitPending(msg.selectedMasterId)
    val masterEntityId = masterEntitySpawner.spawnMaster(msg.selectedMasterId)

    LOG.debug { "Selecting master ${msg.selectedMasterId} with entity id: $masterEntityId for account: ${msg.playerId}" }

    publishWeather(msg.playerId, masterEntityId)

    // The safety net for everything that is not a skill investment: gear that became illegal while its owner
    // was offline, or before the rule that refuses it existed. MasterEntitySpawner replays whatever the
    // container says is worn without consulting EquipmentService, so this is the only thing that re-asks.
    equipmentRevalidationService.revalidate(msg.selectedMasterId, masterEntityId)

    return true
  }

  /**
   * The player's first weather message, sent now rather than on `WeatherSystem`'s next sweep.
   *
   * That sweep runs on `weather.evaluation-seconds` (ten by default), and its dedup map treats a newly seen
   * account as an immediate send - so the delay was never a missing push, only the sweep's scheduling phase.
   * It still meant the client could spend up to ten seconds rendering whatever sky it started with while
   * standing in a blizzard, which is most visible exactly where it matters least to be wrong: the first
   * seconds after entering the world.
   *
   * Published inside one [WorldView.read] scope: the entity is read there, and publishing samples the chunk
   * service's height cache, which belongs to the tick thread.
   *
   * Failure is quiet on purpose. Weather is ambient: a master spawning on a column the chunk service cannot
   * height-sample yet should still finish selecting, and the next sweep will pick it up.
   */
  private fun publishWeather(accountId: Long, masterEntityId: EntityId) {
    val senseSkillId = weatherPublisher.weatherSenseSkillId

    val published = world.read {
      val position = get(masterEntityId, Position::class) ?: return@read false
      val level = senseSkillId?.let { get(masterEntityId, KnownSkills::class)?.levelOf(it) } ?: 0

      weatherPublisher.publish(accountId, position.x, position.y, level)
      true
    }

    if (!published) {
      LOG.warn { "Master entity $masterEntityId has no position; sending no initial weather" }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
