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

import java.nio.file.Files

import TapirJsonBorer.given
import io.bullet.borer.Json
import lemonbusy.ParserSuite.getBlockResponse

class ParserSuite extends munit.FunSuite:

  test("parses response"):
    val obtained = parseOccupancy(getBlockResponse())
    val expected = Map(
      "Savanoriai" -> 44,
      "Banginis" -> 42,
      "Perkūnkiemis" -> 38,
      "Antakalnis" -> 29,
      "Fabijoniškės" -> 45,
      "Vienuolis" -> 37,
      "Pilaitė" -> 20,
      "Urmas" -> 39,
      "Skraja" -> 28,
      "Europa" -> 45,
      "Asanavičiūtė" -> 26,
      "Šilainiai" -> 47,
      "Žalgirio arena" -> 36,
      "Ozas" -> 17
    )
    assertEquals(obtained, expected)

object ParserSuite:
  def getBlockResponse() =
    Json
      .decode(
        Files.readString(BuildInfo.test_resourceDirectory.toPath().resolve("response.json")).getBytes
      )
      .to[Block]
      .value
