package io.scalac.auction.controller.responses

import io.scalac.auction.{ AuctionId, LotId, UserId }

final case class BidResponse(
    auctionId: AuctionId,
    lotId: LotId,
    userId: UserId,
    value: BigDecimal,
    max: Option[BigDecimal]
)
