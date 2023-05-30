package io.scalac.auction.services.impl

import akka.actor.typed.ActorSystem
import io.scalac.auction.infrastructure.repositories.UserRepository
import io.scalac.auction.services.AuthService
import io.scalac.auction.models.errors.AuthError.UserNotRegistered
import io.scalac.auction.models.Token
import io.scalac.auction.{ JwtExpiresIn, JwtSecret, UserId }
import pdi.jwt.{ JwtAlgorithm, JwtArgonaut, JwtClaim }

import java.time.Clock
import scala.concurrent.{ ExecutionContextExecutor, Future }

class AuthServiceImpl(repo: UserRepository)(implicit system: ActorSystem[_]) extends AuthService {

  private implicit val ec: ExecutionContextExecutor = system.executionContext
  private implicit val clock: Clock = Clock.systemUTC()

  override def generateToken(userId: UserId): Future[Either[UserNotRegistered, Token]] =
    for {
      user <- repo.getById(userId)
      res = user
        .map(_ =>
          Token(
            JwtArgonaut.encode(
              JwtClaim(s"""{"userId": ${userId.toInt}}""").issuedNow
                .expiresIn(JwtExpiresIn.getSeconds),
              JwtSecret,
              JwtAlgorithm.HS256
            )
          )
        )
        .toRight(UserNotRegistered(userId))
    } yield res
}
