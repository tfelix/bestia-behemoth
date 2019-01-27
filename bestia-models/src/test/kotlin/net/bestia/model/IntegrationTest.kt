package net.bestia.model

import org.junit.jupiter.api.Tag
import org.junit.runner.RunWith
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner


/**
 * As classes can not be null we sadly have to introduce a placeholder for the
 * ActorComponent annotation if we don't want to handle any component when
 * we don't want to loose type safety.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@RunWith(SpringRunner::class)
@SpringBootTest
@DataJpaTest
@Tag("it")
annotation class IntegrationTest