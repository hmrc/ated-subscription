/*
 * Copyright 2019 HM Revenue & Customs
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

package models

import play.api.libs.json.Json

//TODO: rename this to EMACModel later

case class Verifier(key: String, value: String)

object Verifier {
  implicit val formats = Json.format[Verifier]
}

case class Verifiers(verifiers: List[Verifier])

object Verifiers {
  implicit val formats = Json.format[Verifiers]
}

case class KnownFact(`type`: String, value: String)

object KnownFact {
  implicit val formats = Json.format[KnownFact]
}


case class KnownFactsForService(facts: List[KnownFact])

object KnownFactsForService {
  implicit val formats = Json.format[KnownFactsForService]
}
