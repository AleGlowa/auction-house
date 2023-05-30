package io.scalac.auction.controller

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ PathMatcher1, Route }
import akka.util.Helpers.base64chars
import io.scalac.auction.{ AuctionId, UserId }
import io.scalac.auction.services.AuctionService
import io.scalac.auction.controller.AuctionController.AuctionIdMatcher
import io.scalac.auction.controller.codecs.CommonCodecs.{ actionResponseEncoder, errorResponseEncoder }
import io.scalac.auction.controller.codecs.AuctionCodecs._
import io.scalac.auction.controller.requests.StartAuctionRequest

import scala.concurrent.ExecutionContextExecutor

class AuctionController(service: AuctionService)(implicit ec: ExecutionContextExecutor)
    extends CustomMarshallers
    with CustomUnmarshallers
    with SecureController {

  private val createAuction: UserId => Route = userId =>
    (post & requestEntityEmpty) {
      onSuccess(service.createAuction(userId)) { result =>
        complete(result.auction.toResponse)
      }
    }

  private val getAuctions: Route =
    get {
      onSuccess(service.getAuctions) { result =>
        complete(result.auctions.map(_.toResponse))
      }
    }

  private val getAuction: Route =
    (get & parameters("id".as[AuctionId])) { auctionId =>
      onSuccess(service.getAuction(auctionId)) { result =>
        complete(result.auctionOrNotFound.map(_.toResponse))
      }
    }

  private val startAuction: (AuctionId, UserId) => Route = (auctionId, userId) =>
    (post & entity(as[StartAuctionRequest])) { req =>
      onSuccess(service.startAuction(auctionId, userId, req.howLong)) { result =>
        complete(result.res.map(_.toResponse))
      }
    }

  private val endAuction: (AuctionId, UserId) => Route = (auctionId, userId) =>
    delete {
      onSuccess(service.endAuction(auctionId, userId)) { result =>
        complete(result.res.map(_.toResponse))
      }
    }

  val routes: Route =
    authenticateOAuth2Async("auctions site", userPassAuthenticator) { userId =>
      pathPrefix("auctions") {
        pathEndOrSingleSlash {
          createAuction(userId) ~ getAuctions
        } ~
          pathPrefix(AuctionIdMatcher) { auctionId =>
            pathEndOrSingleSlash(endAuction(auctionId, userId)) ~
              path("start")(startAuction(auctionId, userId))
          } ~
          path("view")(getAuction)
      }
    }
}

object AuctionController {

  val AuctionIdMatcher: PathMatcher1[AuctionId] =
    _regex2PathMatcher(raw"""^[$base64chars]+$$""".r).map(AuctionId.apply)
}
