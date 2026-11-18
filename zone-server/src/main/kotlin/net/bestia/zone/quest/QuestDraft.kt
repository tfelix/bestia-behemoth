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

  init {
    val stages = objectives.map { it.stage }.toSortedSet()
    require(stages == (0 until stages.size).toSortedSet()) { "Stages must count up from 0 without a gap: $stages" }
    // A master moves on when its own counts are met. One shared counter would have to move every signer at once.
    require(stages.size == 1 || objectives.all { it.scope == ObjectiveScope.PERSONAL }) {
      "A quest with stages takes personal objectives only"
    }
    objectives.filter { it.kind == ObjectiveKind.EXAMINE || it.kind == ObjectiveKind.TALK }.forEach {
      require(it.target != null && it.text != null && it.scope == ObjectiveScope.PERSONAL) {
        "$it needs a target and a text, and is personal"
      }
    }
  }


  data class Poster(val masterId: Long, val name: String)
}
