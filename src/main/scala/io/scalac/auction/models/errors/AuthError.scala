package io.scalac.auction.models.errors

import io.scalac.auction.UserId
import io.scalac.auction.models.errors.CommonErrors.UnauthorizedError

sealed trait AuthError extends DomainError

object AuthError {

  final case class UserNotRegistered(userId: UserId) extends AuthError with UnauthorizedError {
    override def message: String = s"User ${userId.show} isn't registered"
  }
}
