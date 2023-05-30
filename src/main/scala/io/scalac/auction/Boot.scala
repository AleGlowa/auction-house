package io.scalac.auction

import akka.actor.typed.ActorSystem
import io.scalac.auction.actors.Guardian

object Boot extends App {

  ActorSystem[Nothing](Guardian(HttpPort), "AuctionHouse", config)
}
