package io.scalac.auction.infrastructure.repositories.impl

import io.scalac.auction.UserId
import io.scalac.auction.infrastructure.repositories.UserRepository
import io.scalac.auction.infrastructure.tables.UsersTable
import io.scalac.auction.models.User
import slick.jdbc.{ JdbcBackend, JdbcProfile }

import scala.concurrent.{ ExecutionContext, Future }

class UserRepositoryImpl(db: JdbcBackend#Database, val profile: JdbcProfile)(implicit ec: ExecutionContext)
    extends UserRepository
    with UsersTable {
  import profile.api._

  override def getById(id: UserId): Future[Option[User]] =
    db.run(
      Users
        .filter(_.id === id.toInt)
        .result
        .headOption
    ).map(_.map(_.toUser))
}
