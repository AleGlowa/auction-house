package io.scalac.auction.models

import akka.actor.typed.ActorRef
import io.scalac.auction.actors.AuctionActor

final case class AuctionWithActor(auction: Auction, actor: ActorRef[AuctionActor.Command])
