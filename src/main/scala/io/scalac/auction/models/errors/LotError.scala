package io.scalac.auction.models.errors

import io.scalac.auction.models.errors.CommonErrors.{ BadRequestError, NotFoundError }
import io.scalac.auction.{ AuctionId, LotId }

sealed trait LotError extends DomainError

object LotError {

  final case class LotCreationInProgress(auctionId: AuctionId) extends LotError with BadRequestError {
    override def message: String =
      s"Can't create a lot, because the auction ${auctionId.show} is open"
  }

  final case class LotNotFound(lotId: LotId) extends LotError with NotFoundError {
    override def message: String =
      s"Lot ${lotId.show} doesn't exist"
  }
}
