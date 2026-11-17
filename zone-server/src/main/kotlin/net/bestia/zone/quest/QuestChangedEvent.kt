package net.bestia.zone.quest

/** A quest changed for everyone who has it in their log. Published inside the writing transaction. */
data class QuestChangedEvent(val questId: Long)
