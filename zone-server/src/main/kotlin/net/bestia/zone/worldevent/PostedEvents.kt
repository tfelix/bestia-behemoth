package net.bestia.zone.worldevent

import net.bestia.zone.blackboard.EventNotices
import net.bestia.zone.cartography.poi.MapPoi
import net.bestia.zone.cartography.poi.MapPoiIndex
import net.bestia.zone.cartography.render.MapService
import net.bestia.zone.place.ecs.PlaceNameService
import net.bestia.zone.place.ecs.ProvinceService
import org.springframework.stereotype.Component

/**
 * The running events as the boards post them: each board shows those of its own province. The director replaces
 * the list every run; boards read it whenever a master opens one.
 */
@Component
class PostedEvents(
  private val provinces: ProvinceService,
  private val placeNames: PlaceNameService,
  private val map: MapPoiIndex,
) : EventNotices {

  @Volatile
  private var posted: List<Posted> = emptyList()

  override fun postedAt(x: Long, y: Long): List<EventNotices.Notice> {
    val province = provinces.provinceAt(x.toDouble(), y.toDouble()) ?: return emptyList()

    return posted.filter { it.provinceIndex == province.index }.map { it.notice }
  }

  fun replace(running: List<Running>) {
    posted = running.map { Posted(it.provinceIndex, noticeOf(it)) }
    // The same notices go on the map, which shows them wherever a master's charts show the ground.
    map.setWorldEvents(running.zip(posted) { event, post -> poiOf(event.instanceId, post.notice) })
  }

  private fun poiOf(instanceId: Long, notice: EventNotices.Notice): MapPoi {
    val x = notice.x.toDouble()
    val y = notice.y.toDouble()
    return MapPoi(POI_ID_BASE - instanceId, MapService.WORLD_EVENT, notice.eventId, x, y, notice.placeName, null, null)
  }

  private fun noticeOf(running: Running): EventNotices.Notice {
    val placeName = placeNames.resolve(running.x, running.y).name

    return EventNotices.Notice(running.eventId, placeName, running.x, running.y, running.stage)
  }

  /** A running event where its ground is now. */
  data class Running(
    val instanceId: Long,
    val eventId: String,
    val provinceIndex: Int,
    val x: Long,
    val y: Long,
    val stage: Int,
  )

  private data class Posted(val provinceIndex: Int, val notice: EventNotices.Notice)

  private companion object {
    /** Below the player towns' and the Strongholds' ids, so an event's point meets no other. */
    const val POI_ID_BASE = -3_000_000_000L
  }
}
