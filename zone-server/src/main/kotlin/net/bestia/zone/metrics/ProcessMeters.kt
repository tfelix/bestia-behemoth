package net.bestia.zone.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import io.micrometer.core.instrument.binder.netty4.NettyAllocatorMetrics
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.ByteBufAllocatorMetricProvider
import org.springframework.stereotype.Component

/**
 * Netty's memory, which the JVM's own meters do not show: it lives in direct buffers, off the heap, and a leak
 * or a slow client piling up outbound data fills it while the heap looks fine. The zone's sockets and its frame
 * encoder both use the default allocator.
 */
@Component
class ProcessMeters : MeterBinder {

  override fun bindTo(registry: MeterRegistry) {
    // The pooled one unless the JVM was told otherwise; an allocator that keeps no numbers has nothing to export.
    val allocator = ByteBufAllocator.DEFAULT as? ByteBufAllocatorMetricProvider ?: return

    NettyAllocatorMetrics(allocator).bindTo(registry)
  }
}
