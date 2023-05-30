package io.scalac.auction.models

import akka.actor.typed.ActorRef
import io.scalac.auction.actors.LotActor

final case class LotWithActor(lot: Lot, actor: ActorRef[LotActor.Command])
