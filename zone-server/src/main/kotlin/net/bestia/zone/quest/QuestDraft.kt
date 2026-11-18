package net.bestia.zone.quest

import java.time.Instant
import net.bestia.zone.text.TextRef

/** What a source hands [QuestService.create]. The quest is its data; there is no definition behind it. */
data class QuestDraft(
  val origin: QuestOrigin,
  val title: TextRef,
  val suggestedLevel: Int,
  val deadline: Instant,
  val objectives: List<QuestObjective>,
  val rewards: List<QuestReward>,
  val poster: Poster? = null,
) {

  data class Poster(val masterId: Long, val name: String)
}
