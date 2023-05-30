package io.scalac.auction.actors

import akka.actor.typed.{ ActorRef, Behavior }
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior, ReplyEffect }
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityTypeKey }
import com.softwaremill.quicklens._
import io.scalac.auction.CborSerializable
import io.scalac.auction.actors.AuctionActor.{ AddLot, Finish, MakeBid, Open, SetMaxBid }
import io.scalac.auction.controller.responses.ActionResponse
import io.scalac.auction.models.{ AuctionWithActor, Auction, Bid, Lot }
import io.scalac.auction.models.errors.AuctionError.{ AuctionNotFound, AuctionStartWithoutLots }
import io.scalac.auction.models.errors.BidError.{ CreatorBet, LotBiddingInClosed }
import io.scalac.auction.models.errors.CommonErrors.NotAuctionCreator
import io.scalac.auction.models.errors.DomainError
import io.scalac.auction.models.errors.LotError.{ LotCreationInProgress, LotNotFound }
import io.scalac.auction.{ AuctionId, LotId, UserId }

import scala.collection.immutable.HashMap
import scala.concurrent.duration.FiniteDuration

object AuctionManagerActor {

  val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("AuctionManager")

  def initSharding(sharding: ClusterSharding): Unit =
    sharding.init(
      Entity(TypeKey)(entityCtx =>
        running(entityCtx.entityId, PersistenceId(entityCtx.entityTypeKey.name, entityCtx.entityId))
      )
    )

  private def running(entityId: String, persistentId: PersistenceId): Behavior[Command] = Behaviors.setup { ctx =>
    new AuctionManagerActor(ctx, entityId, persistentId).init()
  }

  // State
  final case class State(auctionsWithActors: HashMap[AuctionId, AuctionWithActor]) extends CborSerializable

  // Commands
  sealed trait Command extends CborSerializable
  final case class CreateAuction(auction: Auction, replyTo: ActorRef[CreatedAuction]) extends Command
  final case class GetAuctions(replyTo: ActorRef[GotAuctions]) extends Command
  final case class GetAuction(
      auctionId: AuctionId,
      replyTo: ActorRef[GotAuctionOrNotFound],
      excludeIsDeleted: Boolean = true
  ) extends Command
  final case class StartAuction(
      auctionId: AuctionId,
      userId: UserId,
      duration: FiniteDuration,
      replyTo: ActorRef[ActionResult]
  ) extends Command
  final case class EndAuction(auctionId: AuctionId, userId: UserId, replyTo: ActorRef[ActionResult]) extends Command
  final case class CreateLot(lot: Lot, userId: UserId, replyTo: ActorRef[LotCreatedOrFailure]) extends Command
  final case class GetLot(lotId: LotId, replyTo: ActorRef[GotLotOrNotFound], excludeIsDeleted: Boolean = true)
      extends Command
  final case class BidLot(
      lotId: LotId,
      userId: UserId,
      value: BigDecimal,
      isMax: Boolean,
      replyTo: ActorRef[BetLotOrFailure]
  ) extends Command
  private final case class WrappedLotAddedResponse(
      response: AuctionActor.LotAddedOrForbidden,
      replyTo: ActorRef[LotCreatedOrFailure]
  ) extends Command
  private final case class WrappedBidOrFailureResponse(
      response: AuctionActor.RespondBidOrFailure,
      replyTo: ActorRef[BetLotOrFailure]
  ) extends Command

  // Replies
  sealed trait Reply
  final case class CreatedAuction(auction: Auction) extends Reply
  final case class GotAuctions(auctions: Set[Auction]) extends Reply
  final case class GotAuctionOrNotFound(auctionOrNotFound: Either[AuctionNotFound, Auction]) extends Reply
  sealed trait SuccessfulResult {

    val description: String

    def toResponse: ActionResponse = ActionResponse(description)
  }
  final case class ActionResult(res: Either[DomainError, SuccessfulResult]) extends Reply
  final case class SuccessRes(description: String) extends SuccessfulResult
  final case class NoChangeRes(description: String) extends SuccessfulResult
  final case class LotCreatedOrFailure(lotOrFailure: Either[DomainError, Lot]) extends Reply
  final case class GotLotsOrNotFound(lotsOrNotFound: Either[AuctionNotFound, Set[Lot]]) extends Reply
  final case class GotLotOrNotFound(lotOrNotFound: Either[LotNotFound, Lot]) extends Reply
  final case class BetLotOrFailure(bidOrFailure: Either[DomainError, Bid]) extends Reply

  // Events
  sealed trait Event extends CborSerializable
  final case class AuctionCreated(auctionWithActor: AuctionWithActor) extends Event
  final case class AuctionStarted(auctionWithActor: AuctionWithActor) extends Event
  final case class AuctionEnded(auctionWithActor: AuctionWithActor) extends Event
  final case class LotCreated(auctionWithActor: AuctionWithActor, lot: Lot) extends Event
  final case class LotBet(auctionWithActor: AuctionWithActor, bid: Bid) extends Event
}

class AuctionManagerActor(
    ctx: ActorContext[AuctionManagerActor.Command],
    entityId: String,
    persistentId: PersistenceId
) {
  import AuctionManagerActor._

  private implicit val lotAddedResponseMapper: ActorRef[LotCreatedOrFailure] => ActorRef[
    AuctionActor.LotAddedOrForbidden
  ] =
    replyTo => ctx.messageAdapter(WrappedLotAddedResponse(_, replyTo))
  private implicit val bidOrFailureResponseMapper: ActorRef[BetLotOrFailure] => ActorRef[
    AuctionActor.RespondBidOrFailure
  ] =
    replyTo => ctx.messageAdapter(WrappedBidOrFailureResponse(_, replyTo))

  ctx.log.info("Auction Manager actor {} has been created", entityId)

  private def init(): EventSourcedBehavior[Command, Event, State] =
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, State](
        persistentId,
        State(HashMap.empty),
        (state, cmd) => {
          ctx.log.info("Receiving command {} for auction manager {}", cmd, entityId)
          handleCommands(state, cmd)
        },
        handleEvents
      )
      .withTagger {
        case _: AuctionCreated => Set("auction", "created")
        case _: AuctionStarted => Set("auction", "started")
        case _: AuctionEnded => Set("auction", "ended")
        case _: LotCreated => Set("lot", "created")
        case _: LotBet => Set("lot", "bet")
      }
      .snapshotWhen {
        case (_, _: AuctionEnded, _) => true
        case _ => false
      }

  private def handleCommands(state: State, cmd: Command): ReplyEffect[Event, State] =
    cmd match {

      case CreateAuction(auction, replyTo) =>
        val auctionId = auction.id
        ctx.log.info("Creating an auction {}", auctionId.show)
        val actor = ctx.spawn(auction.toBehavior, s"Auction-$auctionId")

        Effect
          .persist(AuctionCreated(AuctionWithActor(auction, actor)))
          .thenReply(replyTo)(_ => CreatedAuction(auction))

      case GetAuctions(replyTo) =>
        ctx.log.info("Getting all auctions")
        val allAuctions =
          state.auctionsWithActors.values
            .filterNot(_.auction.isDeleted)
            .map(_.auction)
            .toSet
        Effect.reply(replyTo)(GotAuctions(allAuctions))

      case GetAuction(auctionId, replyTo, excludeIsDeleted) =>
        state.auctionsWithActors.get(auctionId).map(_.auction) match {
          case Some(auction) if !excludeIsDeleted || !auction.isDeleted =>
            ctx.log.info("Getting the auction {}", auctionId.show)
            Effect.reply(replyTo)(GotAuctionOrNotFound(Right(auction)))

          case _ =>
            ctx.log.warn("Auction {} doesn't exist", auctionId.show)
            Effect.reply(replyTo)(GotAuctionOrNotFound(Left(AuctionNotFound(auctionId))))
        }

      case StartAuction(auctionId, userId, duration, replyTo) =>
        state.auctionsWithActors.get(auctionId) match {
          case Some(auctionWithActor) if auctionWithActor.auction.creatorId != userId =>
            ctx.log.warn("Forbidden to start an auction by non-creator of the auction")
            Effect.reply(replyTo)(ActionResult(Left(NotAuctionCreator)))

          case Some(auctionWithActor) if auctionWithActor.auction.isOpen =>
            Effect.reply(replyTo)(
              ActionResult(
                Right(NoChangeRes(s"Auction ${auctionId.show} is already started. No actions performed"))
              )
            )

          case Some(auctionWithActor) if auctionWithActor.auction.lots.isEmpty =>
            Effect.reply(replyTo)(ActionResult(Left(AuctionStartWithoutLots(auctionId))))

          case None =>
            Effect.reply(replyTo)(ActionResult(Left(AuctionNotFound(auctionId))))

          case Some(auctionWithActor) =>
            auctionWithActor.actor ! Open(duration)
            Effect
              .persist(AuctionStarted(auctionWithActor))
              .thenReply(replyTo)(_ => ActionResult(Right(SuccessRes(s"Auction ${auctionId.show} has been started"))))
        }

      case EndAuction(auctionId, userId, replyTo) =>
        state.auctionsWithActors.get(auctionId) match {
          case Some(auctionWithActor) if auctionWithActor.auction.creatorId != userId =>
            ctx.log.warn("Forbidden to end an auction by non-creator of the auction")
            Effect.reply(replyTo)(ActionResult(Left(NotAuctionCreator)))

          case Some(auctionWithActor) if !auctionWithActor.auction.isOpen =>
            Effect.reply(replyTo)(
              ActionResult(
                Right(NoChangeRes(s"Auction ${auctionId.show} isn't started. No actions performed"))
              )
            )

          case None =>
            Effect.reply(replyTo)(ActionResult(Left(AuctionNotFound(auctionId))))

          case Some(auctionWithActor) =>
            auctionWithActor.actor ! Finish
            Effect
              .persist(AuctionEnded(auctionWithActor))
              .thenReply(replyTo)(_ => ActionResult(Right(SuccessRes(s"Auction ${auctionId.show} has been ended"))))
        }

      case CreateLot(lot, userId, replyTo) =>
        state.auctionsWithActors.get(lot.auctionId) match {
          case Some(auctionWithActor) if auctionWithActor.auction.isOpen =>
            Effect.reply(replyTo)(LotCreatedOrFailure(Left(LotCreationInProgress(lot.auctionId))))

          case None =>
            Effect.reply(replyTo)(LotCreatedOrFailure(Left(AuctionNotFound(lot.auctionId))))

          case Some(auctionWithActor) =>
            auctionWithActor.actor ! AddLot(
              lot.id,
              userId,
              lot.startBid,
              lotAddedResponseMapper(replyTo)
            )
            Effect.noReply
        }

      case GetLot(lotId, replyTo, excludeIsDeleted) =>
        val lotOpt = state.auctionsWithActors.values.flatMap(_.auction.lots).find(_.id == lotId)

        lotOpt match {
          case Some(lot) if !excludeIsDeleted || !lot.isDeleted =>
            ctx.log.info("Getting the lot {}", lotId.show)
            Effect.reply(replyTo)(GotLotOrNotFound(Right(lot)))

          case _ =>
            ctx.log.warn("Lot {} doesn't exist", lotId.show)
            Effect.reply(replyTo)(GotLotOrNotFound(Left(LotNotFound(lotId))))
        }

      case BidLot(lotId, userId, value, isMax, replyTo) =>
        val lotOpt = state.auctionsWithActors.values.flatMap(_.auction.lots).find(_.id == lotId)
        val auctionOpt = lotOpt.map(lot => state.auctionsWithActors(lot.auctionId).auction)

        auctionOpt match {
          case Some(auction) if auction.creatorId == userId =>
            ctx.log.warn("Forbidden to bid an auction's lot by creator of the auction")
            Effect.reply(replyTo)(BetLotOrFailure(Left(CreatorBet)))

          case Some(auction) if !auction.isOpen =>
            ctx.log.warn("Can't bid a lot when the auction's closed")
            Effect.reply(replyTo)(BetLotOrFailure(Left(LotBiddingInClosed(auction.id))))

          case Some(_) if lotOpt.get.isDeleted =>
            ctx.log.warn("Lot {} doesn't exist", lotId.show)
            Effect.reply(replyTo)(BetLotOrFailure(Left(LotNotFound(lotId))))

          case None =>
            ctx.log.warn("Lot {} doesn't exist", lotId.show)
            Effect.reply(replyTo)(BetLotOrFailure(Left(LotNotFound(lotId))))

          case Some(_) =>
            val actor = state.auctionsWithActors(lotOpt.get.auctionId).actor
            if (isMax)
              actor ! SetMaxBid(
                lotId,
                userId,
                value,
                bidOrFailureResponseMapper(replyTo)
              )
            else
              actor ! MakeBid(lotId, userId, value, bidOrFailureResponseMapper(replyTo))
            Effect.noReply
        }

      case WrappedLotAddedResponse(resp, replyTo) =>
        resp.lotOrForbidden match {
          case failure @ Left(_) =>
            Effect.reply(replyTo)(LotCreatedOrFailure(failure))

          case Right(lot) =>
            val auctionWithActor = state.auctionsWithActors(lot.auctionId)
            Effect.persist(LotCreated(auctionWithActor, lot)).thenReply(replyTo)(_ => LotCreatedOrFailure(Right(lot)))
        }

      case WrappedBidOrFailureResponse(resp, replyTo) =>
        resp.bidOrFailure match {
          case failure @ Left(_) =>
            Effect.reply(replyTo)(BetLotOrFailure(failure))

          case Right(bid) =>
            replyTo ! BetLotOrFailure(Right(bid))
            val auctionWithActor = state.auctionsWithActors(bid.auctionId)
            val lot = auctionWithActor.auction.lots.find(_.id == bid.lotId).get
            if (lot.winningBid.forall(bid.value > _))
              Effect.persist(LotBet(auctionWithActor, bid)).thenReply(replyTo)(_ => BetLotOrFailure(Right(bid)))
            else Effect.noReply
        }
    }

  private def handleEvents(state: State, event: Event) =
    event match {

      case AuctionCreated(auctionWithActor) =>
        state.modify(_.auctionsWithActors).using(_ + (auctionWithActor.auction.id -> auctionWithActor))

      case e @ AuctionStarted(AuctionWithActor(auction, _)) =>
        state
          .modify(_.auctionsWithActors.at(auction.id))
          .setTo(
            e.auctionWithActor
              .modify(_.auction.isOpen)
              .setTo(true)
          )

      case e @ AuctionEnded(AuctionWithActor(auction, _)) =>
        state
          .modify(_.auctionsWithActors.at(auction.id))
          .setTo(
            e.auctionWithActor
              .modifyAll(_.auction.isDeleted, _.auction.isOpen)
              .using(!_)
          )

      case e @ LotCreated(AuctionWithActor(auction, _), lot) =>
        state
          .modify(_.auctionsWithActors.at(auction.id))
          .setTo(
            e.auctionWithActor
              .modify(_.auction.lots)
              .using(_ + lot)
          )

      case e @ LotBet(AuctionWithActor(auction, _), bid) =>
        val lot = auction.lots.find(_.id == bid.lotId).get
        state
          .modify(_.auctionsWithActors.at(auction.id))
          .setTo(
            e.auctionWithActor
              .modify(_.auction.lots)
              .using(
                _ - lot +
                  lot
                    .modify(_.winner)
                    .setTo(Some(bid.userId))
                    .modify(_.winningBid)
                    .setTo(Some(bid.value))
              )
          )
    }
}
