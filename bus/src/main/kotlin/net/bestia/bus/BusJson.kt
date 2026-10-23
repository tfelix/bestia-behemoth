package net.bestia.bus

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule

/**
 * The one JSON mapping both ends of the bus use. Its own instance rather than an application's, so a Spring
 * setting changed for a REST API cannot change what the other service reads.
 */
object BusJson {

  val mapper: ObjectMapper = ObjectMapper()
    .registerModule(KotlinModule.Builder().enable(KotlinFeature.SingletonSupport).build())
    // A newer zone may add a field before the api-server knows it; that must not break the older reader.
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    // But a field the reader needs and did not get is an error, not a zero: a position that quietly reads 0.0
    // looks like an answer. That is how `xMetres` once failed, written as `xmetres` by its getter.
    .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
}
