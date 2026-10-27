package net.bestia.zone.cartography.notebook

import net.bestia.zone.notebook.NotebookCollection
import org.springframework.stereotype.Component

/** How a master likes their map: which service kinds it hides, and whether it draws the grid. */
@Component
class MapPrefsCollection : NotebookCollection<MapPrefsCollection.MapPrefs>(
  id = ID,
  type = MapPrefs::class.java,
  scope = Scope.MASTER,
  shape = Shape.SINGLE,
  maxEntries = 1,
  maxBodyBytes = MAX_BODY_BYTES,
  access = mapOf(Client.GAME to Access.WRITE, Client.COMPANION to Access.WRITE),
) {

  data class MapPrefs(val hiddenServiceKinds: List<String> = emptyList(), val gridOverlay: Boolean = false)

  override fun validate(value: MapPrefs): String? {
    return if (value.hiddenServiceKinds.size > MAX_KINDS) "more hidden kinds than there are" else null
  }

  companion object {
    const val ID = "map-prefs"
    private const val MAX_KINDS = 32
    private const val MAX_BODY_BYTES = 1024
  }
}
