package net.bestia.zoneserver.messages

import java.lang.RuntimeException

class MessageConvertException(
    cause: Throwable?
) : RuntimeException(cause)