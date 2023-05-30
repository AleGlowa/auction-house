package io.scalac.auction.models

import io.scalac.auction.controller.responses.LotResponse
import io.scalac.auction.{ LotId, AuctionId, UserId }

final case class Lot(
    id: LotId,
    auctionId: AuctionId,
    startBid: BigDecimal,
    winner: Option[UserId] = None,
    winningBid: Option[BigDecimal] = None,
    isDeleted: Boolean = false
) {
  def toResponse: LotResponse =
    LotResponse(id, auctionId, startBid, winner, winningBid, isDeleted)
}
