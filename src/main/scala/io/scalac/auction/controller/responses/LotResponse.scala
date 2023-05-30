package io.scalac.auction.controller.responses

import io.scalac.auction.{ AuctionId, LotId, UserId }

final case class LotResponse(
    id: LotId,
    auctionId: AuctionId,
    startBid: BigDecimal,
    winner: Option[UserId],
    winningBid: Option[BigDecimal],
    isDeleted: Boolean
)
