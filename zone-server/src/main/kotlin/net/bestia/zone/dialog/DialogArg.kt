package net.bestia.zone.dialog

import net.bestia.zone.util.EntityId

/**
 * One placeholder value filled into a dialog's translated text by the client.
 *
 * These are typed instead of pre-rendered strings on purpose: item names, skill names and entity
 * names are themselves localized *on the client*, so sending the reference keeps them translatable.
 * Reach for [Text] only when the value has no client-side representation at all - another player's
 * master name being the usual case.
 */
sealed interface DialogArg {

  /** A string the client cannot derive itself, e.g. a player-chosen master name. */
  data class Text(val value: String) : DialogArg

  data class Number(val value: Long) : DialogArg

  /** Resolved by the client to that entity's display name; falls back to a placeholder if unknown. */
  data class Entity(val entityId: EntityId) : DialogArg

  /** Resolved by the client through its item DB, so the item name stays localized. */
  data class Item(val itemId: Long) : DialogArg

  /** Resolved by the client through its attack DB, so the skill name stays localized. */
  data class Skill(val skillId: Long) : DialogArg
}
