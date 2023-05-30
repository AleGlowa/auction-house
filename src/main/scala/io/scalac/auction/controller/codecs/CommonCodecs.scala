package io.scalac.auction.controller.codecs

import io.scalac.auction.controller.responses.{ ActionResponse, ErrorResponse, TokenResponse }
import argonaut._
import argonaut.Argonaut._
import io.scalac.auction.{ AuctionId, LotId, UserId }

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

object CommonCodecs {

  implicit val auctionIdCodec: CodecJson[AuctionId] =
    CodecJson(auctionId => jString(auctionId.toStr), _.jdecode[String].map(AuctionId.apply))
  implicit val lotIdCodec: CodecJson[LotId] =
    CodecJson(lotId => jString(lotId.toStr), _.jdecode[String].map(LotId.apply))
  implicit val userIdCodec: CodecJson[UserId] =
    CodecJson(userId => jNumber(userId.toInt), _.jdecode[Int].map(UserId.apply))
  implicit val finiteDurationDecoder: DecodeJson[FiniteDuration] =
    _.jdecode[Long].map(len => FiniteDuration(len, TimeUnit.SECONDS))

  implicit val errorResponseEncoder: EncodeJson[ErrorResponse] =
    jencode1L[ErrorResponse, String](_.errMessage)("errMessage")
  implicit val actionResponseEncoder: EncodeJson[ActionResponse] =
    jencode1L[ActionResponse, String](_.description)("description")
  implicit val tokenResponseEncoder: EncodeJson[TokenResponse] =
    jencode1L[TokenResponse, String](_.token)("token")
}
