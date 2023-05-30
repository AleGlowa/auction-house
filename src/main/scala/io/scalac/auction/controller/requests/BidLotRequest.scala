package io.scalac.auction.controller.requests

final case class BidLotRequest(value: BigDecimal, isMax: Boolean)
