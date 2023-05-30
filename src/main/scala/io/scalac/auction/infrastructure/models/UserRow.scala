package io.scalac.auction.infrastructure.models

import io.scalac.auction.UserId
import io.scalac.auction.models.User

/** Entity class storing rows of table Users
  *
  * @param id
  *   Database column id SqlType(integer), PrimaryKey
  */
final case class UserRow(id: Int) {
  def toUser: User = User(UserId(id))
}
