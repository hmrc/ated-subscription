/*
 * Copyright 2024 HM Revenue & Customs
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

package helpers.application

import connectors.{DefaultEtmpConnector, DefaultGovernmentGatewayAdminConnector, DefaultTaxEnrolmentsConnector, EtmpConnector, GovernmentGatewayAdminConnector, TaxEnrolmentsConnector}
import helpers.wiremock.WireMockConfig
import metrics.{DefaultServiceMetrics, ServiceMetrics}
import org.scalatest.TestSuite
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{WSClient, WSRequest}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import play.api.inject.bind
import services.{DefaultSubscribeService, SubscribeService}
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import uk.gov.hmrc.http.HttpClient

trait IntegrationApplication extends GuiceOneServerPerSuite with WireMockConfig {
  self: TestSuite =>

  val currentAppBaseUrl: String = "ated-subscription"
  val testAppUrl: String        = s"http://localhost:$port"

  lazy val ws: WSClient = app.injector.instanceOf[WSClient]

  val applicationConfig: Map[String, String] = Map(

  )

  override lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(bind(classOf[AuthConnector]).to(classOf[DefaultAuthConnector]))
    .overrides(bind(classOf[EtmpConnector]).to(classOf[DefaultEtmpConnector]))
    .overrides(bind(classOf[GovernmentGatewayAdminConnector]).to(classOf[DefaultGovernmentGatewayAdminConnector]))
    .overrides(bind(classOf[TaxEnrolmentsConnector]).to(classOf[DefaultTaxEnrolmentsConnector]))
    .overrides(bind(classOf[HttpClient]).to(classOf[DefaultHttpClient]))
    .overrides(bind(classOf[SubscribeService]).to(classOf[DefaultSubscribeService]))
    .overrides(bind(classOf[ServiceMetrics]).to(classOf[DefaultServiceMetrics]))
    .configure(    "microservice.services.auth.host"                     -> wireMockHost,
      "microservice.services.auth.port"                     -> wireMockPort,
      "microservice.services.enrolment-store-proxy.host"    -> wireMockHost,
      "microservice.services.enrolment-store-proxy.port"    -> wireMockPort,
      "play.http.router"                                    -> "testOnlyDoNotUseInAppConf.Routes",
      "microservice.metrics.graphite.host"                  -> "localhost",
      "microservice.metrics.graphite.port"                  -> 2003,
      "microservice.metrics.graphite.prefix"                -> "play.ated-subscription.",
      "microservice.metrics.graphite.enabled"               -> true,
      "microservice.services.etmp-hod.host"                 -> wireMockHost,
      "microservice.services.etmp-hod.port"                 -> wireMockPort,
      "microservice.services.tax-enrolments.host"           -> wireMockHost,
      "microservice.services.tax-enrolments.port"           -> wireMockPort,
      "metrics.name"                                        -> "ated-subscription",
      "metrics.rateUnit"                                    -> "SECONDS",
      "metrics.durationUnit"                                -> "SECONDS",
      "metrics.showSamples"                                 -> true,
      "metrics.jvm"                                         -> true,
      "metrics.enabled"                                     -> true)
    .build()

  def makeRequest(uri: String): WSRequest = ws.url(s"http://localhost:$port/$uri")
}
