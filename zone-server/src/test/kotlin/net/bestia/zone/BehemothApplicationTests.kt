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
		// The dev profile's bus and metrics port would tie the test to a running Valkey and a free port 8092.
		"bus.enabled=false",
		"metrics.port=-1",
	]
)
class BehemothApplicationTests {

	@Test
	fun `context is starting successfully`() { }

}
