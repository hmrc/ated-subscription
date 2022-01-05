/*
 * Copyright 2022 HM Revenue & Customs
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

package controllers

import javax.inject.Inject
import models.BusinessCustomerDetails
import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.EtmpRegimeService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class EtmpCheckController @Inject()(cc: ControllerComponents,
                                    etmpRegimeService: EtmpRegimeService) extends BackendController(cc) with Logging {

  implicit val ec: ExecutionContext = cc.executionContext

  def checkEtmp(): Action[AnyContent] = Action.async { implicit request =>
    val feJson = request.body.asJson.getOrElse(Json.obj())
    val businessCustomerDetails: Option[BusinessCustomerDetails] = Json.parse(feJson.toString()).asOpt[BusinessCustomerDetails]

    businessCustomerDetails match {
      case Some(bcd) =>
        etmpRegimeService.checkEtmpBusinessPartnerExists(bcd.safeId, bcd).map {
          case Some(etmpRegistrationDetails) =>
            val responseJson = Json.obj("regimeRefNumber" -> etmpRegistrationDetails.regimeRefNumber)
            Ok(responseJson)
          case _ =>
            logger.warn("[EtmpCheckController][checkEtmp] Could not retrieve/upsert for checkEtmp")
            NoContent
        }
      case _ =>
        logger.warn("[EtmpCheckController][checkEtmp] Incorrect model for checkEtmp")
        Future.successful(NoContent)
    }
  }
}
