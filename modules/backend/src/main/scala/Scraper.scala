/*
 * Copyright 2025 github.com/2m/lemonbusy/contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package lemonbusy

import java.util.concurrent.TimeoutException

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.util.Try

import TapirJsonBorer.{*, given}
import cats.data.EitherT
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.kernel.Temporal
import cats.effect.syntax.all.*
import cats.syntax.all.*
import com.themillhousegroup.scoup.Scoup
import fs2.io.net.Network
import org.http4s.ProductId
import org.http4s.Request
import org.http4s.Response
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.`User-Agent`
import org.http4s.implicits.*
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.metrics.Gauge
import org.typelevel.otel4s.metrics.Meter
import org.typelevel.otel4s.trace.StatusCode
import org.typelevel.otel4s.trace.Tracer
import sttp.tapir.*
import sttp.tapir.client.http4s.Http4sClientInterpreter
import sttp.tapir.generic.auto.*

object Lemon:
  val Timeout = 50.seconds
  val IdleTimeout = 1.minute

  val Uri = uri"https://www.lemongym.lt"

  val renderEndpoint =
    endpoint
      .in("wp-json" / "api" / "async-render-block")
      .in(query[String]("pid"))
      .in(query[String]("bid"))
      .in(query[String]("rest_language"))
      .out(jsonBody[Block])
      .errorOut(stringBody)

case class Block(data: Data, success: Boolean)
case class Data(content: String, success: Boolean)

def renderBlock[F[_]: Async]() =
  Http4sClientInterpreter[F]()
    .toRequest(Lemon.renderEndpoint, Some(Lemon.Uri))(
      "MTI2NQ==",
      "YWNmL2NsdWJzLW9jY3VwYW5jeQ==",
      "lt"
    )

enum AppError extends Throwable:
  case Decode[T](decodeResult: DecodeResult[T])
  case Upstream(upstreamError: String)
  case Parse(parseError: Throwable)
  case Timeout(timeoutError: Throwable)
  case Generic(error: Throwable)

def handleError[F[_]: Async: Tracer: Meter](result: Either[AppError, Unit]) =
  Tracer[F].currentSpanOrNoop.flatMap: span =>
    result match
      case Left(error) =>
        span.setStatus(StatusCode.Error) >> span.recordException(error) >> Async[F].delay(println(error))
      case Right(_) =>
        span.setStatus(StatusCode.Ok)

def runScraper[F[_]: Async: Tracer: Meter: Network](smoke: Boolean): Resource[F, Unit] =
  EmberClientBuilder
    .default[F]
    .withTimeout(Lemon.Timeout)
    .withIdleConnectionTime(Lemon.IdleTimeout)
    .withUserAgent(`User-Agent`(ProductId("github.com/2m/lemonbusy", Some(BuildInfo.version))))
    .build
    .map(Telemetry.tracedClient)
    .use: client =>
      val (request, parseResponse) = renderBlock()
      for
        occupoancyGauge <-
          Meter[F]
            .gauge[Long]("lemonbusy.occupancy")
            .withDescription("Occupancy in percentage")
            .create
        latencyGauge <-
          Meter[F]
            .gauge[Long]("lemonbusy.latency")
            .withDescription("Occupancy endpoint latency in seconds")
            .create
        _ <- foreverIf(!smoke)(
          Tracer[F]
            .rootSpan("scrape")
            .surround(
              fetchAndRecord(client, request, parseResponse, occupoancyGauge, latencyGauge).value
                .flatMap(handleError)
            )
        )
      yield ()
    .toResource

def foreverIf[F[_]: Async, T](forever: Boolean)(f: F[T]) =
  if forever then (f >> Temporal[F].sleep(Lemon.IdleTimeout * 2)).foreverM
  else f

def fetchAndRecord[F[_]: Async: Tracer](
    client: Client[F],
    request: Request[F],
    parseResponse: Response[F] => F[DecodeResult[Either[String, Block]]],
    occupoancyGauge: Gauge[F, Long],
    latencyGauge: Gauge[F, Long]
) =
  for
    (tookMs, response) <- EitherT(
      client
        .run(request)
        .use(
          parseResponse(_).map:
            case DecodeResult.Value(Right(value)) => Right(value)
            case DecodeResult.Value(Left(error))  => Left(AppError.Upstream(error))
            case other                            => Left(AppError.Decode(other))
        )
        .recover:
          case err: TimeoutException => Left(AppError.Timeout(err))
          case err                   => Left(AppError.Generic(err))
    ).timed
    info <- EitherT.fromEither(Try(parseOccupancy(response)).toEither.left.map(AppError.Parse.apply))
    _ <- EitherT.right(latencyGauge.record(tookMs.toSeconds, List.empty))
    _ <- EitherT.right(info.toList.traverse: (name, occupancy) =>
      occupoancyGauge.record(occupancy, List(Attribute("club", name))))
  yield ()

def parseOccupancy(block: Block) =
  Scoup
    .parseHTML(block.data.content)
    .select(".clubs-occupancy__club")
    .iterator()
    .asScala
    .toList
    .map: club =>
      val name = club.select(".xs-small").first.text.takeWhile(_ != '(').trim
      val occupancy =
        club.siblingElements().select(".clubs-occupancy__percentage").first.text.takeWhile(_ != '%').trim.toInt

      name -> occupancy
    .toMap
