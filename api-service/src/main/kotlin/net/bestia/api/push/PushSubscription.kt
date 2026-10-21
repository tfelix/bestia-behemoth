package net.bestia.api.push

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/** One browser or installed app that allowed notifications, with the keys its messages are encrypted to. */
@Entity
@Table(name = "push_subscription")
class PushSubscription(
  @Column(name = "account_id", nullable = false)
  val accountId: Long,

  @Column(nullable = false, length = 1024)
  val endpoint: String,

  @Column(name = "endpoint_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
  val endpointHash: String,

  @Column(nullable = false, length = 128)
  val p256dh: String,

  @Column(nullable = false, length = 64)
  val auth: String,

  @Column(name = "created_at", nullable = false)
  val createdAt: LocalDateTime = LocalDateTime.now(),
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  @Column(name = "last_ok_at", nullable = true)
  var lastOkAt: LocalDateTime? = null
}
