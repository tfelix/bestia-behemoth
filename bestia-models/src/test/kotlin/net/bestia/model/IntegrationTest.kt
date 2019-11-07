package net.bestia.model

import org.junit.jupiter.api.Tag
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@DataJpaTest
@Tag("it")
annotation class IntegrationTest