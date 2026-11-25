package net.bestia.zone.worldevent.rare

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class RareBestiaCatalogueTest {

  private val catalogue = RareBestiaCatalogue().apply { load() }

  @Test
  fun `every rare is an event-only species, so nothing else places it`() {
    val mobs = File("src/main/resources/mob").listFiles()!!.map { it.readText() }

    catalogue.all().forEach { rare ->
      val mob = mobs.firstOrNull { "\nidentifier: ${rare.bestia}\n" in it }
      assertTrue(mob != null, "${rare.id} names ${rare.bestia}, which no mob file defines")
      assertTrue("\nevent-only: true\n" in mob!!, "${rare.bestia} must be event-only")
    }
  }

  @Test
  fun `every rare has a hint the townsfolk can say`() {
    val dialogs = File("../bestia-client/src/Localization/dialogs.csv").readText()

    catalogue.all().forEach { assertTrue("\n${it.hintKey}," in dialogs, "dialogs.csv has no ${it.hintKey}") }
  }
}
