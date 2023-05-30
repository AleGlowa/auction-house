package io.scalac.auction.controller.codecs

import argonaut.Argonaut.ToJsonIdentity
import argonaut.{ DecodeJson, EncodeJson, Json }
import org.scalatest.Assertion
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

trait CodecTester extends AnyFreeSpec with Matchers {

  protected def checkObjToJson[T: EncodeJson](obj: T, expectedJson: Json): Assertion =
    obj.asJson shouldBe expectedJson

  protected def checkJsonToObj[T: DecodeJson](json: Json, expectedObj: T): Assertion =
    json.as[T].fold((s, _) => throw new Exception(s), _ shouldBe expectedObj)
}
