package helpers

import java.util.UUID

import play.api.Application
import play.api.mvc.{DefaultCookieHeaderEncoding, DefaultSessionCookieBaker}
import uk.gov.hmrc.http.SessionKeys

trait LoginStub {

  val app: Application
  lazy val signerSession: DefaultSessionCookieBaker = app.injector.instanceOf[DefaultSessionCookieBaker]
  lazy val cookieHeader: DefaultCookieHeaderEncoding = app.injector.instanceOf[DefaultCookieHeaderEncoding]

  val sessionId = s"stubbed-${UUID.randomUUID}"

  private def cookieData(additionalData: Map[String, String], timeStampRollback: Long): Map[String, String] = {
    val timeStamp = new java.util.Date().getTime
    val rollbackTimestamp = (timeStamp - timeStampRollback).toString

    Map(
      SessionKeys.sessionId -> sessionId,
      SessionKeys.userId -> "/auth/oid/1234567890",
      SessionKeys.authToken -> "token",
      SessionKeys.authProvider -> "GGW",
      SessionKeys.lastRequestTimestamp -> rollbackTimestamp
    ) ++ additionalData
  }

  def getSessionCookie(additionalData: Map[String, String] = Map(), timeStampRollback: Long = 0): String = {
    val cookie = signerSession.encodeAsCookie(signerSession.deserialize(cookieData(additionalData, timeStampRollback)))
    val encodedCookie = cookieHeader.encodeSetCookieHeader(Seq(cookie))

    encodedCookie
  }
}
