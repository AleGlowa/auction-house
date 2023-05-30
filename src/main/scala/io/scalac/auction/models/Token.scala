package io.scalac.auction.models

import io.scalac.auction.controller.responses.TokenResponse

final case class Token(value: String) {

  def toResponse: TokenResponse = TokenResponse(value)
}
