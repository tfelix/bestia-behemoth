package net.bestia.api.push

import net.bestia.bus.contract.ZoneEvent

/**
 * What the service worker shows. [url] is the app page a tap opens; [tag] lets a newer notification of the same
 * kind replace an older one instead of stacking.
 */
data class Notification(val title: String, val body: String, val url: String, val tag: String) {

  companion object {
    /** The notification for [event], or null for the kinds that only ever go to an open app. */
    fun of(event: ZoneEvent): Notification? {
      return when (event) {
        is ZoneEvent.HomeFell -> Notification(
          title = "${event.fallen} has fallen",
          body = "${event.masterName} now calls ${event.newHome} home.",
          url = "/m/${event.masterId}",
          tag = "home-${event.masterId}",
        )

        is ZoneEvent.BestiaDiedAway -> Notification(
          title = "${event.bestiaName ?: event.species} was killed",
          body = "It lies where it fell until you return.",
          url = "/m/${event.masterId}/bestia",
          tag = "bestia-${event.playerBestiaId}",
        )

        is ZoneEvent.ConstructionDone -> Notification(
          title = "Construction finished",
          body = "Your ${event.kind.lowercase().replace('_', ' ')} is standing.",
          url = "/m/${event.masterId}",
          tag = "construction-${event.masterId}",
        )

        // Chat is read in the app, never pushed: a busy guild would spend the hourly budget in a minute.
        is ZoneEvent.StanceChanged, is ZoneEvent.Presence, is ZoneEvent.ChartsChanged, is ZoneEvent.ChatLine -> null
      }
    }
  }
}
