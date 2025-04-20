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

import scala.jdk.CollectionConverters.*

import cats.effect.Concurrent
import cats.effect.IO
import cats.effect.LiftIO
import cats.effect.kernel.Async
import cats.effect.kernel.Concurrent
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import cats.effect.syntax.all.*
import cats.effect.unsafe.IORuntime
import cats.implicits.*
import org.http4s.otel4s.middleware.trace.client.ClientMiddleware
import org.http4s.otel4s.middleware.trace.client.ClientSpanDataProvider
import org.http4s.otel4s.middleware.trace.client.UriRedactor
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.instrumentation.ce.IORuntimeMetrics
import org.typelevel.otel4s.metrics.Meter
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.oteljava.OtelJava
import org.typelevel.otel4s.oteljava.context.Context
import org.typelevel.otel4s.trace.Tracer
import org.typelevel.otel4s.trace.TracerProvider

object Telemetry:
  final val App = "lemonbusy"

  private def globalOtel[F[_]: Async: LiftIO](environment: String) = OtelJava.autoConfigured[F]: builder =>
    builder.addPropertiesSupplier(() =>
      (Map(
        "otel.java.global-autoconfigure.enabled" -> "true",
        "otel.service.name" -> s"$App-$environment"
      ) ++ sys.env
        .get("EXPORTER_ENDPOINT")
        .fold(Map())(endpoint => Map("otel.exporter.otlp.endpoint" -> endpoint))
        ++ sys.env
          .get("EXPORTER_HEADERS")
          .fold(Map())(headers => Map("otel.exporter.otlp.headers" -> headers))
        ++ sys.env
          .get("EXPORTER_PROTOCOL")
          .fold(Map())(protocol => Map("otel.exporter.otlp.protocol" -> protocol))).asJava
    )

  private def serviceName = if BuildInfo.isSnapshot then "local" else "production"

  def instruments(service: String) =
    for
      otel <- globalOtel[IO](service)
      provider = otel.tracerProvider
      tracer <- provider.get(App).toResource
      (given MeterProvider[IO]) = otel.meterProvider
      meter <- summon[MeterProvider[IO]].get(App).toResource
      _ <- IORuntimeMetrics.register[IO](IORuntime.global.metrics, IORuntimeMetrics.Config.default)
    yield (provider, tracer, meter)

  def instrument[A](entry: (TracerProvider[IO], Tracer[IO], Meter[IO]) ?=> Resource[IO, A]) =
    for
      (given TracerProvider[IO], given Tracer[IO], given Meter[IO]) <- instruments(serviceName)
      results <- entry
    yield results

  def tracedClient[F[_]: TracerProvider, Concurrent] = ClientMiddleware
    .builder(ClientSpanDataProvider.openTelemetry(new UriRedactor.OnlyRedactUserInfo {}))
    // .withClientSpanName(req => s"${req.method} ${req.uri}")
    .build

extension [A, F[_]: Tracer](f: F[A])
  def traced(name: String, attributes: Attribute[?]*): F[A] = Tracer[F].span(name, attributes).surround(f)
  def tracedR(name: String): Resource[F, A] = traced(name).toResource

extension [A, F[_]: Tracer: Sync](r: Resource[F, A])
  def rootSpan(name: String): Resource[F, A] = Resource.eval(Tracer[F].rootSpan(name).surround(r.use(_.pure[F])))
