package io.scalac.auction.models

import io.scalac.auction.controller.responses.BidResponse
import io.scalac.auction.{ AuctionId, LotId, UserId }

final case class Bid(auctionId: AuctionId, lotId: LotId, userId: UserId, value: BigDecimal, max: Option[BigDecimal]) {

  def toResponse: BidResponse = BidResponse(auctionId, lotId, userId, value, max)
}
