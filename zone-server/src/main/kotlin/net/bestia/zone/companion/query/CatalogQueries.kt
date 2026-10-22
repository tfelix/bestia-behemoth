package net.bestia.zone.companion.query

import net.bestia.bus.contract.ZoneAnswer
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.item.persistence.ItemRepository
import org.springframework.stereotype.Service
import java.security.MessageDigest

/**
 * The item and bestia catalogue, whose content is imported at boot and fixed until the next. The version is a
 * digest of the content, so the api-service can cache it until it actually changes.
 */
@Service
class CatalogQueries(
  private val items: ItemRepository,
  private val bestias: BestiaCatalogue,
) {

  @Volatile
  private var cached: ZoneAnswer.CatalogData? = null

  fun catalog(): ZoneAnswer.CatalogData {
    return cached ?: build().also { cached = it }
  }

  private fun build(): ZoneAnswer.CatalogData {
    val itemRows = items.findAll().sortedBy { it.id }.map {
      ZoneAnswer.CatalogData.Item(it.id, it.identifier, it.type.name, it.weight, it.equipSlot?.name, it.stackable)
    }
    val species = bestias.all().sortedBy { it.id }.map {
      ZoneAnswer.CatalogData.Species(
        it.id,
        it.identifier,
        it.kind.name,
        it.element.name,
        it.level,
        it.habitat.split(',').filter { biome -> biome.isNotEmpty() },
        it.inCompendium,
      )
    }

    return ZoneAnswer.CatalogData(version(itemRows, species), itemRows, species)
  }

  private fun version(items: List<ZoneAnswer.CatalogData.Item>, species: List<ZoneAnswer.CatalogData.Species>): String {
    val digest = MessageDigest.getInstance("SHA-256").digest((items.toString() + species.toString()).toByteArray())
    return digest.take(8).joinToString("") { "%02x".format(it) }
  }
}
