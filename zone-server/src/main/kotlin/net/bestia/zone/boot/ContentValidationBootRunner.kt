package net.bestia.zone.boot

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import net.bestia.zone.util.CatalogValidator

/**
 * Checks the static content against the code once every importer and binder has run, and before the zone
 * accepts a login ([ZoneReadyBootRunner]) or starts its tick. A failure stops the boot.
 */
@Component
@Order(200)
class ContentValidationBootRunner(
  private val validators: List<CatalogValidator>,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    validators.forEach { it.validate() }

    LOG.info { "Content passed ${validators.size} check(s)" }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
