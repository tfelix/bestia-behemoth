package net.bestia.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Writes the OpenAPI document to `build/openapi/companion.json`, which companion-app generates its TypeScript
 * types from (`npm run gen:api`), and checks that every resource the app names is in it.
 *
 * On the shared context like the scenario tests: a context of its own joined the push consumer group too, and took
 * half of their push events.
 */
class OpenApiDocumentTest : ApiScenarioTestBase() {

  @Autowired
  private lateinit var rest: TestRestTemplate

  @Test
  fun `the document covers the companion API and is written for the app`() {
    val response = rest.getForEntity("/v1/companion/openapi.json", String::class.java)
    assertEquals(200, response.statusCode.value())

    val document = jacksonObjectMapper().readTree(response.body)
    val schemas = document["components"]["schemas"].fieldNames().asSequence().toSet()
    for (name in APP_SCHEMAS) {
      assertTrue(name in schemas, "$name is missing from the OpenAPI schemas: $schemas")
    }

    val out = Path.of("build/openapi/companion.json")
    Files.createDirectories(out.parent)
    Files.writeString(out, jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(document))
  }

  companion object {
    /** The names companion-app/src/lib/api/model.ts refers to. */
    val APP_SCHEMAS = listOf(
      "MasterSummary", "MasterSheet", "Bestia", "Holder", "Slot", "PartyView", "Catalog", "MapMeta", "Place",
      "StanceOrder", "ChatOrder", "Preferences", "CompendiumEntry",
    )
  }
}
