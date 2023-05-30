package io.scalac.auction.controller

import akka.http.scaladsl.model.{ HttpResponse, StatusCode, StatusCodes }
import akka.http.scaladsl.marshalling.{ Marshaller, ToResponseMarshaller }
import argonaut._
import de.heikoseeberger.akkahttpargonaut.ArgonautSupport
import io.scalac.auction.controller.responses.ErrorResponse
import io.scalac.auction.models.errors.CommonErrors.{
  BadRequestError,
  ForbiddenError,
  NotFoundError,
  UnauthorizedError
}
import io.scalac.auction.models.errors.DomainError

trait CustomMarshallers extends ArgonautSupport {

  implicit def respMarshaller[R](implicit encoder: EncodeJson[R]): ToResponseMarshaller[R] =
    marshaller[R].map(respMessage => HttpResponse(entity = respMessage))

  implicit def failureMarshaller(
      failureCode: StatusCode
  )(implicit encoder: EncodeJson[ErrorResponse]): ToResponseMarshaller[DomainError] =
    marshaller[ErrorResponse]
      .map(error => HttpResponse(status = failureCode, entity = error))
      .compose(_.toResponse)

  implicit def eitherMarshaller[R](implicit
      failureM: StatusCode => ToResponseMarshaller[DomainError],
      successM: ToResponseMarshaller[R]
  ): ToResponseMarshaller[Either[DomainError, R]] =
    Marshaller { implicit ec =>
      _.fold(
        {
          case notFound: NotFoundError => failureM(StatusCodes.NotFound)(notFound)
          case badRequest: BadRequestError => failureM(StatusCodes.BadRequest)(badRequest)
          case forbidden: ForbiddenError => failureM(StatusCodes.Forbidden)(forbidden)
          case unauthorized: UnauthorizedError => failureM(StatusCodes.Unauthorized)(unauthorized)
        },
        successM.apply
      )
    }
}
