package io.scalac.auction.controller.codecs

import io.scalac.auction.controller.responses.AuctionResponse
import argonaut._
import argonaut.Argonaut._
import io.scalac.auction.controller.requests.StartAuctionRequest

object AuctionCodecs {
  import CommonCodecs.{ auctionIdCodec, userIdCodec, finiteDurationDecoder }
  import LotCodecs.lotResponseEncoder

  implicit val auctionResponseEncoder: EncodeJson[AuctionResponse] =
    jencode5L((auction: AuctionResponse) =>
      (auction.id, auction.creatorId, auction.lots, auction.isOpen, auction.isDeleted)
    )(
      "id",
      "creatorId",
      "lots",
      "isOpen",
      "isDeleted"
    )
  implicit val startAuctionRequestDecoder: DecodeJson[StartAuctionRequest] =
    jdecode1L(StartAuctionRequest)("howLong")
}
