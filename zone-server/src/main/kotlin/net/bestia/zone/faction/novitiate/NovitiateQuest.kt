package net.bestia.zone.faction.novitiate

import net.bestia.worldgen.core.Faction
import net.bestia.zone.faction.FactionConfig
import net.bestia.zone.faction.envoy.EnvoyStand
import net.bestia.zone.quest.ObjectiveKind
import net.bestia.zone.quest.ObjectiveScope
import net.bestia.zone.quest.QuestDraft
import net.bestia.zone.quest.QuestObjective
import net.bestia.zone.quest.QuestOrigin
import net.bestia.zone.text.TextArg
import net.bestia.zone.text.TextRef
import java.time.Duration
import java.time.Instant

/**
 * The quest a master walks to join an Order: a proof in the Order's spirit, a pilgrimage to its Envoy in another
 * town, and the oath. Eternity binds mana crystals, Chaos has them shattered, and the Circle hears both rivals.
 */
class NovitiateQuest(private val config: FactionConfig) {

  fun draft(
    faction: Faction,
    home: EnvoyStand,
    pilgrimage: EnvoyStand,
    rivals: List<EnvoyStand>,
    offeringItemId: Long,
    now: Instant,
  ): QuestDraft {
    return QuestDraft(
      origin = QuestOrigin.ORDER,
      title = TextRef("QUEST_NOVITIATE_TITLE_${faction.name}"),
      suggestedLevel = config.minSwearLevel,
      deadline = now.plus(Duration.ofDays(config.novitiateDays)),
      objectives = proofOf(faction, home, rivals, offeringItemId) + pilgrimageTo(pilgrimage) + oath(),
      rewards = emptyList(),
      issuer = home.townName,
    )
  }

  private fun proofOf(faction: Faction, home: EnvoyStand, rivals: List<EnvoyStand>, offeringItemId: Long): List<QuestObjective> {
    return when (faction) {
      Faction.ETERNITY -> listOf(offering("QUEST_NOVITIATE_BIND", home, offeringItemId))
      Faction.CHAOS -> listOf(offering("QUEST_NOVITIATE_SHATTER", home, offeringItemId))
      Faction.CIRCLE -> rivals.map { rival ->
        talk(PROOF, rival, TextRef("QUEST_NOVITIATE_HEAR_${rival.faction.name}", townArg(rival)))
      }
    }
  }

  private fun offering(key: String, home: EnvoyStand, itemId: Long): QuestObjective {
    val amount = config.novitiateOfferings
    val args = townArg(home) + mapOf("amount" to TextArg.Number(amount.toLong()), "item" to TextArg.Item(itemId))

    return QuestObjective(
      ObjectiveKind.BRING, ObjectiveScope.PERSONAL, amount,
      itemId = itemId, stage = PROOF, target = home.target, text = TextRef(key, args),
    )
  }

  private fun pilgrimageTo(stand: EnvoyStand): QuestObjective {
    return talk(PILGRIMAGE, stand, TextRef("QUEST_NOVITIATE_PILGRIMAGE", townArg(stand)))
  }

  private fun oath(): QuestObjective {
    return QuestObjective(
      ObjectiveKind.DECIDE, ObjectiveScope.PERSONAL, 1,
      stage = OATH,
      text = TextRef("QUEST_NOVITIATE_OATH"),
      choices = listOf(
        QuestObjective.Choice(SWEAR, TextRef("QUEST_NOVITIATE_SWEAR")),
        QuestObjective.Choice(WITHDRAW, TextRef("QUEST_NOVITIATE_WITHDRAW")),
      ),
    )
  }

  private fun talk(stage: Int, stand: EnvoyStand, text: TextRef): QuestObjective {
    return QuestObjective(ObjectiveKind.TALK, ObjectiveScope.PERSONAL, 1, stage = stage, target = stand.target, text = text)
  }

  private fun townArg(stand: EnvoyStand): Map<String, TextArg> {
    return mapOf("town" to TextArg.Name(stand.townName))
  }

  companion object {
    const val PROOF = 0
    const val PILGRIMAGE = 1
    const val OATH = 2

    const val SWEAR = "swear"
    const val WITHDRAW = "withdraw"
  }
}
