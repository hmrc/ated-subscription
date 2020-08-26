import sbt._

object AppDependencies {

  import play.sbt.PlayImport._
  import play.core.PlayVersion

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-backend-play-27" % "2.24.0"
  )

  def apply() = compile ++ Test()

  trait TestDependencies {
    lazy val scope: String = "it,test"
    val test: Seq[ModuleID]
  }

  object Test {
    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % scope,
        "org.pegdown" % "pegdown" % "1.6.0" % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "org.mockito" % "mockito-core" % "3.3.3" % scope,
        "com.github.tomakehurst" % "wiremock-jre8" % "2.23.2" % IntegrationTest withSources()
      )
    }.test
  }

}
