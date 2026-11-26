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
 * Tells the world when the doomsday moves to another stage, or its Foreshadowing strikes: every master online reads
 * which way it lies, and the towns near the doomsday's origin start to talk.
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

    tellEveryone(event.originX, event.originY) { direction ->
      when (announcement) {
        Announcement.DOOM_ONSET, Announcement.DOOM_ESCALATION -> AnnouncementSMSG(announcement, listOf(direction, name))
        else -> AnnouncementSMSG(announcement, listOf(name))
      }
    }
  }

  fun foreshadowBegan(kind: ForeshadowKind, x: Double, y: Double) {
    tellEveryone(x, y) { direction ->
      AnnouncementSMSG(Announcement.FORESHADOW_BEGINS, listOf(direction, "FORESHADOW_${kind.name}"))
    }
  }

  fun foreshadowEnded(kind: ForeshadowKind, contained: Boolean) {
    val announcement = if (contained) Announcement.FORESHADOW_HELD else Announcement.FORESHADOW_FAILED
    out.sendToAllConnected(AnnouncementSMSG(announcement, listOf("FORESHADOW_${kind.name}")))
  }

  /** Sends every master online what [build] makes of the direction from where they stand to [x], [y]. */
  private fun tellEveryone(x: Double, y: Double, build: (direction: String) -> AnnouncementSMSG) {
    world.post {
      query(Master::class, Account::class, Position::class).each { _ ->
        out.sendToPlayer(get<Account>().accountId, build(directionTo(get<Position>(), x, y)))
      }
    }
  }

  private fun directionTo(here: Position, x: Double, y: Double): String {
    val fromX = here.x * voxelMetres
    val fromY = here.y * voxelMetres
    return Compass.tokenOf(wrap.deltaX(fromX, x), wrap.deltaY(fromY, y))
  }

  private fun toVoxel(metres: Double): Long {
    return (metres / voxelMetres).toLong()
  }

  private companion object {
    const val RUMOUR_STRENGTH = 1.0
  }
}
