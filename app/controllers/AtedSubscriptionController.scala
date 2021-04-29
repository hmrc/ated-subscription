/*
 * Copyright 2021 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.SubscribeService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class DefaultAtedSubscriptionController @Inject()(val subscribeService: SubscribeService,
                                                  val cc: ControllerComponents) extends BackendController(cc) with AtedSubscriptionController

@Singleton
class AgentAtedSubscriptionController @Inject()(val subscribeService: SubscribeService,
                                                val cc: ControllerComponents) extends BackendController(cc) with AtedSubscriptionController

trait AtedSubscriptionController extends BackendController {
  def subscribeService: SubscribeService

  def subscribe(orgId: String): Action[AnyContent] = Action.async { implicit request =>
    val jsonData = request.body.asJson.get
    subscribeService.subscribe(jsonData) map { returnedResponse =>
      returnedResponse.status match {
        case OK => Ok(returnedResponse.body)
        case BAD_REQUEST => BadRequest(returnedResponse.body)
        case NOT_FOUND => NotFound(returnedResponse.body)
        case SERVICE_UNAVAILABLE => ServiceUnavailable(returnedResponse.body)
        case _ => InternalServerError(returnedResponse.body)
      }
    }
  }
}
