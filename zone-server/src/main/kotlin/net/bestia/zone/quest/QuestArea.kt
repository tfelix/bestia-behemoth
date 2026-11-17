package net.bestia.zone.quest

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.place.ecs.PlaceRef

/** Where a kill must happen to count. Both checks are one compare, so they run only after the species matched. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
  JsonSubTypes.Type(QuestArea.InPlace::class, name = "place"),
  JsonSubTypes.Type(QuestArea.Around::class, name = "around"),
)
sealed interface QuestArea {

  /** @param killerPlace where the credited master stands, as the zone already keeps it per entity */
  fun contains(killerPlace: PlaceRef?, killedAt: Vec3L): Boolean

  /** A named place such as a town: matched against the master's own place, so no geometry runs at kill time. */
  data class InPlace(val placeName: String) : QuestArea {
    override fun contains(killerPlace: PlaceRef?, killedAt: Vec3L): Boolean {
      return killerPlace?.name == placeName
    }
  }

  /** A spot with no name of its own, such as a battlefield: matched against where the bestia died. */
  data class Around(val x: Long, val y: Long, val radius: Long) : QuestArea {
    override fun contains(killerPlace: PlaceRef?, killedAt: Vec3L): Boolean {
      val dx = killedAt.x - x
      val dy = killedAt.y - y

      return dx * dx + dy * dy <= radius * radius
    }
  }
}
