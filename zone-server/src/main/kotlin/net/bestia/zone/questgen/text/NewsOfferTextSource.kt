package net.bestia.zone.questgen.text

import net.bestia.zone.questgen.QuestgenConfig
import net.bestia.zone.text.TextBrief
import net.bestia.zone.text.TextSource
import net.bestia.zone.text.TextSubject
import net.bestia.zone.townsfolk.rumour.RumourKind
import org.springframework.stereotype.Component

/**
 * What a townsperson says who asks for news to be carried on. News happens in play, long after the texts of a
 * world are written. So these are written ahead, a few per kind of news, and leave the town open as `{town}`.
 */
@Component
class NewsOfferTextSource(private val config: QuestgenConfig) : TextSource {

  override fun wanted(): List<TextSource.Wanted> {
    return FACTS.keys.flatMap { kind ->
      (0 until config.newsTextsPerKind).map { TextSource.Wanted(TextSubject.newsOffer(kind.name, it), PRIORITY) }
    }
  }

  override fun briefOf(subject: String): TextBrief? {
    val (kindName, variant) = TextSubject.newsPartsOf(subject) ?: return null
    val facts = FACTS.entries.firstOrNull { it.key.name == kindName }?.value ?: return null

    return TextBrief(
      task = "A townsperson speaks to a traveller. News has reached their town, and a town nearby has not heard " +
        "it. Write what the townsperson says: 2 to 4 sentences, 35 to 70 words, plain spoken words, no greeting. " +
        "They tell what happened, as the FACT SHEET says, and ask the traveller to carry the word to the other " +
        "town, which is $TOWN. Use no name of your own. Add nothing the sheet does not say. " +
        // The standing instruction asks for old prose. That is right for a stone, and too much for a farmer.
        "Speak in plain words, no old words like \"hath\" or \"naught\". " +
        "The speaker is ${VOICES[variant % VOICES.size]}.",
      allowedNames = emptyList(),
      facts = facts,
      placeholders = listOf(TextBrief.Placeholder(TOWN_ARG, "the name of the town the news must be carried to")),
    )
  }

  companion object {
    /** What the game fills with the town the news goes to. Not "to": a model reads that as the word. */
    const val TOWN_ARG = "town"
    const val TOWN = "{$TOWN_ARG}"

    /** After the texts of the quests that exist, before the epitaphs: this is met only once news happens. */
    private const val PRIORITY = 15

    /** What may be said about each kind of news that is carried. It must be true of every such rumour. */
    private val FACTS = mapOf(
      RumourKind.BOSS_SLAIN to listOf(
        "A great beast that people feared was killed, not far from the town of the speaker.",
        "The speaker did not see it. Others went to look and told of it.",
        "The speaker does not know who killed it.",
      ),
      RumourKind.TOWN_FELL to listOf(
        "A town not far from the town of the speaker was destroyed. No house is left standing.",
        "Some of its people got away.",
        "The speaker does not know who did it.",
      ),
    )

    /** One per variant, so the texts of one kind do not read alike. */
    private val VOICES = listOf(
      "an old farmer who speaks slowly", "a busy innkeeper", "a worried mother", "a tired guard",
      "a trader who has kin in the other town", "a young shepherd who is still shaken", "a priest who speaks calmly",
      "a smith of few words", "a fisher who heard it at the market", "a widow who has seen such things before",
    )
  }
}
