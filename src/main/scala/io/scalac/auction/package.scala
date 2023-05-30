package io.scalac

import akka.util.Helpers.base64
import com.typesafe.config.{ Config, ConfigFactory }
import io.estatico.newtype.macros.newtype

import java.time.Duration
import scala.util.Random

package object auction {

  val config: Config = ConfigFactory.load()

  val HttpPort: Int = config.getInt("httpPort")
  val JwtSecret: String = config.getString("jwt.secret")
  val JwtExpiresIn: Duration = config.getDuration("jwt.expiresIn")

  def getRandomBase64(from: Long, to: Long): String =
    base64(Random.between(from, to), new java.lang.StringBuilder())

  @newtype case class LotId(toStr: String) {
    def show: String = s"[lot-id=$toStr]"
  }
  @newtype case class AuctionId(toStr: String) {
    def show: String = s"[auction-id=$toStr]"
  }
  @newtype case class UserId(toInt: Int) {
    def show: String = s"[user-id=$toInt]"
  }
}
