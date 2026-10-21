package net.bestia.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Writes the OpenAPI document to `build/openapi/companion.json`, which companion-app generates its TypeScript
 * types from (`npm run gen:api`), and checks that every resource the app names is in it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ApiTestContainers::class)
@ActiveProfiles("test")
class OpenApiDocumentTest {

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
      "StanceOrder", "ChatOrder", "Preferences",
    )

    @JvmStatic
    @DynamicPropertySource
    fun bus(registry: DynamicPropertyRegistry) {
      registry.add("bus.host") { ApiTestContainers.valkey.host }
      registry.add("bus.port") { ApiTestContainers.valkey.getMappedPort(6379) }
    }
  }
}
