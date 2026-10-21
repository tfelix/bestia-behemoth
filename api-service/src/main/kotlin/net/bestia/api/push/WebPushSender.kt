package net.bestia.api.push

/** Hands one encrypted message to the push service behind a subscription. */
fun interface WebPushSender {

  enum class Outcome {
    DELIVERED,

    /** The subscription is gone (404/410): the browser unsubscribed or the app was removed. Delete it. */
    GONE,

    FAILED,
  }

  fun send(subscription: PushSubscription, payload: ByteArray): Outcome
}
