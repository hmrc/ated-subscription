/*
 * Copyright 2020 HM Revenue & Customs
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

package module

import connectors._
import metrics.{DefaultServiceMetrics, ServiceMetrics}
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import services.{DefaultSubscribeService, SubscribeService}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.http.{DefaultHttpClient, HttpClient}

class ServiceBindings extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      bind(classOf[AuthConnector]).to(classOf[DefaultAuthConnector]),
      bind(classOf[EtmpConnector]).to(classOf[DefaultEtmpConnector]),
      bind(classOf[GovernmentGatewayAdminConnector]).to(classOf[DefaultGovernmentGatewayAdminConnector]),
      bind(classOf[TaxEnrolmentsConnector]).to(classOf[DefaultTaxEnrolmentsConnector]),
      bind(classOf[HttpClient]).to(classOf[DefaultHttpClient]),
      bind(classOf[SubscribeService]).to(classOf[DefaultSubscribeService]),
      bind(classOf[ServiceMetrics]).to(classOf[DefaultServiceMetrics])
    )
}
