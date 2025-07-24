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

import cats.effect.Concurrent
import cats.effect.IO
import cats.effect.LiftIO
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import cats.effect.syntax.all.*
import cats.effect.unsafe.IORuntime
import cats.implicits.*
import org.http4s.Uri
import org.http4s.otel4s.middleware.client.UriTemplateClassifier
import org.http4s.otel4s.middleware.trace.client.ClientMiddleware
import org.http4s.otel4s.middleware.trace.client.ClientSpanDataProvider
import org.http4s.otel4s.middleware.trace.client.UriRedactor
import org.http4s.otel4s.middleware.trace.redact.HeaderRedactor
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.instrumentation.ce.IORuntimeMetrics
import org.typelevel.otel4s.metrics.Meter
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.sdk.OpenTelemetrySdk
import org.typelevel.otel4s.sdk.exporter.otlp.autoconfigure.OtlpExportersAutoConfigure
import org.typelevel.otel4s.trace.Tracer
import org.typelevel.otel4s.trace.TracerProvider

object Telemetry:
  final val App = "lemonbusy"

  private def telemetryProperties(config: Main.Config): Map[String, String] =
    config.telemetry match
      case Main.Config.Telemetry.Remote(endpoint, headers, protocol) =>
        Map(
          "otel.metrics.exporter" -> "otlp",
          "otel.traces.exporter" -> "otlp",
          "otel.exporter.otlp.endpoint" -> endpoint,
          "otel.exporter.otlp.headers" -> headers.getOrElse(""),
          "otel.exporter.otlp.protocol" -> protocol
        )
      case Main.Config.Telemetry.Console() =>
        Map(
          "otel.metrics.exporter" -> "console",
          "otel.traces.exporter" -> "console"
        )

  private def globalOtel(config: Main.Config) = OpenTelemetrySdk.autoConfigured[IO]: builder =>
    builder
      .addExportersConfigurer(OtlpExportersAutoConfigure[IO])
      .addPropertiesLoader(
        Map("otel.service.name" -> s"$App-${if config.production then "production" else "local"}").pure[IO]
      )
      .addPropertiesLoader(telemetryProperties(config).pure[IO])

  def instruments(config: Main.Config) =
    for
      otel <- globalOtel(config)
      provider = otel.sdk.tracerProvider
      tracer <- provider.get(App).toResource
      (given MeterProvider[IO]) = otel.sdk.meterProvider
      meter <- summon[MeterProvider[IO]].get(App).toResource
      _ <- IORuntimeMetrics.register[IO](IORuntime.global.metrics, IORuntimeMetrics.Config.default)
    yield (provider, tracer, meter)

  def instrument[A](config: Main.Config, entry: (TracerProvider[IO], Tracer[IO], Meter[IO]) ?=> Resource[IO, A]) =
    for
      (given TracerProvider[IO], given Tracer[IO], given Meter[IO]) <- instruments(config)
      results <- entry
    yield results

  def tracedClient[F[_]: TracerProvider: Concurrent] = ClientMiddleware
    .builder(
      ClientSpanDataProvider
        .openTelemetry(new UriRedactor.OnlyRedactUserInfo {})
        .withUrlTemplateClassifier(
          new UriTemplateClassifier:
            override def classify(uri: Uri): Option[String] = Some(uri.path.toString)
        )
        .optIntoHttpResponseHeaders(HeaderRedactor.default)
        .optIntoUrlScheme
        .optIntoUrlTemplate
    )
    .build
    .toResource

extension [A, F[_]: Tracer](f: F[A])
  def traced(name: String, attributes: Attribute[?]*): F[A] = Tracer[F].span(name, attributes).surround(f)
  def tracedR(name: String): Resource[F, A] = traced(name).toResource

extension [A, F[_]: Tracer: Sync](r: Resource[F, A])
  def rootSpan(name: String): Resource[F, A] = Resource.eval(Tracer[F].rootSpan(name).surround(r.use(_.pure[F])))
