package io.scalac.auction.controller.codecs

import argonaut._
import Argonaut._
import CommonCodecs._
import io.scalac.auction.controller.responses.{ ActionResponse, ErrorResponse, TokenResponse }

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class CommonCodecsSpec extends CodecTester {

  private val auctionId = AuctionId(1)
  private val auctionIdJson = JsonLong(1).asJson

  private val lotId = LotId(1)
  private val lotIdJson = JsonLong(1).asJson

  private val userId = UserId(1)
  private val userIdJson = JsonLong(1).asJson

  private val finiteDuration = FiniteDuration(1, TimeUnit.SECONDS)
  private val finiteDurationJson = JsonLong(1).asJson

  private val errorResponse = ErrorResponse("error")
  private val errorResponseJson =
    Json("errMessage" := "error")

  private val actionResponse = ActionResponse("success")
  private val actionResponseJson =
    Json("description" := "success")

  private val tokenResponse = TokenResponse("7836423tbc")
  private val tokenResponseJson =
    Json("token" := "7836423tbc")

  "CommonCodecs should" - {

    "encode AuctionId to Json" in {
      checkObjToJson(auctionId, auctionIdJson)
    }
    "decode Json to AuctionId" in {
      checkJsonToObj(auctionIdJson, auctionId)
    }
    "encode LotId to Json" in {
      checkObjToJson(lotId, lotIdJson)
    }
    "decode Json to LotId" in {
      checkJsonToObj(lotIdJson, lotId)
    }
    "encode UserId to Json" in {
      checkObjToJson(userId, userIdJson)
    }
    "decode Json to UserId" in {
      checkJsonToObj(userIdJson, userId)
    }
    "decode Json to FiniteDuration" in {
      checkJsonToObj(finiteDurationJson, finiteDuration)
    }
    "encode ErrorResponse to Json" in {
      checkObjToJson(errorResponse, errorResponseJson)
    }
    "encode ActionResponse to Json" in {
      checkObjToJson(actionResponse, actionResponseJson)
    }
    "encode TokenResponse to Json" in {
      checkObjToJson(tokenResponse, tokenResponseJson)
    }
  }
}
