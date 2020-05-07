package net.bestia.loginserver.login

import net.bestia.loginserver.error.BestiaException
import net.bestia.loginserver.error.BestiaExceptionCode

object AuthenticationException : BestiaException(BestiaExceptionCode.AUTH_FAILED)