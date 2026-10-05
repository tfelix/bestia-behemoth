package net.bestia.login.scenario

import net.bestia.login.TestcontainersConfiguration
import net.bestia.login.zone.ZoneStubConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * Pinned to `test` rather than `dev`. Scenarios assert what an ordinary deployment does, and the `dev`
 * profile is not that: it hands every registration a raised `account.sign-up-role`.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class, ZoneStubConfiguration::class)
@ActiveProfiles("test")
@Transactional
abstract class BaseLoginScenario
