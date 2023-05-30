package io.scalac.auction.services

import io.scalac.auction.{ AuctionId, LotId, UserId }
import io.scalac.auction.actors.AuctionManagerActor.{ BetLotOrFailure, GotLotOrNotFound, LotCreatedOrFailure }

import scala.concurrent.Future

trait LotService {

  def createLot(
      auctionId: AuctionId,
      userId: UserId,
      startBid: BigDecimal
  ): Future[LotCreatedOrFailure]

  def getLot(lotId: LotId): Future[GotLotOrNotFound]

  def bidLot(
      lotId: LotId,
      userId: UserId,
      value: BigDecimal,
      isMax: Boolean = false
  ): Future[BetLotOrFailure]
}
