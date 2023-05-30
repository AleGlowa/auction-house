package io.scalac.auction.controller.requests

import scala.concurrent.duration.FiniteDuration

final case class StartAuctionRequest(howLong: FiniteDuration)
