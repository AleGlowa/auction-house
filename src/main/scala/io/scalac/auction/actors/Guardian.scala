package io.scalac.auction.actors

import akka.actor.typed.{ ActorSystem, Behavior }
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import io.scalac.auction.controller.{ AuctionController, AuthController, LotController }
import io.scalac.auction.infrastructure.repositories.impl.UserRepositoryImpl
import io.scalac.auction.services.impl.{ AuctionServiceImpl, LotServiceImpl }
import io.scalac.auction.services.impl.AuthServiceImpl
import slick.jdbc.{ JdbcBackend, PostgresProfile }

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.io.StdIn
import scala.util.{ Failure, Success }

object Guardian {

  def apply(httpPort: Int): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
    new Guardian(ctx).bootstrap(httpPort)
  }
}

class Guardian(ctx: ActorContext[Nothing]) {

  private implicit val system: ActorSystem[Nothing] = ctx.system
  private implicit val ec: ExecutionContextExecutor = system.executionContext
  private val sharding = ClusterSharding(system)

  def bootstrap(httpPort: Int): Behavior[Nothing] = {

    AuctionManagerActor.initSharding(sharding)
    val auctionManagerActor = sharding.entityRefFor(AuctionManagerActor.TypeKey, "1")

    val userRepo = new UserRepositoryImpl(
      JdbcBackend.Database.forConfig("slick.db", system.settings.config),
      PostgresProfile
    )

    val auctionService = new AuctionServiceImpl(auctionManagerActor)
    val lotService = new LotServiceImpl(auctionManagerActor)
    val authService = new AuthServiceImpl(userRepo)

    val auctionRoutes = new AuctionController(auctionService).routes
    val lotRoutes = new LotController(lotService).routes
    val authRoutes = new AuthController(authService).routes
    startHttpServer(Route.seal(authRoutes ~ auctionRoutes ~ lotRoutes), httpPort)

    Behaviors.empty
  }

  private def startHttpServer(routes: Route, httpPort: Int): Unit = {

    val binding = Http().newServerAt("localhost", httpPort).bind(routes)

    binding
      .map(_.addToCoordinatedShutdown(10.seconds))
      .onComplete {
        case Success(binding) =>
          val address = binding.localAddress
          system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
          system.log.info("Press enter key to stop...")
          StdIn.readLine()
          stopHttpServer(binding)

        case Failure(ex) =>
          system.log.error("Failed to bind HTTP endpoint, terminating the system", ex)
          system.terminate()
      }
  }

  private def stopHttpServer(binding: ServerBinding): Unit =
    binding
      .terminate(10.seconds)
      .onComplete {
        case Success(_) =>
          system.log.info("The server stopped gracefully")
          system.terminate()

        case Failure(ex) =>
          system.log.error("Failed to gracefully terminate the server", ex)
          system.terminate()
      }
}
