package net.bestia.zone.prose

import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.questgen.lostRelicPlan
import net.bestia.zone.questgen.persistence.StoredQuestPlan
import net.bestia.zone.questgen.persistence.StoredQuestPlanRepository
import net.bestia.zone.questgen.text.QuestTextSource
import net.bestia.zone.text.TextSubject
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The briefs the writer is handed, built from the generated test world. */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class TextSourcesTest {

  @Autowired
  private lateinit var epitaphs: EpitaphSource

  @Autowired
  private lateinit var questTexts: QuestTextSource

  @Autowired
  private lateinit var plans: StoredQuestPlanRepository

  @Autowired
  private lateinit var worldService: WorldService

  private var planId: Long? = null

  @AfterEach
  fun forget() {
    planId?.let(plans::deleteById)
  }

  @Test
  fun `every named grave wants an epitaph that names its dead and uses only what the chronicle says`() {
    val wanted = epitaphs.wanted()
    assertTrue(wanted.isNotEmpty(), "the test world has no named grave")

    val subject = wanted.first().subject
    val figure = worldService.generated.world.chronicle.figures[TextSubject.figureOfGrave(subject)!!]
    val brief = assertNotNull(epitaphs.briefOf(subject))

    assertEquals(1, brief.mustName.size)
    assertTrue(brief.mustName.single() in brief.task)
    assertTrue(brief.facts.any { it == "born: ${figure.birthYear}" } && brief.facts.any { it == "died: ${figure.deathYear}" })
    assertEquals(brief.task, epitaphs.briefOf(subject)!!.task, "a text keeps its framing when it is written again")
    assertNull(epitaphs.briefOf(TextSubject.questOffer(1)), "another source's subject is not this one's")
    println("EPITAPH BRIEF\n${brief.task}\n${brief.allowedNames}\n${brief.facts.joinToString("\n")}")
  }

  @Test
  fun `a quest plan wants its giver's tale and a journal line per step, each with its lead`() {
    // Artifact 23 is lost in the test world, so the chronicle has its story.
    val id = plans.save(StoredQuestPlan(lostRelicPlan(artifact = 23, WorldTarget.townsfolk(1), home = 6))).id
    planId = id

    val mine = questTexts.wanted().map { it.subject }.filter { TextSubject.questPartsOf(it)?.first == id }
    assertTrue(TextSubject.questOffer(id) in mine && TextSubject.questStep(id, 1) in mine, mine.toString())
    assertTrue(TextSubject.questStep(id, 2) !in mine, "a decide step has nothing to tell")

    val offer = assertNotNull(questTexts.briefOf(TextSubject.questOffer(id)))
    assertTrue("Ashbrand" in offer.mustName && "Hale" in offer.mustName, "the giver names the artifact and the lead: ${offer.mustName}")
    assertTrue(offer.task.endsWith("End by telling the traveller to dig for Ashbrand near Hale, where it was lost."), offer.task)

    val dig = assertNotNull(questTexts.briefOf(TextSubject.questStep(id, 1)))
    assertEquals(listOf("Ashbrand", "Hale"), dig.mustName)
    assertTrue(dig.facts.size > 1, "the artifact's story is in the facts: ${dig.facts}")
    println("QUEST BRIEF\n${offer.task}\n${offer.allowedNames}\n${offer.facts.joinToString("\n")}")
  }
}
