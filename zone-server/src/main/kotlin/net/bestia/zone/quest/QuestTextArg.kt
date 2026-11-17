package net.bestia.zone.quest

/**
 * A value for a quest's title template, typed so the client names items and species in its own language. Only
 * [Kind.NAME] carries text that is shown as it is: a town or a player.
 */
data class QuestTextArg(
  val name: String,
  val kind: Kind,
  val value: String,
) {

  enum class Kind {
    NUMBER,
    ITEM,
    SPECIES,
    TOKEN,
    NAME,
  }

  companion object {
    fun number(name: String, value: Long): QuestTextArg {
      return QuestTextArg(name, Kind.NUMBER, value.toString())
    }

    fun item(name: String, itemId: Long): QuestTextArg {
      return QuestTextArg(name, Kind.ITEM, itemId.toString())
    }
  }
}
