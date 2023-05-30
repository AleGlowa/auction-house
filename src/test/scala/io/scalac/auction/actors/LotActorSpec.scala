package io.scalac.auction.actors

import akka.actor.testkit.typed.scaladsl.{ LoggingTestKit, ScalaTestWithActorTestKit }
import io.scalac.auction.models.Bid
import org.scalatest.wordspec.AnyWordSpecLike

class LotActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  import AuctionActor._
  import LotActor._
  import io.scalac.auction.models.errors.BidError._

  "LotActor" must {

    "not reply when auction creator try to bid self lot" in {
      val bidProbe = createTestProbe[RespondBidOrFailure]()
      val lotActor = spawn(LotActor(LotId(1), AuctionId(1), UserId(1), 100))

      lotActor ! MakeBid(LotId(1), UserId(1), 200, bidProbe.ref)
      bidProbe.expectMessage(RespondBidOrFailure(Left(CreatorBet)))

      lotActor ! SetMaxBid(LotId(1), UserId(1), 200, bidProbe.ref)
      bidProbe.expectMessage(RespondBidOrFailure(Left(CreatorBet)))
    }

    "not reply when a user with max bid value set wants to make a new bid" in {
      val bidProbe = createTestProbe[RespondBidOrFailure]()
      val lotActor = spawn(LotActor(LotId(1), AuctionId(1), UserId(1), 100))

      lotActor ! SetMaxBid(LotId(1), UserId(2), 200, bidProbe.ref)
      bidProbe.expectMessage(
        RespondBidOrFailure(
          Right(
            Bid(AuctionId(1), LotId(1), UserId(2), 200, Some(200))
          )
        )
      )

      lotActor ! MakeBid(LotId(1), UserId(2), 300, bidProbe.ref)
      bidProbe.expectMessage(RespondBidOrFailure(Left(MaxBetAlreadySet)))
    }

    "inform when a lot is stopped" in {
      val lotActor = spawn(LotActor(LotId(1), AuctionId(1), UserId(1), 100))

      LoggingTestKit.info("Lot actor [lot-id=1][auction-id=1] stopped").expect {
        lotActor ! Passivate
      }
    }

    "reply with a new winning bid when the new bid is higher than the previous winning bid" in {
      val bidProbe = createTestProbe[RespondBidOrFailure]()
      val winningBidProbe = createTestProbe[RespondWinningBid]()
      val lotActor = spawn(LotActor(LotId(1), AuctionId(1), UserId(1), 100))

      lotActor ! MakeBid(LotId(1), UserId(2), 200, bidProbe.ref)
      bidProbe.expectMessage(
        RespondBidOrFailure(
          Right(
            Bid(AuctionId(1), LotId(1), UserId(2), 200, None)
          )
        )
      )

      lotActor ! GetWinningBid(LotId(1), winningBidProbe.ref)
      winningBidProbe.expectMessage(RespondWinningBid(LotId(1), UserId(2), 200))
    }

    "reply with a new winning bid when the new bid is higher than the previous winning bid twice in a row" in {
      val bidProbe = createTestProbe[RespondBidOrFailure]()
      val winningBidProbe = createTestProbe[RespondWinningBid]()
      val lotActor = spawn(LotActor(LotId(1), AuctionId(1), UserId(1), 100))

      lotActor ! MakeBid(LotId(1), UserId(2), 200, bidProbe.ref)
      bidProbe.expectMessage(
        RespondBidOrFailure(
          Right(
            Bid(AuctionId(1), LotId(1), UserId(2), 200, None)
          )
        )
      )

      lotActor ! MakeBid(LotId(1), UserId(2), 300, bidProbe.ref)
      bidProbe.expectMessage(
        RespondBidOrFailure(
          Right(
            Bid(AuctionId(1), LotId(1), UserId(2), 300, None)
          )
        )
      )

      lotActor ! GetWinningBid(LotId(1), winningBidProbe.ref)
      winningBidProbe.expectMessage(RespondWinningBid(LotId(1), UserId(2), 300))
    }

    "not reply when a user try to set max bid value lower than his current bid value" in {
      val bidProbe = createTestProbe[RespondBidOrFailure]()
      val lotActor = spawn(LotActor(LotId(1), AuctionId(1), UserId(1), 100))

      lotActor ! MakeBid(LotId(1), UserId(2), 200, bidProbe.ref)
      bidProbe.expectMessage(
        RespondBidOrFailure(
          Right(
            Bid(AuctionId(1), LotId(1), UserId(2), 200, None)
          )
        )
      )

      lotActor ! SetMaxBid(LotId(1), UserId(2), 150, bidProbe.ref)
      bidProbe.expectMessage(RespondBidOrFailure(Left(TooLowMaxBid(150, 200))))
    }

    "reply with lower bid than the winning bid when some user's max bid is lower than the winning bid" in {
      val bidProbe = createTestProbe[RespondBidOrFailure]()
      val winningBidProbe = createTestProbe[RespondWinningBid]()
      val lotActor = spawn(LotActor(LotId(1), AuctionId(1), UserId(1), 100))

      lotActor ! MakeBid(LotId(1), UserId(2), 200, bidProbe.ref)
      bidProbe.expectMessage(
        RespondBidOrFailure(
          Right(
            Bid(AuctionId(1), LotId(1), UserId(2), 200, None)
          )
        )
      )

      lotActor ! MakeBid(LotId(1), UserId(3), 300, bidProbe.ref)
      bidProbe.expectMessage(
        RespondBidOrFailure(
          Right(
            Bid(AuctionId(1), LotId(1), UserId(3), 300, None)
          )
        )
      )

      lotActor ! SetMaxBid(LotId(1), UserId(4), 250, bidProbe.ref)
      bidProbe.expectMessage(
        RespondBidOrFailure(
          Right(
            Bid(AuctionId(1), LotId(1), UserId(4), 250, Some(250))
          )
        )
      )

      lotActor ! GetWinningBid(LotId(1), winningBidProbe.ref)
      winningBidProbe.expectMessage(RespondWinningBid(LotId(1), UserId(3), 300))
    }

    "reply with the correct winning bid in a complicated scenario" in {
      val bidProbe = createTestProbe[RespondBidOrFailure]()
      val winningBidProbe = createTestProbe[RespondWinningBid]()
      val lotActor = spawn(LotActor(LotId(1), AuctionId(1), UserId(1), 100))

      // MaxBid from user 2 = 200
      lotActor ! SetMaxBid(LotId(1), UserId(2), 200, bidProbe.ref)
      bidProbe.expectMessage(
        RespondBidOrFailure(
          Right(
            Bid(AuctionId(1), LotId(1), UserId(2), 200, Some(200))
          )
        )
      )

      // MaxBid from user 3 = 300
      // (300 > 200) - Outbid user 2 to 200.01
      lotActor ! SetMaxBid(LotId(1), UserId(3), 300, bidProbe.ref)
      bidProbe.expectMessage(
        RespondBidOrFailure(
          Right(
            Bid(AuctionId(1), LotId(1), UserId(3), 200.01, Some(300))
          )
        )
      )

      // MaxBid from user 4 = 250
      // (250 > 200) - Outbid user 2 to 250
      // (300 > 250) - Update bid from user 3 to 250.01
      lotActor ! SetMaxBid(LotId(1), UserId(4), 250, bidProbe.ref)
      bidProbe.expectMessage(
        RespondBidOrFailure(
          Right(
            Bid(AuctionId(1), LotId(1), UserId(4), 250, Some(250))
          )
        )
      )

      // MaxBid from user 5 = 400
      // (400 > 300) - Outbid users 2, 3, 4 to 300.01
      lotActor ! SetMaxBid(LotId(1), UserId(5), 400, bidProbe.ref)
      bidProbe.expectMessage(
        RespondBidOrFailure(
          Right(
            Bid(AuctionId(1), LotId(1), UserId(5), 300.01, Some(400))
          )
        )
      )

      // MakeBid from user 6 = 375
      // (375 > 300) - Outbid users 2, 3, 4 to 375
      // (400 > 375) - Update bid from user 5 to 375.01
      lotActor ! MakeBid(LotId(1), UserId(6), 375, bidProbe.ref)
      bidProbe.expectMessage(
        RespondBidOrFailure(
          Right(
            Bid(AuctionId(1), LotId(1), UserId(6), 375, None)
          )
        )
      )

      lotActor ! GetWinningBid(LotId(1), winningBidProbe.ref)
      winningBidProbe.expectMessage(RespondWinningBid(LotId(1), UserId(5), 375.01))
    }
  }
}
