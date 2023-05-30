package io.scalac.auction.services.impl

import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.util.Timeout
import io.scalac.auction.actors.AuctionManagerActor
import io.scalac.auction.actors.AuctionManagerActor._
import io.scalac.auction.models.Auction
import io.scalac.auction.services.AuctionService
import io.scalac.auction.{ AuctionId, UserId, getRandomBase64 }

import scala.concurrent.duration.{ DurationInt, FiniteDuration }
import scala.concurrent.{ ExecutionContext, Future }

class AuctionServiceImpl(auctionManager: EntityRef[AuctionManagerActor.Command])(implicit ec: ExecutionContext)
    extends AuctionService {

  private implicit val timeout: Timeout = Timeout(5.seconds)

  override def createAuction(creatorId: UserId): Future[CreatedAuction] = {
    def getRandomNonExistentId(auctionId: AuctionId): Future[AuctionId] =
      for {
        auctionOrNotFound <- auctionManager
          .ask(GetAuction(auctionId, _, excludeIsDeleted = false))
          .map(_.auctionOrNotFound)
        res <- auctionOrNotFound.swap
          .fold(
            _ => getRandomNonExistentId(AuctionId(getRandomBase64(500_000, 1_000_000))),
            _ => Future.successful(auctionId)
          )
      } yield res
    for {
      auctionId <- getRandomNonExistentId(AuctionId(getRandomBase64(500_000, 1_000_000)))
      res <- auctionManager ? (CreateAuction(Auction(auctionId, creatorId), _))
    } yield res
  }

  override def getAuctions: Future[GotAuctions] =
    auctionManager ? GetAuctions.apply

  override def getAuction(auctionId: AuctionId): Future[GotAuctionOrNotFound] =
    auctionManager ? (GetAuction(auctionId, _))

  override def startAuction(auctionId: AuctionId, userId: UserId, duration: FiniteDuration): Future[ActionResult] =
    auctionManager ? (StartAuction(auctionId, userId, duration, _))

  override def endAuction(auctionId: AuctionId, userId: UserId): Future[ActionResult] =
    auctionManager ? (EndAuction(auctionId, userId, _))
}
