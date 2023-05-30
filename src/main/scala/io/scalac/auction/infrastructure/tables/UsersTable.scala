package io.scalac.auction.infrastructure.tables

import io.scalac.auction.infrastructure.models.UserRow

trait UsersTable {
  val profile: slick.jdbc.JdbcProfile

  import profile.api._

  /** Table description of table users. Objects of this class serve as prototypes for rows in queries. */
  class Users(_tableTag: Tag) extends Table[UserRow](_tableTag, Some("auction_house"), "users") {
    def * = id <> (UserRow.apply, UserRow.unapply)

    /** Database column id SqlType(integer), PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.PrimaryKey)
  }

  /** Collection-like TableQuery object for table Users */
  lazy val Users = new TableQuery(tag => new Users(tag))
}
