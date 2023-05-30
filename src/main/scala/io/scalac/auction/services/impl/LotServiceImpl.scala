package io.scalac.auction.services.impl

import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.util.Timeout
import io.scalac.auction.actors.AuctionManagerActor
import io.scalac.auction.actors.AuctionManagerActor._
import io.scalac.auction.models.Lot
import io.scalac.auction.services.LotService
import io.scalac.auction.{ AuctionId, LotId, UserId, getRandomBase64 }

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.DurationInt

class LotServiceImpl(auctionManager: EntityRef[AuctionManagerActor.Command])(implicit ec: ExecutionContext)
    extends LotService {

  private implicit val timeout: Timeout = Timeout(5.seconds)

  override def createLot(
      auctionId: AuctionId,
      userId: UserId,
      startBid: BigDecimal
  ): Future[LotCreatedOrFailure] = {
    def getRandomNonExistentId(lotId: LotId): Future[LotId] =
      for {
        lotOrNotFound <- auctionManager.ask(GetLot(lotId, _, excludeIsDeleted = false)).map(_.lotOrNotFound)
        res <- lotOrNotFound.swap
          .fold(
            _ => getRandomNonExistentId(LotId(getRandomBase64(500_000, 1_000_000))),
            _ => Future.successful(lotId)
          )
      } yield res
    for {
      lotId <- getRandomNonExistentId(LotId(getRandomBase64(500_000, 1_000_000)))
      res <- auctionManager ? (CreateLot(Lot(lotId, auctionId, startBid), userId, _))
    } yield res
  }

  override def getLot(lotId: LotId): Future[GotLotOrNotFound] =
    auctionManager ? (GetLot(lotId, _))

  override def bidLot(
      lotId: LotId,
      userId: UserId,
      value: BigDecimal,
      isMax: Boolean
  ): Future[BetLotOrFailure] =
    auctionManager ? (BidLot(lotId, userId, value, isMax, _))
}
