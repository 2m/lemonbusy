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
      header = "Availability scraper for certain Lemon gym"
    ):

  case class Scraper()
  case class SmokeRun()

  val scraper: Opts[Scraper] =
    Opts.subcommand("scraper", "Runs availability scraper.") {
      Opts.unit.map(_ => Scraper())
    }

  val smokeRun: Opts[SmokeRun] =
    Opts.subcommand("smoke-run", "Exercice all functionality of the app.") {
      Opts.unit.map(_ => SmokeRun())
    }

  override def main: Opts[IO[ExitCode]] =
    (scraper orElse smokeRun)
      .map {
        case Scraper()  => Telemetry.instrument(runScraper[IO](smoke = false)).use(_ => IO.never)
        case SmokeRun() => Telemetry.instrument(runScraper[IO](smoke = true)).use(_ => ExitCode.Success.pure[IO])
      }
