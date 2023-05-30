package io.scalac.auction.infrastructure.repositories

import io.scalac.auction.UserId
import io.scalac.auction.models.User

import scala.concurrent.Future

trait UserRepository {
  def getById(id: UserId): Future[Option[User]]
}
