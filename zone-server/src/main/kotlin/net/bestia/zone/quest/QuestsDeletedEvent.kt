package net.bestia.zone.quest

/** Quests that ended long ago are about to be deleted. Published inside the deleting transaction, so sources go too. */
data class QuestsDeletedEvent(val questIds: List<Long>)
