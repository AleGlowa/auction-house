package io.scalac.auction.models.errors

object CommonErrors {

  trait NotFoundError extends DomainError
  trait BadRequestError extends DomainError
  trait ForbiddenError extends DomainError
  trait UnauthorizedError extends DomainError

  final case object NotAuctionCreator extends ForbiddenError {
    override def message: String = "You're not allowed to do this, because you're not the auction creator"
  }
}
