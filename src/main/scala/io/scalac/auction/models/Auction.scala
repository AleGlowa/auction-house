package io.scalac.auction.models

import akka.actor.typed.Behavior
import io.scalac.auction.actors.AuctionActor
import io.scalac.auction.actors.AuctionActor.Command
import io.scalac.auction.controller.responses.AuctionResponse
import io.scalac.auction.{ AuctionId, UserId }

final case class Auction(
    id: AuctionId,
    creatorId: UserId,
    lots: Set[Lot] = Set.empty,
    isOpen: Boolean = false,
    isDeleted: Boolean = false
) {

  def toBehavior: Behavior[Command] = AuctionActor(id, creatorId)

  def toResponse: AuctionResponse = AuctionResponse(id, creatorId, lots.map(_.toResponse), isOpen, isDeleted)
}
