package net.bestia.zone.message

/**
 * Wraps an unexpected exception thrown by an [InMessageProcessor.IncomingMessageHandler].
 *
 * This is fatal for the connection: an [IncomingMessageHandler] failing with an exception (as
 * opposed to returning `false`) means the client sent a message the server could not process in
 * a way it knows how to recover from, so the session is torn down rather than left hanging.
 * [errorCode] is generated where the original exception is caught and logged, then carried all
 * the way to the client-facing disconnect notice so a user-reported code can be grepped straight
 * back to the matching server-side stack trace.
 */
class MessageHandlingFailedException(
  val errorCode: String,
  cause: Throwable
) : RuntimeException("Unhandled error while processing a client message [errorCode=$errorCode]", cause)
