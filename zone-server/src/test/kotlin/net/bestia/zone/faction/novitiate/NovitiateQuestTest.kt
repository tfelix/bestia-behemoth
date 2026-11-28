package net.bestia.zone.faction.novitiate

import net.bestia.worldgen.core.Faction
import net.bestia.zone.faction.FactionConfig
import net.bestia.zone.faction.envoy.EnvoyStand
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.quest.ObjectiveKind
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class NovitiateQuestTest {

  private val quest = NovitiateQuest(FactionConfig(minSwearLevel = 40, novitiateOfferings = 3))
  private val now = Instant.parse("2026-11-28T12:00:00Z")

  private fun stand(faction: Faction, settlement: Int, town: String): EnvoyStand {
    return EnvoyStand(faction, settlement, town, Vec3L(settlement * 1000L, 0, 0))
  }

  private fun draftFor(faction: Faction) = quest.draft(
    faction,
    home = stand(faction, 1, "Ashfen"),
    pilgrimage = stand(faction, 2, "Kellmoor"),
    rivals = Faction.entries.filter { it != faction }.map { stand(it, 1, "Ashfen") },
    offeringItemId = 6,
    now = now,
  )

  @Test
  fun `Eternity asks for mana crystals at its own Envoy, then the pilgrimage, then the oath`() {
    val objectives = draftFor(Faction.ETERNITY).objectives

    assertEquals(listOf(ObjectiveKind.BRING, ObjectiveKind.TALK, ObjectiveKind.DECIDE), objectives.map { it.kind })
    assertEquals(listOf(0, 1, 2), objectives.map { it.stage })
    assertEquals(3, objectives[0].required)
    assertEquals(6L, objectives[0].itemId)
    assertEquals(stand(Faction.ETERNITY, 1, "Ashfen").target, objectives[0].target)
    assertEquals(stand(Faction.ETERNITY, 2, "Kellmoor").target, objectives[1].target)
  }

  @Test
  fun `the Circle hears both rival Envoys of its home town`() {
    val proof = draftFor(Faction.CIRCLE).objectives.filter { it.stage == NovitiateQuest.PROOF }

    assertEquals(listOf(ObjectiveKind.TALK, ObjectiveKind.TALK), proof.map { it.kind })
    assertEquals(
      setOf(stand(Faction.CHAOS, 1, "Ashfen").target, stand(Faction.ETERNITY, 1, "Ashfen").target),
      proof.mapNotNull { it.target }.toSet(),
    )
  }

  @Test
  fun `the oath offers to swear or to withdraw`() {
    val oath = draftFor(Faction.CHAOS).objectives.single { it.kind == ObjectiveKind.DECIDE }

    assertEquals(listOf(NovitiateQuest.SWEAR, NovitiateQuest.WITHDRAW), oath.choices.map { it.id })
  }
}
