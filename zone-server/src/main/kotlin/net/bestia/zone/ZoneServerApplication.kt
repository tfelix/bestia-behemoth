package net.bestia.zone

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan("net.bestia.zone")
class ZoneServerApplication

fun main(args: Array<String>) {
	runApplication<ZoneServerApplication>(*args)
}
