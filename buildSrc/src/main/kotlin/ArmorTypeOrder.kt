import org.gradle.api.GradleException

/**
 * The client stores armor types by `net.bestia.zone.item.equip.ArmorType` ordinal, so this list must mirror
 * that enum's declaration order exactly. Shared by [BestiaDbSyncTask] and [ItemDbSyncTask].
 */
object ArmorTypeOrder {
  private val NAMES = listOf("CLOTH", "LIGHT", "MEDIUM", "HEAVY")

  fun ordinalOf(name: String, owner: String): Int {
    val ordinal = NAMES.indexOf(name.uppercase())
    if (ordinal < 0) {
      throw GradleException("Unknown armor type '$name' for $owner. Known: $NAMES")
    }
    return ordinal
  }
}
