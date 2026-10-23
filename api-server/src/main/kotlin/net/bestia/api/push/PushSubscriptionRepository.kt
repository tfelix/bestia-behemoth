package net.bestia.api.push

import org.springframework.data.jpa.repository.JpaRepository

interface PushSubscriptionRepository : JpaRepository<PushSubscription, Long> {

  fun findAllByAccountId(accountId: Long): List<PushSubscription>

  fun findByEndpointHash(endpointHash: String): PushSubscription?
}
