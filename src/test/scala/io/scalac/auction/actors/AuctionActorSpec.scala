package io.scalac.auction.actors

import akka.actor.testkit.typed.scaladsl.{ LoggingTestKit, ScalaTestWithActorTestKit, TestProbe }
import io.scalac.auction.models.Bid
import io.scalac.auction.models.errors.BidError.CreatorBet
import io.scalac.auction.models.errors.CommonErrors.NotAuctionCreator
import org.scalatest.EitherValues
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt

class AuctionActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with EitherValues {

  import AuctionActor._

  "Auction Actor" should {

    "return same lot after trying to add the same lot" in {
      val addProbe = createTestProbe[LotAddedOrForbidden]()
      val auctionActor = spawn(AuctionActor(AuctionId(1), UserId(1)))

      auctionActor ! AddLot(LotId(1), UserId(1), 100, addProbe.ref)
      val lot1 = addProbe.receiveMessage().lotOrForbidden

      LoggingTestKit.warn("Lot [lot-id=1] is already added").expect {
        auctionActor ! AddLot(LotId(1), UserId(1), 200, addProbe.ref)
      }

      val lot2 = addProbe.receiveMessage().lotOrForbidden
      lot1.value should ===(lot2.value)
    }

    "return None after trying to remove a non-existent lot" in {
      val removeProbe = createTestProbe[LotRemoved]()
      val auctionActor = spawn(AuctionActor(AuctionId(1), UserId(1)))

      LoggingTestKit.warn("Lot [lot-id=1] isn't in the auction").expect {
        auctionActor ! RemoveLot(LotId(1), removeProbe.ref)
      }

      removeProbe.expectMessage(LotRemoved(None))
    }
  }

  "Auction Actor" can {

    "not add lots in not Closed state" in {
      val addProbe = createTestProbe[LotAddedOrForbidden]()
      val auctionActor = spawn(AuctionActor(AuctionId(1), UserId(1)))

      auctionActor ! AddLot(LotId(1), UserId(1), 100, addProbe.ref)
      addProbe.expectMessageType[LotAddedOrForbidden]

      auctionActor ! Open(5.seconds)

      auctionActor ! AddLot(LotId(2), UserId(1), 10000, addProbe.ref)
      addProbe.expectNoMessage(500.milliseconds)
    }

    "not add a lot by not auction creator" in {
      val addProbe = createTestProbe[LotAddedOrForbidden]()
      val auctionActor = spawn(AuctionActor(AuctionId(1), UserId(1)))

      auctionActor ! AddLot(LotId(1), UserId(2), 100, addProbe.ref)
      addProbe.expectMessage(LotAddedOrForbidden(Left(NotAuctionCreator)))
    }

    "add lots in Closed state" in {
      val addProbe = createTestProbe[LotAddedOrForbidden]()
      val auctionActor = spawn(AuctionActor(AuctionId(1), UserId(1)))

      auctionActor ! AddLot(LotId(1), UserId(1), 100, addProbe.ref)
      val lot1 = addProbe.receiveMessage().lotOrForbidden

      auctionActor ! AddLot(LotId(2), UserId(1), 150, addProbe.ref)
      val lot2 = addProbe.receiveMessage().lotOrForbidden

      lot1.value should !==(lot2.value)
    }

    "not remove lots in not Closed state" in {
      val removeProbe = createTestProbe[LotRemoved]()
      val addProbe = createTestProbe[LotAddedOrForbidden]()
      val auctionActor = spawn(AuctionActor(AuctionId(1), UserId(1)))

      auctionActor ! AddLot(LotId(1), UserId(1), 100, addProbe.ref)
      addProbe.expectMessageType[LotAddedOrForbidden]

      auctionActor ! Open(5.seconds)

      auctionActor ! RemoveLot(LotId(1), removeProbe.ref)
      removeProbe.expectNoMessage()
    }

    "remove lots in Closed state" in {
      val removeProbe = createTestProbe[LotRemoved]()
      val addProbe = createTestProbe[LotAddedOrForbidden]()
      val auctionActor = spawn(AuctionActor(AuctionId(1), UserId(1)))

      auctionActor ! AddLot(LotId(1), UserId(1), 100, addProbe.ref)
      addProbe.expectMessageType[LotAddedOrForbidden]

      auctionActor ! RemoveLot(LotId(1), removeProbe.ref)
      removeProbe.expectMessage(LotRemoved(Some(LotId(1))))
    }

    "not open an auction without any lot" in {
      val auctionActor = spawn(AuctionActor(AuctionId(1), UserId(1)))

      auctionActor ! Open(5.seconds)
      TestProbe("unhandled").expectNoMessage()
    }

    "not make a bid and set a max bid by creator of the auction" in {
      val addProbe = createTestProbe[LotAddedOrForbidden]()
      val bidProbe = createTestProbe[RespondBidOrFailure]()
      val auctionActor = spawn(AuctionActor(AuctionId(1), UserId(1)))

      auctionActor ! AddLot(LotId(1), UserId(1), 100, addProbe.ref)
      addProbe.expectMessageType[LotAddedOrForbidden]

      auctionActor ! Open(5.seconds)

      auctionActor ! MakeBid(LotId(1), UserId(1), 200, bidProbe.ref)
      bidProbe.expectMessage(RespondBidOrFailure(Left(CreatorBet)))

      auctionActor ! SetMaxBid(LotId(1), UserId(1), 200, bidProbe.ref)
      bidProbe.expectMessage(RespondBidOrFailure(Left(CreatorBet)))
    }

    "not make a bid, set a max bid and get winning bid for non-existent lot" in {
      val addProbe = createTestProbe[LotAddedOrForbidden]()
      val bidProbe = createTestProbe[RespondBidOrFailure]()
      val winningBidProbe = createTestProbe[RespondWinningBid]()
      val auctionActor = spawn(AuctionActor(AuctionId(1), UserId(1)))

      auctionActor ! AddLot(LotId(1), UserId(1), 100, addProbe.ref)
      addProbe.expectMessageType[LotAddedOrForbidden]

      auctionActor ! Open(5.seconds)

      LoggingTestKit.warn("Lot [lot-id=99] isn't in the auction").expect {
        auctionActor ! MakeBid(LotId(99), UserId(2), 200, bidProbe.ref)
      }
      LoggingTestKit.warn("Lot [lot-id=99] isn't in the auction").expect {
        auctionActor ! SetMaxBid(LotId(99), UserId(2), 200, bidProbe.ref)
      }
      LoggingTestKit.warn("Lot [lot-id=99] isn't in the auction").expect {
        auctionActor ! GetWinningBid(LotId(99), winningBidProbe.ref)
      }
    }

    "make a bid, set a max bid and get winning bid" in {
      val addProbe = createTestProbe[LotAddedOrForbidden]()
      val bidProbe = createTestProbe[RespondBidOrFailure]()
      val winningBidProbe = createTestProbe[RespondWinningBid]()
      val auctionActor = spawn(AuctionActor(AuctionId(1), UserId(1)))

      auctionActor ! AddLot(LotId(1), UserId(1), 100, addProbe.ref)
      addProbe.expectMessageType[LotAddedOrForbidden]

      auctionActor ! Open(5.seconds)

      auctionActor ! MakeBid(LotId(1), UserId(2), 200, bidProbe.ref)
      bidProbe.expectMessage(RespondBidOrFailure(Right(Bid(AuctionId(1), LotId(1), UserId(2), 200, None))))

      auctionActor ! SetMaxBid(LotId(1), UserId(2), 300, bidProbe.ref)
      bidProbe.expectMessage(RespondBidOrFailure(Right(Bid(AuctionId(1), LotId(1), UserId(2), 200, Some(300)))))

      auctionActor ! GetWinningBid(LotId(1), winningBidProbe.ref)
      winningBidProbe.expectMessage(RespondWinningBid(LotId(1), UserId(2), 200))
    }
  }
}
