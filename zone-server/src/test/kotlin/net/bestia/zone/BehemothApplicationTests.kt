package net.bestia.zone

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@TestPropertySource(
	properties = [
		"spring.datasource.url=jdbc:h2:mem:behemoth-application-tests",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"zone.jwt-auth-secret-key=behemoth-application-test-secret-long-enough",
	]
)
class BehemothApplicationTests {

	@Test
	fun `context is starting successfully`() { }

}
