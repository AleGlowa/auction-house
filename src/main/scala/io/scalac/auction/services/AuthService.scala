package io.scalac.auction.services

import io.scalac.auction.models.Token
import io.scalac.auction.UserId
import io.scalac.auction.models.errors.AuthError.UserNotRegistered

import scala.concurrent.Future

trait AuthService {

  def generateToken(userId: UserId): Future[Either[UserNotRegistered, Token]]
}
