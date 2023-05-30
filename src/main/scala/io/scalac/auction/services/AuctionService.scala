package io.scalac.auction.services

import io.scalac.auction.{ AuctionId, UserId }
import io.scalac.auction.actors.AuctionManagerActor.{ ActionResult, CreatedAuction, GotAuctionOrNotFound, GotAuctions }

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait AuctionService {

  def createAuction(creatorId: UserId): Future[CreatedAuction]

  def getAuctions: Future[GotAuctions]

  def getAuction(auctionId: AuctionId): Future[GotAuctionOrNotFound]

  def startAuction(auctionId: AuctionId, userId: UserId, duration: FiniteDuration): Future[ActionResult]

  def endAuction(auctionId: AuctionId, userId: UserId): Future[ActionResult]
}
