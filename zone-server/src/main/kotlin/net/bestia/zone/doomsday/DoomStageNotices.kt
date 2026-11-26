package net.bestia.zone.doomsday

import net.bestia.worldgen.core.WorldWrap
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Compass
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.message.AnnouncementSMSG
import net.bestia.zone.message.AnnouncementSMSG.Announcement
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.townsfolk.rumour.RumourKind
import net.bestia.zone.townsfolk.rumour.RumourService
import net.bestia.zone.world.WorldService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Tells the world when the doomsday moves to another stage: every master online reads which way it lies, and the
 * towns near its origin start to talk.
 */
@Component
class DoomStageNotices(
  private val world: WorldView,
  private val out: OutMessageProcessor,
  private val rumours: RumourService,
  private val worldService: WorldService,
) {

  private val wrap by lazy { WorldWrap(worldService.config) }
  private val voxelMetres by lazy { worldService.config.voxelSize }

  @EventListener
  fun onStageChanged(event: DoomStageChanged) {
    val receding = event.to < event.from
    if (!receding) {
      rumours.post(RumourKind.DOOM_GROWS, toVoxel(event.originX), toVoxel(event.originY), RUMOUR_STRENGTH)
    }

    val announcement = when {
      receding -> Announcement.DOOM_RECEDES
      event.to == DoomStage.ONSET -> Announcement.DOOM_ONSET
      event.to == DoomStage.ESCALATION -> Announcement.DOOM_ESCALATION
      event.to == DoomStage.CATACLYSM -> Announcement.DOOM_CATACLYSM
      else -> return
    }
    val name = "DOOM_${event.kind.name}"

    world.post {
      query(Master::class, Account::class, Position::class).each { _ ->
        val args = when (announcement) {
          Announcement.DOOM_ONSET, Announcement.DOOM_ESCALATION -> listOf(directionTo(get<Position>(), event), name)
          else -> listOf(name)
        }
        out.sendToPlayer(get<Account>().accountId, AnnouncementSMSG(announcement, args))
      }
    }
  }

  private fun directionTo(here: Position, event: DoomStageChanged): String {
    val x = here.x * voxelMetres
    val y = here.y * voxelMetres
    return Compass.tokenOf(wrap.deltaX(x, event.originX), wrap.deltaY(y, event.originY))
  }

  private fun toVoxel(metres: Double): Long {
    return (metres / voxelMetres).toLong()
  }

  private companion object {
    const val RUMOUR_STRENGTH = 1.0
  }
}
