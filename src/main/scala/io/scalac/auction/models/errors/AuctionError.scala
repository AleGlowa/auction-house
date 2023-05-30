package io.scalac.auction.models.errors

import io.scalac.auction.AuctionId
import io.scalac.auction.models.errors.CommonErrors.{ BadRequestError, NotFoundError }

sealed trait AuctionError extends DomainError

object AuctionError {

  final case class AuctionStartWithoutLots(auctionId: AuctionId) extends AuctionError with BadRequestError {
    override def message: String = s"Auction ${auctionId.show} can't be started without lots"
  }

  final case class AuctionNotFound(auctionId: AuctionId) extends AuctionError with NotFoundError {
    override def message: String = s"Auction ${auctionId.show} doesn't exist"
  }
}
