package net.bestia.zone.prose

import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.core.FigureRecord
import net.bestia.worldgen.core.Sex
import net.bestia.worldgen.dress.FixtureChannels
import net.bestia.worldgen.dress.FixtureKind
import net.bestia.worldgen.render.optionalAttribute
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.zone.text.TextBrief
import net.bestia.zone.text.TextBrief.AllowedName
import net.bestia.zone.text.TextSource
import net.bestia.zone.text.TextSubject
import net.bestia.zone.townsfolk.knowledge.ChronicleNames
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component
import java.util.Locale
import java.util.zip.CRC32

/** An epitaph for every figure of the chronicle with a named grave in the world. */
@Component
class EpitaphSource(private val worldService: WorldService) : TextSource {

  override fun wanted(): List<TextSource.Wanted> {
    return buriedFigures().sorted().map { TextSource.Wanted(TextSubject.grave(it), PRIORITY) }
  }

  override fun briefOf(subject: String): TextBrief? {
    val chronicle = chronicle()
    val figure = TextSubject.figureOfGrave(subject)?.let(chronicle.figures::getOrNull) ?: return null
    val name = ChronicleNames.figureOf(chronicle, figure.index) ?: return null
    val people = ChronicleNames.civOf(chronicle, figure.civ)
    val home = ChronicleNames.placeOf(chronicle, figure.homeSettlement)
    val role = figure.role.name.lowercase(Locale.ROOT)
    val who = listOfNotNull(role, people?.let { "of the $it" }).joinToString(" ")

    return TextBrief(
      task = "Write the epitaph ${framingOf(subject)} of $name. 2 to 5 short lines, 20 to 50 words in total. Name the " +
        "dead, the years of birth and death, and one deed or tie from the FACT SHEET. Solemn tone.",
      allowedNames = listOfNotNull(
        AllowedName(name, "$who, a ${sexOf(figure)}, ${figure.birthYear}-${figure.deathYear}"),
        people?.let { AllowedName(it, "a people") },
        home?.let { AllowedName(it, "a town, the home of $name") },
      ),
      facts = listOfNotNull(
        "Subject: $name, $who",
        "born: ${figure.birthYear}",
        "died: ${figure.deathYear}",
        home?.let { "home: $it" },
      ) + deedsOf(chronicle, figure),
      mustName = listOf(name),
    )
  }

  /** The events that matter most in the figure's life, in the order they happened. */
  private fun deedsOf(chronicle: Chronicle, figure: FigureRecord): List<String> {
    return chronicle.eventsOf(Actor(ActorType.FIGURE, figure.index))
      .sortedByDescending { it.importance }
      .take(MAX_DEEDS)
      .sortedWith(compareBy({ it.year }, { it.id }))
      .map { "${it.year}: ${it.detail}" }
  }

  /** The same framing on every run, so a text that is written again reads as the same stone. */
  private fun framingOf(subject: String): String {
    val pick = CRC32().apply { update(subject.toByteArray()) }.value % FRAMINGS.size

    return FRAMINGS[pick.toInt()]
  }

  private fun sexOf(figure: FigureRecord): String {
    return if (figure.sex == Sex.FEMALE) "woman" else "man"
  }

  private fun buriedFigures(): List<Int> {
    return worldService.generated.world.features.all()
      .filterIsInstance<PointMarker>()
      .filter { it.kind == FeatureKind.FIXTURE && it.optionalAttribute(FixtureChannels.KIND)?.toInt() == FixtureKind.NAMED_GRAVE.ordinal }
      .map { it.attribute(FixtureChannels.FIGURE).toInt() }
  }

  private fun chronicle(): Chronicle {
    return worldService.generated.world.chronicle
  }

  private companion object {
    /** After the quest texts: an epitaph is found by walking, a quest text is met at once. */
    const val PRIORITY = 20

    /** More lines than this and a small model lists the life, where a stone names one deed. */
    const val MAX_DEEDS = 6

    /** Without a choice of framings a small model opens every text the same way. */
    val FRAMINGS = listOf(
      "carved on the grave",
      "cut into the stone above the grave",
      "written on a weathered marker by the grave",
    )
  }
}
