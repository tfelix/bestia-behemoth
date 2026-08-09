package net.bestia.zone.account.master

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.environment.weather.WeatherPublisher
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

@Component
class SelectMasterHandler(
  private val masterEntitySpawner: MasterEntitySpawner,
  private val world: WorldView,
  private val weatherPublisher: WeatherPublisher,
) : InMessageProcessor.IncomingMessageHandler<SelectMasterCMSG> {
  override val handles = SelectMasterCMSG::class

  override fun handle(msg: SelectMasterCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val masterEntityId = masterEntitySpawner.spawnMaster(msg.selectedMasterId)

    LOG.debug { "Selecting master ${msg.selectedMasterId} with entity id: $masterEntityId for account: ${msg.playerId}" }

    publishWeather(msg.playerId, masterEntityId)

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
   * Safe to read the entity here because `WorldView.createEntity` is a lock-holding scope rather than a
   * queued command: by the time [MasterEntitySpawner.spawnMaster] has returned, `Position` and `KnownSkills`
   * are on the entity. Both are pulled inside a single [WorldView.read] and copied out as plain values -
   * leaking the components themselves would reintroduce the race that type exists to prevent.
   *
   * Failure is quiet on purpose. Weather is ambient: a master spawning on a column the chunk service cannot
   * height-sample yet should still finish selecting, and the next sweep will pick it up.
   */
  private fun publishWeather(accountId: Long, masterEntityId: EntityId) {
    val senseSkillId = weatherPublisher.weatherSenseSkillId

    val standing = world.read {
      val position = get(masterEntityId, Position::class) ?: return@read null
      val level = senseSkillId?.let { get(masterEntityId, KnownSkills::class)?.levelOf(it) } ?: 0

      Standing(position.x, position.y, level)
    }

    if (standing == null) {
      LOG.warn { "Master entity $masterEntityId has no position; sending no initial weather" }
      return
    }

    weatherPublisher.publish(accountId, standing.voxelX, standing.voxelY, standing.weatherSenseLevel)
  }

  /** What the publisher needs, copied out from under the world lock. */
  private data class Standing(val voxelX: Long, val voxelY: Long, val weatherSenseLevel: Int)

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
