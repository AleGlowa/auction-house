package io.scalac.auction.controller.requests

import io.scalac.auction.AuctionId

final case class CreateLotRequest(auctionId: AuctionId, startBid: BigDecimal)
