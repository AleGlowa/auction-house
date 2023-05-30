package io.scalac.auction.controller

import akka.http.scaladsl.server.directives.Credentials
import io.scalac.auction.JwtSecret
import io.scalac.auction.UserId
import pdi.jwt.{ JwtAlgorithm, JwtArgonaut }
import io.scalac.auction.controller.codecs.CommonCodecs.userIdCodec

import scala.concurrent.{ ExecutionContextExecutor, Future }

trait SecureController {

  protected def userPassAuthenticator(
      credentials: Credentials
  )(implicit ec: ExecutionContextExecutor): Future[Option[UserId]] =
    credentials match {
      case Credentials.Provided(token) =>
        Future {
          if (JwtArgonaut.isValid(token, JwtSecret, Seq(JwtAlgorithm.HS256)))
            JwtArgonaut
              .decodeJson(token, JwtSecret, Seq(JwtAlgorithm.HS256))
              .map(json => (+json --\ "userId") flatMap (_.focus.jdecode[UserId].toOption))
              .toOption
              .flatten
          else
            None
        }

      case _ =>
        Future.successful(None)
    }
}
