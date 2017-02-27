/*
 * Copyright 2017 HM Revenue & Customs
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

package services

import connectors.{ETMPConnector, GovernmentGatewayAdminConnector}
import models.{KnownFact, KnownFactsForService}
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.{JsObject, JsValue}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import utils.GovernmentGatewayConstants

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object SubscribeService extends SubscribeService {
  val etmpConnector: ETMPConnector = ETMPConnector
  val ggAdminConnector: GovernmentGatewayAdminConnector = GovernmentGatewayAdminConnector
}

trait SubscribeService {

  def etmpConnector: ETMPConnector

  def ggAdminConnector: GovernmentGatewayAdminConnector

  def subscribe(data: JsValue)(implicit hc: HeaderCarrier) = {
    for {
      submitResponse <- etmpConnector.subscribeAted(stripUtr(data))
      _ <- addKnownFacts(submitResponse, data)
    } yield {
      submitResponse
    }
  }

  private def addKnownFacts(response: HttpResponse, data: JsValue)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val isNonUKClientRegisteredByAgent = (data \ "isNonUKClientRegisteredByAgent").asOpt[Boolean].getOrElse(false)
    (isNonUKClientRegisteredByAgent, response.status) match {
      case (false, OK) =>
        ggAdminConnector.addKnownFacts(createKnownFacts(response, data))
      case _ =>
        Future.successful(response)
    }
  }

  def stripUtr(data: JsValue) = {
    data.as[JsObject] - "utr" - "isNonUKClientRegisteredByAgent"
  }

  private def createKnownFacts(response: HttpResponse, data: JsValue) = {

    val json = response.json
    val atedRefNumber = (json \ "atedRefNumber").asOpt[String]
    if (atedRefNumber.isEmpty) {
      throw new RuntimeException("[SubscribeService][createKnownFacts] - atedRefNumber not returned from etmp subscribe")
    }

    val utr = (data \ "utr").asOpt[String]
    val postalCode = (data \\ "postalCode")
    if (postalCode.isEmpty && utr.map(_.isEmpty).getOrElse(true)) {
      throw new RuntimeException("[SubscribeService][createKnownFacts] - postalCode or utr must be supplied " + data)
    }

    val knownFact1 = atedRefNumber.map(atedRef => KnownFact(GovernmentGatewayConstants.AtedReferenceNoType, atedRef))
    val knownFact2 = postalCode.headOption.map(postCodeFound => KnownFact(GovernmentGatewayConstants.PostalCode, postCodeFound.toString()))
    val knownFact3 = utr.map(KnownFact(GovernmentGatewayConstants.CTUTR, _))
    val knownFacts = List(knownFact1, knownFact2, knownFact3).flatten
    KnownFactsForService(knownFacts)
  }
}
