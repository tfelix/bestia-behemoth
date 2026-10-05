package net.bestia.zone.ecs

import org.hibernate.cfg.AvailableSettings
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Installs the [TickSqlGuard] into Hibernate. `zone.sql-on-tick` is `log` or `fail`. */
@Configuration
class TickSqlGuardConfiguration {

  @Bean
  fun tickSqlGuardCustomizer(@Value("\${zone.sql-on-tick:log}") mode: String): HibernatePropertiesCustomizer {
    val guard = TickSqlGuard(failOnTick = mode == "fail")

    return HibernatePropertiesCustomizer { properties -> properties[AvailableSettings.STATEMENT_INSPECTOR] = guard }
  }
}
