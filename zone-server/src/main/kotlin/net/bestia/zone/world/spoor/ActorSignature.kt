package net.bestia.zone.world.spoor

/**
 * Who left a track, as much of it as the world can honestly answer.
 *
 * ### Held once per actor, referenced by every print
 *
 * `ColumnStamps` stores an entity id against each stamp and nothing else, so a boar crossing two hundred cells
 * costs two hundred longs and **one** of these. That is what makes identity this rich affordable at all -
 * packing species, level and a name into every print would cost more than the print does.
 *
 * ### What is here is what exists, and no more
 *
 * A wild mob carries no `Level` component - `BestiaEntitySpawner.spawnMob` never adds one - so its level comes
 * off the species row instead. There is no size, no weight and no per-creature name anywhere in the world, so
 * "which individual wolf" is a question this deliberately cannot answer. "A blob" against "something level 80"
 * it can, which is what a tracker actually wants to know.
 *
 * @property speciesId the `Bestia` row for a creature, the master id for a player
 * @property identifier the species' catalogue name, empty for a master. See [nameToken].
 * @property masterName a player's chosen name, null for anything else. Already public via
 *   `MasterVisualComponentSMSG`, so reporting it reveals nothing new - the level gate on it is about the skill
 *   feeling like a skill, not about privacy.
 */
data class ActorSignature(
  val kind: ActorKind,
  val speciesId: Long,
  val level: Int,
  val identifier: String,
  val masterName: String?,
) {

  /**
   * The translation key naming this species, or null for a master - whose name is not a key.
   *
   * The client already carries these rows (`BESTIA_BLOB` and friends in `general.csv`), so a reading names a
   * creature in the player's own language without any text crossing the wire.
   */
  val nameToken: String?
    get() = if (kind == ActorKind.BESTIA && identifier.isNotEmpty()) {
      "BESTIA_${identifier.uppercase()}"
    } else {
      null
    }
}

enum class ActorKind {
  MASTER,
  BESTIA,
}
