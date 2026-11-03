package net.bestia.zone.honor

import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/** The shipped `honor.yml`, read once when the context starts so a broken file stops the boot. */
@Component
class HonorCatalog {
  val rules: HonorRules = ClassPathResource(HonorRules.RESOURCE).inputStream.use { HonorRules.read(it) }
}
