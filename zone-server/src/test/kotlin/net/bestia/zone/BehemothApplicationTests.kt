package net.bestia.zone

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@TestPropertySource(properties = ["spring.datasource.url=jdbc:h2:mem:behemoth-application-tests"])
class BehemothApplicationTests {

	@Test
	fun `context is starting successfully`() { }

}
