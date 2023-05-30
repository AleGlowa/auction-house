package io.scalac.auction.controller.responses

import io.scalac.auction.{ AuctionId, UserId }

final case class AuctionResponse(
    id: AuctionId,
    creatorId: UserId,
    lots: Set[LotResponse],
    isOpen: Boolean,
    isDeleted: Boolean
)
