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

        // Told in the game already when the player was in it.
        is ZoneEvent.JourneyEnded -> if (event.whilePlaying) null else journeyEnded(event)

        // A letter waits 30 days, so unlike chat it is worth waking somebody for.
        is ZoneEvent.LetterArrived -> Notification(
          title = "A letter from ${event.senderName}",
          body = event.subject.ifEmpty { "A pigeon brought it." },
          url = "/m/${event.masterId}/mail",
          tag = "letter-${event.letterId}",
        )

        is ZoneEvent.ParcelArrived -> Notification(
          title = "A parcel from ${event.senderName}",
          body = "It waits at ${event.officeName}.",
          url = "/m/${event.masterId}/mail",
          tag = "parcel-${event.parcelId}",
        )

        // Told in the game already when the player was in it; hauls and new orders are only for the diary.
        is ZoneEvent.BestiaHappening -> if (event.whilePlaying) null else bestiaHappening(event)

        // Chat is read in the app, never pushed: a busy guild would spend the hourly budget in a minute.
        is ZoneEvent.OrdersChanged, is ZoneEvent.Presence, is ZoneEvent.ChartsChanged, is ZoneEvent.ChatLine,
        is ZoneEvent.CompendiumChanged, is ZoneEvent.NotebookChanged -> null
      }
    }

    /** Pushed only for these, and only when the master is away. */
    val STOPPED_KINDS = setOf("WORK_PAUSED", "QUEUE_FINISHED")
    val FIGHT_KINDS = setOf("ATTACKED", "RETREATED")

    private val STOPPED_BECAUSE = mapOf(
      "STATION_OUT" to "Its fire went out.",
      "CANNOT_CRAFT" to "It cannot make the next one.",
      "CHEST_FULL" to "The chest is full.",
      "NO_ORE" to "There is no ore left in reach.",
    )

    private fun bestiaHappening(event: ZoneEvent.BestiaHappening): Notification? {
      val who = event.bestiaName ?: event.species
      val bestiaPage = "/m/${event.masterId}/bestia"
      val tag = "bestia-${event.playerBestiaId}"

      return when (event.kind) {
        "WORK_PAUSED" -> Notification(
          "$who stopped working", STOPPED_BECAUSE[event.reason] ?: "It cannot go on by itself.", "/m/${event.masterId}/diary", tag,
        )
        "QUEUE_FINISHED" -> Notification(
          "Queue finished", "$who finished the queue${event.place?.let { " at the ${stationName(it)}" } ?: ""}.",
          "/m/${event.masterId}/workshop", "queue-${event.playerBestiaId}",
        )
        "ATTACKED" -> Notification("$who is under attack", "It fights back as its courage allows.", bestiaPage, tag)
        "RETREATED" -> Notification("$who retreated", "It was losing the fight and went home.", bestiaPage, tag)
        else -> null
      }
    }

    private fun stationName(kind: String): String = kind.lowercase().replace('_', ' ')

    private fun journeyEnded(event: ZoneEvent.JourneyEnded): Notification {
      val who = event.bestiaName ?: event.species
      val (title, body) = when (event.failure) {
        null -> "$who has arrived" to (event.journeyName ?: "It is where you sent it.")
        "NO_ROUTE" -> "$who knows no way on" to "Every way it knows of is blocked."
        "DIED" -> "$who died on the way" to "It lies where it fell until you return."
        else -> "$who is stuck" to "It found no way on and stopped."
      }

      return Notification(title, body, "/m/${event.masterId}/map", "journey-${event.playerBestiaId}")
    }
  }
}
