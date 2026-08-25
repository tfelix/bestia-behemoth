package net.bestia.login.scenario

import net.bestia.login.TestcontainersConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * Pinned off `dev` on purpose. Scenarios assert what an ordinary deployment does, and the `dev`
 * profile that application.yml activates by default is not that: it hands every registration a
 * raised `account.sign-up-role`.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@ActiveProfiles("test")
@Transactional
abstract class BaseLoginScenario
