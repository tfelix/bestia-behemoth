package net.bestia.zoneserver

import org.junit.jupiter.api.Tag
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest
@ActiveProfiles(profiles = ["integration"])
@Tag("integration")
@Import(TestZoneConfiguration::class)
annotation class IntegrationTest