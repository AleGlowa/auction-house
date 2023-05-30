package io.scalac.auction.controller.codecs

import argonaut._
import argonaut.Argonaut._
import io.scalac.auction.controller.responses.LotResponse
import io.scalac.auction.controller.requests.CreateLotRequest

object LotCodecs {
  import CommonCodecs.{ lotIdCodec, auctionIdCodec, userIdCodec }

  implicit val lotResponseEncoder: EncodeJson[LotResponse] =
    jencode6L((lot: LotResponse) => (lot.id, lot.auctionId, lot.startBid, lot.winner, lot.winningBid, lot.isDeleted))(
      "id",
      "auctionId",
      "startBid",
      "winner",
      "winningBid",
      "isDeleted"
    )
  implicit val createLotRequestDecoder: DecodeJson[CreateLotRequest] =
    jdecode2L(CreateLotRequest)("auctionId", "startBid")
}
