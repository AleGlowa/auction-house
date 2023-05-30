package io.scalac.auction.actors

import akka.actor.typed.{ ActorRef, Behavior, PostStop }
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import io.scalac.auction.models.Bid
import io.scalac.auction.models.errors.BidError.{ CreatorBet, MaxBetAlreadySet, TooLowBid, TooLowMaxBid }
import io.scalac.auction.{ AuctionId, LotId, UserId }

import scala.collection.mutable.{ Map => MutMap }

object LotActor {
  def apply(id: LotId, auctionId: AuctionId, userId: UserId, startBid: BigDecimal): Behavior[Command] =
    Behaviors.setup { ctx =>
      new LotActor(ctx, id, auctionId, userId, startBid).init()
    }

  trait Command

  private final case class UpdateBid(id: LotId, userId: UserId, newValue: BigDecimal) extends Command
}

class LotActor private (
    ctx: ActorContext[LotActor.Command],
    id: LotId,
    auctionId: AuctionId,
    userId: UserId,
    startBid: BigDecimal
) {
  import LotActor._
  import AuctionActor.{ MakeBid, SetMaxBid, RespondBidOrFailure, GetWinningBid, RespondWinningBid }

  private var winningBid: BigDecimal = startBid
  private var winner: UserId = userId
  private val userIdToBidWithMax = MutMap.empty[UserId, (BigDecimal, Option[BigDecimal])]
  private val OutBidBy = BigDecimal.decimal(0.01)

  ctx.log.info(
    "Lot actor {}{}{} has been created with starting bid value {}",
    id.show,
    auctionId.show,
    userId.show,
    startBid
  )

  override def toString: String =
    s"Lot ${id.show}${auctionId.show}${userId.show}[winner=$winner][winning-bid=$winningBid]"

  private def init(): Behavior[Command] =
    Behaviors
      .receiveMessage[Command] {

        case MakeBid(_, `userId`, _, replyTo) =>
          ctx.log.warn("Auction creator {} can't bid on his own lot {}", userId.show, id.show)
          replyTo ! RespondBidOrFailure(Left(CreatorBet))
          Behaviors.same

        case SetMaxBid(_, `userId`, _, replyTo) =>
          ctx.log.warn("Auction creator {} can't bid on his own lot {}", userId.show, id.show)
          replyTo ! RespondBidOrFailure(Left(CreatorBet))
          Behaviors.same

        case MakeBid(lotId, _, value, replyTo) if value <= winningBid =>
          ctx.log.warn(
            "New bid value {} must be higher than the current winning bid value {} in lot {}",
            value,
            winningBid,
            lotId.show
          )
          replyTo ! RespondBidOrFailure(Left(TooLowBid(value, winningBid)))
          Behaviors.unhandled

        case MakeBid(lotId, uId, _, replyTo) if userIdToBidWithMax.get(uId).exists(_._2.isDefined) =>
          ctx.log.warn(
            "User {} can't make a new bid, because he/she has already set max bid value in lot {}",
            uId.show,
            lotId.show
          )
          replyTo ! RespondBidOrFailure(Left(MaxBetAlreadySet))
          Behaviors.unhandled

        case MakeBid(lotId, uId, value, replyTo) =>
          winningBid = value
          winner = uId
          userIdToBidWithMax(uId) = (value, None)
          ctx.log.info(
            "New winning bid value set {} by user {} in lot {}",
            winningBid,
            winner.show,
            lotId.show
          )
          replyTo ! RespondBidOrFailure(Right(Bid(auctionId, lotId, uId, value, None)))
          updateBid(lotId, value, value + OutBidBy)
          Behaviors.same

        case SetMaxBid(lotId, userId, value, replyTo) =>
          userIdToBidWithMax.get(userId) match {

            case Some((bid, _)) if value <= bid =>
              ctx.log.warn(
                "Max bid value {} must be higher than the current user's bid value {} in lot {}",
                value,
                bid,
                lotId.show
              )
              replyTo ! RespondBidOrFailure(Left(TooLowMaxBid(value, bid)))
              Behaviors.unhandled

            case None =>
              onSetMaxBid(lotId, userId, value, value, replyTo)

            case Some((bid, _)) =>
              onSetMaxBid(lotId, userId, value, bid, replyTo)
          }

        case GetWinningBid(id, replyTo) =>
          replyTo ! RespondWinningBid(id, winner, winningBid)
          Behaviors.same

        case UpdateBid(id, userId, newValue) =>
          winningBid = newValue
          winner = userId
          userIdToBidWithMax(userId) = (newValue, userIdToBidWithMax(userId)._2)
          ctx.log.info(
            "New winning bid value set {} by user {} in lot {}",
            winningBid,
            winner.show,
            id.show
          )
          Behaviors.same
      }
      .receiveSignal { case (_, PostStop) =>
        ctx.log.info("Lot actor {}{} stopped", id.show, auctionId.show)
        Behaviors.same
      }

  private def onSetMaxBid(
      lotId: LotId,
      userId: UserId,
      maxBid: BigDecimal,
      default: BigDecimal,
      replyTo: ActorRef[RespondBidOrFailure]
  ): Behavior[Command] = {
    val newBid =
      userIdToBidWithMax
        .filterNot(_._1 == userId)
        .values
        .map { case (bid, mBid) =>
          val tmp1 = maxBid - bid
          val tmp2 = maxBid - mBid.getOrElse(bid)

          if (tmp1 < 0 || tmp2 < 0) OutBidBy else tmp1 min tmp2
        }
        .minOption
        .fold(default)(maxBid - _ + OutBidBy)
    userIdToBidWithMax(userId) = (newBid, Some(maxBid))

    if (newBid > winningBid) {
      winningBid = newBid
      winner = userId
      ctx.log.info(
        "New winning bid value set {} by user {} in lot {}",
        winningBid,
        winner.show,
        lotId.show
      )
      updateBid(lotId, maxBid, newBid + OutBidBy)
    }
    replyTo ! RespondBidOrFailure(Right(Bid(auctionId, lotId, userId, newBid, Some(maxBid))))
    Behaviors.same
  }

  private def updateBid(id: LotId, currBid: BigDecimal, newBid: BigDecimal): Unit =
    userIdToBidWithMax
      .find { case (_, v) => v._2.exists(_ > currBid) }
      .foreach { case (k, _) => ctx.self ! UpdateBid(id, k, newBid) }
}
