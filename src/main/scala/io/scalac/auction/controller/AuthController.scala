package io.scalac.auction.controller

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import io.scalac.auction.UserId
import io.scalac.auction.services.AuthService
import io.scalac.auction.controller.codecs.CommonCodecs.{ errorResponseEncoder, tokenResponseEncoder }

class AuthController(service: AuthService) extends CustomMarshallers with CustomUnmarshallers {

  private val getToken: Route =
    (get & parameters("userId".as[UserId])) { userId =>
      onSuccess(service.generateToken(userId)) { result =>
        complete(result.map(_.toResponse))
      }
    }

  val routes: Route =
    pathPrefix("auth") {
      pathEndOrSingleSlash(getToken)
    }
}
