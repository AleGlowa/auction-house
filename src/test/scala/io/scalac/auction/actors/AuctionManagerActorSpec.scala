package io.scalac.auction.actors

import akka.actor.testkit.typed.scaladsl.{ LoggingTestKit, ScalaTestWithActorTestKit }
import io.scalac.auction.models.{ Bid, Lot }
import io.scalac.auction.models.errors.AuctionError.{ AuctionNotFound, AuctionStartWithoutLots }
import io.scalac.auction.models.errors.BidError.LotBiddingInClosed
import io.scalac.auction.models.errors.CommonErrors.NotAuctionCreator
import io.scalac.auction.models.errors.LotError.{ LotCreationInProgress, LotNotFound }
import org.scalatest.EitherValues
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt

class AuctionManagerActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with EitherValues {
  import AuctionManagerActor._
  import io.scalac.auction.models.Auction

  "Auction Manager Actor" should {

    "get same actor after trying to add the same auction" in {
      val createdProbe = createTestProbe[AuctionCreated]()
      val auctionManagerActor = spawn(AuctionManagerActor())
      val auction = Auction(AuctionId(1), UserId(1))

      auctionManagerActor ! CreateAuction(auction, createdProbe.ref)
      val auction1 = createdProbe.receiveMessage().auction

      LoggingTestKit.warn("Auction [auction-id=1] is already created").expect {
        auctionManagerActor ! CreateAuction(auction, createdProbe.ref)
      }

      val auction2 = createdProbe.receiveMessage().auction
      auction1 should ===(auction2)
    }

    "get all auctions" in {
      val createdProbe = createTestProbe[AuctionCreated]()
      val gotProbe = createTestProbe[GotAuctions]()
      val auctionManagerActor = spawn(AuctionManagerActor())
      val auction = Auction(AuctionId(1), UserId(1))

      auctionManagerActor ! CreateAuction(auction, createdProbe.ref)
      createdProbe.receiveMessage().auction should ===(auction)

      auctionManagerActor ! GetAuctions(gotProbe.ref)
      gotProbe.receiveMessage().auctions should ===(Set(auction))
    }

    "get an auction by its id" in {
      val createdProbe = createTestProbe[AuctionCreated]()
      val gotProbe = createTestProbe[GotAuctionOrNotFound]()
      val auctionManagerActor = spawn(AuctionManagerActor())
      val auction = Auction(AuctionId(1), UserId(1))

      auctionManagerActor ! CreateAuction(auction, createdProbe.ref)
      createdProbe.receiveMessage().auction should ===(auction)

      auctionManagerActor ! GetAuction(auction.id, gotProbe.ref)
      gotProbe.receiveMessage().auctionOrNotFound.value should ===(auction)
    }

    "not get a non-existent auction" in {
      val gotProbe = createTestProbe[GotAuctionOrNotFound]()
      val auctionManagerActor = spawn(AuctionManagerActor())
      val auction = Auction(AuctionId(1), UserId(1))

      auctionManagerActor ! GetAuction(auction.id, gotProbe.ref)
      gotProbe.receiveMessage().auctionOrNotFound.swap.value should ===(AuctionNotFound(auction.id))
    }

    "inform about the success after a started auction" in {
      val actionProbe = createTestProbe[ActionResult]()
      val auctionCreatedProbe = createTestProbe[AuctionCreated]()
      val lotCreatedProbe = createTestProbe[LotCreatedOrFailure]()
      val auctionManagerActor = spawn(AuctionManagerActor())
      val auction = Auction(AuctionId(1), UserId(1))
      val lot = Lot(LotId(1), AuctionId(1), 100)

      auctionManagerActor ! CreateAuction(auction, auctionCreatedProbe.ref)
      auctionCreatedProbe.receiveMessage().auction should ===(auction)

      auctionManagerActor ! CreateLot(lot, UserId(1), lotCreatedProbe.ref)
      lotCreatedProbe.receiveMessage().lotOrFailure.value should ===(lot)

      auctionManagerActor ! StartAuction(AuctionId(1), UserId(1), 5.seconds, actionProbe.ref)
      actionProbe.receiveMessage().res.value should ===(Success("Auction [auction-id=1] has been started"))
    }

    "inform about no changes, in case of starting the same auction again" in {
      val actionProbe = createTestProbe[ActionResult]()
      val auctionCreatedProbe = createTestProbe[AuctionCreated]()
      val lotCreatedProbe = createTestProbe[LotCreatedOrFailure]()
      val auctionManagerActor = spawn(AuctionManagerActor())
      val auction = Auction(AuctionId(1), UserId(1))
      val lot = Lot(LotId(1), AuctionId(1), 100)

      auctionManagerActor ! CreateAuction(auction, auctionCreatedProbe.ref)
      auctionCreatedProbe.receiveMessage().auction should ===(auction)

      auctionManagerActor ! CreateLot(lot, UserId(1), lotCreatedProbe.ref)
      lotCreatedProbe.receiveMessage().lotOrFailure.value should ===(lot)

      auctionManagerActor ! StartAuction(AuctionId(1), UserId(1), 5.seconds, actionProbe.ref)
      actionProbe.receiveMessage().res.value should ===(Success("Auction [auction-id=1] has been started"))

      auctionManagerActor ! StartAuction(AuctionId(1), UserId(1), 5.seconds, actionProbe.ref)
      actionProbe.receiveMessage().res.value should ===(
        NoChange(s"Auction [auction-id=1] is already started. No actions performed")
      )
    }

    "not start an auction without lots" in {
      val actionProbe = createTestProbe[ActionResult]()
      val auctionCreatedProbe = createTestProbe[AuctionCreated]()
      val auctionManagerActor = spawn(AuctionManagerActor())
      val auction = Auction(AuctionId(1), UserId(1))

      auctionManagerActor ! CreateAuction(auction, auctionCreatedProbe.ref)
      auctionCreatedProbe.receiveMessage().auction should ===(auction)

      auctionManagerActor ! StartAuction(AuctionId(1), UserId(1), 5.seconds, actionProbe.ref)
      actionProbe.receiveMessage().res.swap.value should ===(AuctionStartWithoutLots(auction.id))
    }

    "not start a non-existent auction" in {
      val actionProbe = createTestProbe[ActionResult]()
      val auctionManagerActor = spawn(AuctionManagerActor())

      auctionManagerActor ! StartAuction(AuctionId(1), UserId(1), 5.seconds, actionProbe.ref)
      actionProbe.receiveMessage().res.swap.value should ===(AuctionNotFound(AuctionId(1)))
    }

    "not start an auction by a non-creator of the auction" in {
      val actionProbe = createTestProbe[ActionResult]()
      val auctionCreatedProbe = createTestProbe[AuctionCreated]()
      val auctionManagerActor = spawn(AuctionManagerActor())
      val auction = Auction(AuctionId(1), UserId(1))

      auctionManagerActor ! CreateAuction(auction, auctionCreatedProbe.ref)
      auctionCreatedProbe.receiveMessage().auction should ===(auction)

      auctionManagerActor ! StartAuction(AuctionId(1), UserId(2), 5.seconds, actionProbe.ref)
      actionProbe.receiveMessage().res.swap.value should ===(NotAuctionCreator)
    }

    "inform about the success, after an ended auction" in {
      val actionProbe = createTestProbe[ActionResult]()
      val auctionCreatedProbe = createTestProbe[AuctionCreated]()
      val lotCreatedProbe = createTestProbe[LotCreatedOrFailure]()
      val auctionManagerActor = spawn(AuctionManagerActor())
      val auction = Auction(AuctionId(1), UserId(1))
      val lot = Lot(LotId(1), AuctionId(1), 100)

      auctionManagerActor ! CreateAuction(auction, auctionCreatedProbe.ref)
      auctionCreatedProbe.receiveMessage().auction should ===(auction)

      auctionManagerActor ! CreateLot(lot, UserId(1), lotCreatedProbe.ref)
      lotCreatedProbe.receiveMessage().lotOrFailure.value should ===(lot)

      auctionManagerActor ! StartAuction(AuctionId(1), UserId(1), 5.seconds, actionProbe.ref)
      actionProbe.receiveMessage().res.value should ===(Success("Auction [auction-id=1] has been started"))

      auctionManagerActor ! EndAuction(AuctionId(1), UserId(1), actionProbe.ref)
      actionProbe.receiveMessage().res.value should ===(Success("Auction [auction-id=1] has been ended"))
    }

    "inform about no changes, in case of ending a not started auction" in {
      val actionProbe = createTestProbe[ActionResult]()
      val auctionCreatedProbe = createTestProbe[AuctionCreated]()
      val auctionManagerActor = spawn(AuctionManagerActor())
      val auction = Auction(AuctionId(1), UserId(1))

      auctionManagerActor ! CreateAuction(auction, auctionCreatedProbe.ref)
      auctionCreatedProbe.receiveMessage().auction should ===(auction)

      auctionManagerActor ! EndAuction(AuctionId(1), UserId(1), actionProbe.ref)
      actionProbe.receiveMessage().res.value should ===(
        NoChange("Auction [auction-id=1] isn't started. No actions performed")
      )
    }

    "not end a non-existent auction" in {
      val actionProbe = createTestProbe[ActionResult]()
      val auctionManagerActor = spawn(AuctionManagerActor())

      auctionManagerActor ! EndAuction(AuctionId(1), UserId(1), actionProbe.ref)
      actionProbe.receiveMessage().res.swap.value should ===(AuctionNotFound(AuctionId(1)))
    }

    "not end an auction by a non-creator of the auction" in {
      val actionProbe = createTestProbe[ActionResult]()
      val auctionCreatedProbe = createTestProbe[AuctionCreated]()
      val lotCreatedProbe = createTestProbe[LotCreatedOrFailure]()
      val auctionManagerActor = spawn(AuctionManagerActor())
      val auction = Auction(AuctionId(1), UserId(1))
      val lot = Lot(LotId(1), AuctionId(1), 100)

      auctionManagerActor ! CreateAuction(auction, auctionCreatedProbe.ref)
      auctionCreatedProbe.receiveMessage().auction should ===(auction)

      auctionManagerActor ! CreateLot(lot, UserId(1), lotCreatedProbe.ref)
      lotCreatedProbe.receiveMessage().lotOrFailure.value should ===(lot)

      auctionManagerActor ! StartAuction(AuctionId(1), UserId(1), 5.seconds, actionProbe.ref)
      actionProbe.receiveMessage().res.value should ===(Success("Auction [auction-id=1] has been started"))

      auctionManagerActor ! EndAuction(AuctionId(1), UserId(2), actionProbe.ref)
      actionProbe.receiveMessage().res.swap.value should ===(NotAuctionCreator)
    }

    "create a lot" in {
      val auctionCreatedProbe = createTestProbe[AuctionCreated]()
      val auctionManagerActor = spawn(AuctionManagerActor())
      val lotCreatedProbe = createTestProbe[LotCreatedOrFailure]()
      val auction = Auction(AuctionId(1), UserId(1))
      val lot = Lot(LotId(1), AuctionId(1), 100)

      auctionManagerActor ! CreateAuction(auction, auctionCreatedProbe.ref)
      auctionCreatedProbe.receiveMessage().auction should ===(auction)

      auctionManagerActor ! CreateLot(lot, UserId(1), lotCreatedProbe.ref)
      lotCreatedProbe.receiveMessage().lotOrFailure.value should ===(lot)
    }

    "not create a lot when the auction is open" in {
      val actionProbe = createTestProbe[ActionResult]()
      val auctionCreatedProbe = createTestProbe[AuctionCreated]()
      val lotCreatedProbe = createTestProbe[LotCreatedOrFailure]()
      val auctionManagerActor = spawn(AuctionManagerActor())
      val auction = Auction(AuctionId(1), UserId(1))
      val lot = Lot(LotId(1), AuctionId(1), 100)

      auctionManagerActor ! CreateAuction(auction, auctionCreatedProbe.ref)
      auctionCreatedProbe.receiveMessage().auction should ===(auction)

      auctionManagerActor ! CreateLot(lot, UserId(1), lotCreatedProbe.ref)
      lotCreatedProbe.receiveMessage().lotOrFailure.value should ===(lot)

      auctionManagerActor ! StartAuction(AuctionId(1), UserId(1), 5.seconds, actionProbe.ref)
      actionProbe.receiveMessage().res.value should ===(Success("Auction [auction-id=1] has been started"))

      auctionManagerActor ! CreateLot(lot.copy(id = LotId(2)), UserId(1), lotCreatedProbe.ref)
      lotCreatedProbe.receiveMessage().lotOrFailure.swap.value should ===(LotCreationInProgress(lot.auctionId))
    }

    "not create an auction's lot by a non-creator of the auction" in {
      val auctionCreatedProbe = createTestProbe[AuctionCreated]()
      val lotCreatedProbe = createTestProbe[LotCreatedOrFailure]()
      val auctionManagerActor = spawn(AuctionManagerActor())
      val auction = Auction(AuctionId(1), UserId(1))
      val lot = Lot(LotId(1), AuctionId(1), 100)

      auctionManagerActor ! CreateAuction(auction, auctionCreatedProbe.ref)
      auctionCreatedProbe.receiveMessage().auction should ===(auction)

      auctionManagerActor ! CreateLot(lot, UserId(2), lotCreatedProbe.ref)
      lotCreatedProbe.receiveMessage().lotOrFailure.swap.value should ===(NotAuctionCreator)
    }

    "not create a lot for a non-existent auction" in {
      val lotCreatedProbe = createTestProbe[LotCreatedOrFailure]()
      val auctionManagerActor = spawn(AuctionManagerActor())

      auctionManagerActor ! CreateLot(Lot(LotId(1), AuctionId(1), 100), UserId(1), lotCreatedProbe.ref)
      lotCreatedProbe.receiveMessage().lotOrFailure.swap.value should ===(AuctionNotFound(AuctionId(1)))
    }

    "get all an auction's lots" in {
      val gotLotsProbe = createTestProbe[GotLotsOrNotFound]()
      val auctionCreatedProbe = createTestProbe[AuctionCreated]()
      val lotCreatedProbe = createTestProbe[LotCreatedOrFailure]()
      val auctionManagerActor = spawn(AuctionManagerActor())
      val auction = Auction(AuctionId(1), UserId(1))
      val lot = Lot(LotId(1), AuctionId(1), 100)

      auctionManagerActor ! CreateAuction(auction, auctionCreatedProbe.ref)
      auctionCreatedProbe.receiveMessage().auction should ===(auction)

      auctionManagerActor ! CreateLot(lot, UserId(1), lotCreatedProbe.ref)
      lotCreatedProbe.receiveMessage().lotOrFailure.value should ===(lot)

      auctionManagerActor ! GetLots(AuctionId(1), gotLotsProbe.ref)
      gotLotsProbe.receiveMessage().lotsOrNotFound.value should ===(Set(lot))
    }

    "not get lots for a non-existent auction" in {
      val gotLotsProbe = createTestProbe[GotLotsOrNotFound]()
      val auctionManagerActor = spawn(AuctionManagerActor())

      auctionManagerActor ! GetLots(AuctionId(1), gotLotsProbe.ref)
      gotLotsProbe.receiveMessage().lotsOrNotFound.swap.value should ===(AuctionNotFound(AuctionId(1)))
    }

    "get a lot by its id" in {
      val gotLotProbe = createTestProbe[GotLotOrNotFound]()
      val auctionCreatedProbe = createTestProbe[AuctionCreated]()
      val lotCreatedProbe = createTestProbe[LotCreatedOrFailure]()
      val auctionManagerActor = spawn(AuctionManagerActor())
      val auction = Auction(AuctionId(1), UserId(1))
      val lot = Lot(LotId(1), AuctionId(1), 100)

      auctionManagerActor ! CreateAuction(auction, auctionCreatedProbe.ref)
      auctionCreatedProbe.receiveMessage().auction should ===(auction)

      auctionManagerActor ! CreateLot(lot, UserId(1), lotCreatedProbe.ref)
      lotCreatedProbe.receiveMessage().lotOrFailure.value should ===(lot)

      auctionManagerActor ! GetLot(AuctionId(1), LotId(1), gotLotProbe.ref)
      gotLotProbe.receiveMessage().lotOrNotFound.value should ===(lot)
    }

    "not get a non-existent lot" in {
      val gotLotProbe = createTestProbe[GotLotOrNotFound]()
      val auctionManagerActor = spawn(AuctionManagerActor())

      auctionManagerActor ! GetLot(AuctionId(1), LotId(1), gotLotProbe.ref)
      gotLotProbe.receiveMessage().lotOrNotFound.swap.value should ===(LotNotFound(LotId(1)))
    }

    "bid a lot" in {
      val auctionCreatedProbe = createTestProbe[AuctionCreated]()
      val lotCreatedProbe = createTestProbe[LotCreatedOrFailure]()
      val betLotProbe = createTestProbe[BetLotOrFailure]()
      val actionProbe = createTestProbe[ActionResult]()
      val auctionManagerActor = spawn(AuctionManagerActor())
      val auction = Auction(AuctionId(1), UserId(1))
      val lot = Lot(LotId(1), AuctionId(1), 100)

      auctionManagerActor ! CreateAuction(auction, auctionCreatedProbe.ref)
      auctionCreatedProbe.receiveMessage().auction should ===(auction)

      auctionManagerActor ! CreateLot(lot, UserId(1), lotCreatedProbe.ref)
      lotCreatedProbe.receiveMessage().lotOrFailure.value should ===(lot)

      auctionManagerActor ! StartAuction(AuctionId(1), UserId(1), 5.seconds, actionProbe.ref)
      actionProbe.receiveMessage().res.value should ===(Success("Auction [auction-id=1] has been started"))

      auctionManagerActor ! BidLot(AuctionId(1), LotId(1), UserId(2), 200, isMax = false, betLotProbe.ref)
      betLotProbe.receiveMessage().bidOrFailure.value should ===(Bid(AuctionId(1), LotId(1), UserId(2), 200, None))
    }

    "not bid a lot in a closed auction" in {
      val auctionCreatedProbe = createTestProbe[AuctionCreated]()
      val lotCreatedProbe = createTestProbe[LotCreatedOrFailure]()
      val betLotProbe = createTestProbe[BetLotOrFailure]()
      val auctionManagerActor = spawn(AuctionManagerActor())
      val auction = Auction(AuctionId(1), UserId(1))
      val lot = Lot(LotId(1), AuctionId(1), 100)

      auctionManagerActor ! CreateAuction(auction, auctionCreatedProbe.ref)
      auctionCreatedProbe.receiveMessage().auction should ===(auction)

      auctionManagerActor ! CreateLot(lot, UserId(1), lotCreatedProbe.ref)
      lotCreatedProbe.receiveMessage().lotOrFailure.value should ===(lot)

      auctionManagerActor ! BidLot(AuctionId(1), LotId(1), UserId(2), 200, isMax = false, betLotProbe.ref)
      betLotProbe.receiveMessage().bidOrFailure.swap.value should ===(LotBiddingInClosed(auction.id))
    }

    "not bid a non-existent lot" in {
      val auctionCreatedProbe = createTestProbe[AuctionCreated]()
      val lotCreatedProbe = createTestProbe[LotCreatedOrFailure]()
      val actionProbe = createTestProbe[ActionResult]()
      val betLotProbe = createTestProbe[BetLotOrFailure]()
      val auctionManagerActor = spawn(AuctionManagerActor())
      val auction = Auction(AuctionId(1), UserId(1))
      val lot = Lot(LotId(1), AuctionId(1), 100)

      auctionManagerActor ! CreateAuction(auction, auctionCreatedProbe.ref)
      auctionCreatedProbe.receiveMessage().auction should ===(auction)

      auctionManagerActor ! CreateLot(lot, UserId(1), lotCreatedProbe.ref)
      lotCreatedProbe.receiveMessage().lotOrFailure.value should ===(lot)

      auctionManagerActor ! StartAuction(AuctionId(1), UserId(1), 5.seconds, actionProbe.ref)
      actionProbe.receiveMessage().res.value should ===(Success("Auction [auction-id=1] has been started"))

      auctionManagerActor ! BidLot(AuctionId(1), LotId(2), UserId(2), 200, isMax = false, betLotProbe.ref)
      betLotProbe.receiveMessage().bidOrFailure.swap.value should ===(LotNotFound(LotId(2)))
    }
  }
}
