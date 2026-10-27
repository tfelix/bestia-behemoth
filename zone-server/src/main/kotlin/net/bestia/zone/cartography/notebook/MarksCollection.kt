package net.bestia.zone.cartography.notebook

import net.bestia.zone.notebook.NotebookCollection
import org.springframework.stereotype.Component

/** The points a master marks on their own map, in map metres. Nobody else sees them. */
@Component
class MarksCollection : NotebookCollection<MarksCollection.Mark>(
  id = ID,
  type = Mark::class.java,
  scope = Scope.MASTER,
  shape = Shape.KEYED,
  maxEntries = MAX_MARKS,
  maxBodyBytes = MAX_BODY_BYTES,
  access = mapOf(Client.GAME to Access.WRITE, Client.COMPANION to Access.WRITE),
) {

  data class Mark(
    val name: String = "",
    val east: Double = 0.0,
    val north: Double = 0.0,
    val icon: String = ICONS.first(),
    val note: String? = null,
  )

  override fun validate(value: Mark): String? {
    return when {
      value.name.isBlank() || value.name.length > MAX_NAME_LENGTH -> "a name of 1 to $MAX_NAME_LENGTH characters"
      !value.east.isFinite() || !value.north.isFinite() -> "not a place"
      value.icon !in ICONS -> "no icon '${value.icon}'"
      (value.note?.length ?: 0) > MAX_NOTE_LENGTH -> "a note longer than $MAX_NOTE_LENGTH"
      else -> null
    }
  }

  companion object {
    const val ID = "marks"

    /** The pictures a mark may carry; both apps draw each one. */
    val ICONS = listOf("pin", "star", "danger", "treasure", "camp", "resource")

    const val MAX_MARKS = 200
    const val MAX_NAME_LENGTH = 32
    const val MAX_NOTE_LENGTH = 200
    private const val MAX_BODY_BYTES = 1024
  }
}
