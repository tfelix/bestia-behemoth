package net.bestia.login.zone

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/** Points the scenarios' login server at one recording zone instead of the configured ones. */
@TestConfiguration(proxyBeanMethods = false)
class ZoneStubConfiguration {

  @Bean(destroyMethod = "close")
  fun zoneStub(): ZoneStub {
    return ZoneStub()
  }

  @Bean
  @Primary
  fun stubZoneDirectory(zoneStub: ZoneStub): ZoneDirectory {
    return ZoneDirectory { listOf(zoneStub.endpoint) }
  }
}
