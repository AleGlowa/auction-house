package io.scalac.auction.actors

import akka.actor.typed.{ ActorRef, Behavior, PostStop }
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import com.softwaremill.quicklens.ModifyPimp
import io.scalac.auction.models.{ Bid, Lot, LotWithActor }
import io.scalac.auction.models.errors.BidError
import io.scalac.auction.models.errors.CommonErrors.NotAuctionCreator
import io.scalac.auction.{ AuctionId, LotId, UserId }

import scala.collection.mutable.{ HashMap => MutHashMap }
import scala.concurrent.duration.FiniteDuration

object AuctionActor {
  def apply(id: AuctionId, userId: UserId): Behavior[Command] = Behaviors.setup { ctx =>
    new AuctionActor(ctx, id, userId).Closed
  }

  sealed trait Command
  final case class Open(duration: FiniteDuration) extends Command
  final case class AddLot(lotId: LotId, userId: UserId, startBid: BigDecimal, replyTo: ActorRef[LotAddedOrForbidden])
      extends Command
  final case class RemoveLot(lotId: LotId, replyTo: ActorRef[LotRemoved]) extends Command
  final case class MakeBid(
      lotId: LotId,
      userId: UserId,
      value: BigDecimal,
      replyTo: ActorRef[RespondBidOrFailure]
  ) extends Command
      with LotActor.Command
  final case class SetMaxBid(
      lotId: LotId,
      userId: UserId,
      value: BigDecimal,
      replyTo: ActorRef[RespondBidOrFailure]
  ) extends Command
      with LotActor.Command
  final case class GetWinningBid(id: LotId, replyTo: ActorRef[RespondWinningBid]) extends Command with LotActor.Command
  final case object Finish extends Command
  private final case class LotTerminated(lotId: LotId) extends Command

  final case class LotAddedOrForbidden(lotOrForbidden: Either[NotAuctionCreator.type, Lot])
  final case class LotRemoved(lotId: Option[LotId])
  final case class RespondBidOrFailure(bidOrFailure: Either[BidError, Bid])
  final case class RespondWinningBid(lotId: LotId, winner: UserId, value: BigDecimal)
}

class AuctionActor(ctx: ActorContext[AuctionActor.Command], id: AuctionId, creatorId: UserId) {
  import AuctionActor._

  ctx.log.info("Auction actor {}{} has been created", id.show, creatorId.show)
  private val lotsWithActors = MutHashMap.empty[LotId, LotWithActor]

  private lazy val Closed: Behavior[Command] =
    Behaviors.receiveMessagePartial {

      case AddLot(lotId, userId, startBid, replyTo) =>
        lotsWithActors.get(lotId).map(_.lot) match {
          case Some(lot) =>
            ctx.log.warn("Lot {} is already added", lotId.show)
            replyTo ! LotAddedOrForbidden(Right(lot))
            Behaviors.same

          case Some(_) | None if userId != creatorId =>
            ctx.log.warn("Forbidden to create an auction's lot by non-creator of the auction")
            replyTo ! LotAddedOrForbidden(Left(NotAuctionCreator))
            Behaviors.same

          case _ =>
            ctx.log.info("Adding lot {}", lotId.show)
            val actor = ctx.spawn(LotActor(lotId, id, creatorId, startBid), s"Lot-$lotId")
            ctx.watchWith(actor, LotTerminated(lotId))

            val lot = Lot(lotId, id, startBid)
            lotsWithActors += (lotId -> LotWithActor(lot, actor))
            replyTo ! LotAddedOrForbidden(Right(lot))
            Behaviors.same
        }

      case RemoveLot(lotId, replyTo) =>
        lotsWithActors.get(lotId) match {
          case Some(lotWithActor) =>
            ctx.log.info("Removing lot {}", lotId.show)
            ctx.stop(lotWithActor.actor)

            replyTo ! LotRemoved(Some(lotId))
            Behaviors.same

          case None =>
            ctx.log.warn("Lot {} doesn't exist", lotId.show)
            replyTo ! LotRemoved(None)
            Behaviors.same
        }

      case Open(duration) =>
        if (lotsWithActors.nonEmpty) {
          ctx.log.info("Auction {} is open", id.show)
          startInProgress(duration)
        } else
          Behaviors.same

      case LotTerminated(lotId) =>
        lotsWithActors -= lotId
        Behaviors.same
    }

  private lazy val InProgress: Behavior[Command] =
    Behaviors
      .receiveMessagePartial[Command] {

        case msg @ MakeBid(lotId, _, _, _) =>
          deliverToLotActor(lotId, msg)

        case msg @ SetMaxBid(lotId, _, _, _) =>
          deliverToLotActor(lotId, msg)

        case msg @ GetWinningBid(lotId, _) =>
          deliverToLotActor(lotId, msg)

        case Finish =>
          lotsWithActors.foreach { case (lotId, lotWithActor) =>
            lotsWithActors(lotId) = lotWithActor.modify(_.lot.isDeleted).setTo(true)
          }
          Finished

        case LotTerminated(lotId) =>
          lotsWithActors -= lotId
          Behaviors.same
      }
      .receiveSignal { case (_, PostStop) =>
        ctx.log.info("Auction actor {} stopped", id.show)
        Behaviors.same
      }

  private def startInProgress(duration: FiniteDuration): Behavior[Command] =
    Behaviors.withTimers { timers =>
      timers.startSingleTimer(Finish, Finish, duration)
      InProgress
    }

  private def deliverToLotActor(lotId: LotId, msg: LotActor.Command): Behavior[Command] = {
    lotsWithActors.get(lotId).map(_.actor) match {
      case Some(actor) =>
        actor ! msg

      case None =>
        ctx.log.warn("Lot {} isn't in the auction", lotId.show)
    }
    Behaviors.same
  }

  private lazy val Finished: Behavior[Command] =
    Behaviors.ignore
}
