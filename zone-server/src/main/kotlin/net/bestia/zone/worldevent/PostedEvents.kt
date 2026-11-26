package net.bestia.zone.worldevent

import net.bestia.zone.blackboard.EventNotices
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
) : EventNotices {

  @Volatile
  private var posted: List<Posted> = emptyList()

  override fun postedAt(x: Long, y: Long): List<EventNotices.Notice> {
    val province = provinces.provinceAt(x.toDouble(), y.toDouble()) ?: return emptyList()

    return posted.filter { it.provinceIndex == province.index }.map { it.notice }
  }

  fun replace(running: List<Running>) {
    posted = running.map { Posted(it.provinceIndex, noticeOf(it)) }
  }

  private fun noticeOf(running: Running): EventNotices.Notice {
    val placeName = placeNames.resolve(running.x, running.y).name

    return EventNotices.Notice(running.eventId, placeName, running.x, running.y, running.stage)
  }

  /** A running event where its ground is now. */
  data class Running(val eventId: String, val provinceIndex: Int, val x: Long, val y: Long, val stage: Int)

  private data class Posted(val provinceIndex: Int, val notice: EventNotices.Notice)
}
