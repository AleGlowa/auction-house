package io.scalac.auction.controller

import akka.http.scaladsl.unmarshalling.Unmarshaller
import io.scalac.auction.{ AuctionId, LotId, UserId }
import akka.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers.intFromStringUnmarshaller

import scala.concurrent.Future

trait CustomUnmarshallers {

  implicit val fromStringToUserIdUnmarshaller: Unmarshaller[String, UserId] =
    intFromStringUnmarshaller.transform(implicit ec => _ => int => int.map(UserId.apply))

  implicit val fromStringToAuctionIdUnmarshaller: Unmarshaller[String, AuctionId] =
    Unmarshaller[String, AuctionId](implicit ec => string => Future(AuctionId(string)))

  implicit val fromStringToLotIdUnmarshaller: Unmarshaller[String, LotId] =
    Unmarshaller[String, LotId](implicit ec => string => Future(LotId(string)))
}
