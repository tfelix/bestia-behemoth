package net.bestia.zone.item.script

/**
 * Item scripts that come from a catalogue rather than one class per item, like the meals of `meals.yml`.
 *
 * Asked once, lazily, after boot has imported the items, so a source may resolve its item names to ids.
 */
interface ItemScriptSource {
  fun scripts(): List<ItemScript>
}
