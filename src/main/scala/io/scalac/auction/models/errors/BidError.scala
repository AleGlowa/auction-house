package io.scalac.auction.models.errors

import io.scalac.auction.AuctionId
import io.scalac.auction.models.errors.CommonErrors.{ BadRequestError, ForbiddenError }

sealed trait BidError extends DomainError

object BidError {

  final case object CreatorBet extends BidError with ForbiddenError {
    override def message: String = "You're not allowed to bid an auction's lot, because you're the auction creator"
  }

  final case class TooLowBid(bid: BigDecimal, winningBid: BigDecimal) extends BidError with BadRequestError {
    override def message: String = s"New bid value $bid must be higher than the current winning bid value $winningBid"
  }

  final case object MaxBetAlreadySet extends BidError with BadRequestError {
    override def message: String = s"Can't make a new bid, because you already set max bid value"
  }

  final case class TooLowMaxBid(maxBid: BigDecimal, currBid: BigDecimal) extends BidError with BadRequestError {
    override def message: String = s"Max bid value $maxBid must be higher than the current user's bid value $currBid"
  }

  final case class LotBiddingInClosed(auctionId: AuctionId) extends BidError with BadRequestError {
    override def message: String = s"Can't make a bid when the auction ${auctionId.show} is closed"
  }
}
