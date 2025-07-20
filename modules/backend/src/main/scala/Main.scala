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

import cats.effect.ExitCode
import cats.effect.IO
import cats.implicits.*
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp

object Main
    extends CommandIOApp(
      name = "lemonbusy",
      header = "Availability scraper for certain Lemon gym",
      version = BuildInfo.version
    ):

  case class Config(production: Boolean, telemetry: Config.Telemetry)
  object Config:
    sealed trait Telemetry
    object Telemetry:
      val endpoint = Opts.option[String]("exporter-endpoint", "")
      val headers = Opts.option[String]("exporter-headers", "")
      val protocol = Opts.option[String]("exporter-protocol", "")

      case class Remote(endpoint: String, headers: Option[String], protocol: String) extends Telemetry
      case class Console() extends Telemetry

      val opts = (endpoint, headers.orNone, protocol).mapN(Remote.apply).withDefault(Console())

    val production = Opts.flag("production", "").orFalse
    val opts = (production, Telemetry.opts).mapN(Config.apply)

  case class Scraper(config: Config)
  case class SmokeRun(config: Config)

  val scraper: Opts[Scraper] =
    Opts.subcommand("scraper", "Runs availability scraper.") {
      Config.opts.map(Scraper.apply)
    }

  val smokeRun: Opts[SmokeRun] =
    Opts.subcommand("smoke-run", "Exercice all functionality of the app.") {
      Config.opts.map(SmokeRun.apply)
    }

  override def main: Opts[IO[ExitCode]] =
    (scraper orElse smokeRun)
      .map {
        case Scraper(config) =>
          Telemetry.instrument(config, runScraper[IO](smoke = false)).use(_ => IO.never)
        case SmokeRun(config) =>
          Telemetry.instrument(config, runScraper[IO](smoke = true)).use(_ => ExitCode.Success.pure[IO])
      }
