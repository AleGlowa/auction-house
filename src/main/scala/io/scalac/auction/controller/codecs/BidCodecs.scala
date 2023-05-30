package io.scalac.auction.controller.codecs

import argonaut._
import argonaut.Argonaut._
import io.scalac.auction.controller.responses.BidResponse
import io.scalac.auction.controller.requests.BidLotRequest

object BidCodecs {
  import CommonCodecs.{ lotIdCodec, userIdCodec, auctionIdCodec }

  implicit val bidResponseEncoder: EncodeJson[BidResponse] =
    jencode5L((bid: BidResponse) => (bid.auctionId, bid.lotId, bid.userId, bid.value, bid.max))(
      "auctionId",
      "lotId",
      "userId",
      "value",
      "max"
    )
  implicit val bidLotRequestDecoder: DecodeJson[BidLotRequest] =
    jdecode2L(BidLotRequest)("value", "isMax")
}
