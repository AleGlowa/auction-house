package io.scalac.auction.models.errors

import io.scalac.auction.controller.responses.ErrorResponse

trait DomainError {

  def message: String

  def toResponse: ErrorResponse = ErrorResponse(message)
}
