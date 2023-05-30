package io.scalac.auction.controller

import io.scalac.auction.services.LotService
import akka.http.scaladsl.server.{ PathMatcher1, Route }
import akka.http.scaladsl.server.Directives._
import akka.util.Helpers.base64chars
import io.scalac.auction.{ LotId, UserId }
import io.scalac.auction.controller.LotController.LotIdMatcher
import io.scalac.auction.controller.codecs.CommonCodecs.errorResponseEncoder
import io.scalac.auction.controller.codecs.LotCodecs._
import io.scalac.auction.controller.codecs.BidCodecs._
import io.scalac.auction.controller.requests.{ BidLotRequest, CreateLotRequest }

import scala.concurrent.ExecutionContextExecutor

class LotController(service: LotService)(implicit ec: ExecutionContextExecutor)
    extends CustomMarshallers
    with CustomUnmarshallers
    with SecureController {

  private val createLot: UserId => Route = userId =>
    (post & entity(as[CreateLotRequest])) { req =>
      onSuccess(service.createLot(req.auctionId, userId, req.startBid)) { result =>
        complete(result.lotOrFailure.map(_.toResponse))
      }
    }

  private val getLot: Route =
    (get & parameters("id".as[LotId])) { lotId =>
      onSuccess(service.getLot(lotId)) { result =>
        complete(result.lotOrNotFound.map(_.toResponse))
      }
    }

  private val bidLot: (UserId, LotId) => Route = (userId, lotId) =>
    (patch & entity(as[BidLotRequest])) { req =>
      onSuccess(service.bidLot(lotId, userId, req.value, req.isMax)) { result =>
        complete(result.bidOrFailure.map(_.toResponse))
      }
    }

  val routes: Route =
    authenticateOAuth2Async("lots site", userPassAuthenticator) { userId =>
      pathPrefix("lots") {
        pathEndOrSingleSlash {
          createLot(userId)
        } ~
          pathPrefix(LotIdMatcher) { lotId =>
            bidLot(userId, lotId)
          } ~
          path("view")(getLot)
      }
    }
}

object LotController {

  val LotIdMatcher: PathMatcher1[LotId] =
    _regex2PathMatcher(raw"""^[$base64chars]+$$""".r).map(LotId.apply)
}
