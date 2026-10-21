package net.bestia.bus.contract

import com.fasterxml.jackson.annotation.JsonTypeInfo

/** Something that happened in a zone that an account's companion app should hear about. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
sealed interface ZoneEvent {
  val accountId: Long

  /** The master's home town fell, and the master now lives in [newHome]. */
  data class HomeFell(
    override val accountId: Long,
    val masterId: Long,
    val masterName: String,
    val fallen: String,
    val newHome: String,
  ) : ZoneEvent

  /** An owned bestia died while nobody was playing its master. It stays down until the master comes back. */
  data class BestiaDiedAway(
    override val accountId: Long,
    val masterId: Long,
    val playerBestiaId: Long,
    val bestiaName: String?,
    val species: String,
  ) : ZoneEvent

  data class ConstructionDone(override val accountId: Long, val masterId: Long, val kind: String) : ZoneEvent

  data class StanceChanged(
    override val accountId: Long,
    val masterId: Long,
    val playerBestiaId: Long,
    val stance: String,
    val aggression: Int,
  ) : ZoneEvent

  /** The account entered the world with [masterId], or left it ([inWorld] false, [masterId] null). */
  data class Presence(override val accountId: Long, val masterId: Long?, val inWorld: Boolean) : ZoneEvent

  /** What [masterId] can see of the map may have changed. */
  data class ChartsChanged(override val accountId: Long, val masterId: Long) : ZoneEvent
}
