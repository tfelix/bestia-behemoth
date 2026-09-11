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

  /**
   * A translation key the client resolves in turn and substitutes.
   *
   * What lets a template say "{who} spoke of {what}" where `{what}` is a localized word rather than an
   * English one. Without it, any composed phrase has to be either one key per combination or an English
   * fragment glued together on the server.
   */
  data class Token(val key: String) : DialogArg

  /**
   * A proper noun, already rendered.
   *
   * Passing the text is correct here rather than a concession: generated names are built from invented
   * stems that belong to no language, so a place is called the same thing in every locale. Distinct from
   * [Text] so that a reviewer can see at a glance that no server-composed sentence has been smuggled in
   * under a type documented as a last resort.
   */
  data class Name(val value: String) : DialogArg
}
