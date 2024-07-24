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

package uk.gov.hmrc.connectors

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.JsValue
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}


trait ConnectorTest extends FutureAwaits with DefaultAwaitTimeout with MockitoSugar {
  val mockHttpClient: HttpClientV2 = mock[HttpClientV2]
  val requestBuilder: RequestBuilder = mock[RequestBuilder]
  when(mockHttpClient.get(any[URL])(any[HeaderCarrier])).thenReturn(requestBuilder)
  when(mockHttpClient.post(any[URL])(any[HeaderCarrier])).thenReturn(requestBuilder)
  when(mockHttpClient.put(any[URL])(any[HeaderCarrier])).thenReturn(requestBuilder)
  when(requestBuilder.withBody(any[JsValue])(any(), any(), any())).thenReturn(requestBuilder)
  when(requestBuilder.setHeader(any[(String, String)])).thenReturn(requestBuilder)
  def requestBuilderExecute[A]: Future[A] = requestBuilder.execute[A](any[HttpReads[A]], any[ExecutionContext])
}