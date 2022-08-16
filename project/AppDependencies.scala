import sbt._

object AppDependencies {

  import play.sbt.PlayImport._
  import play.core.PlayVersion

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-backend-play-28" % "5.25.0"
  )

  def apply(): Seq[sbt.ModuleID] = compile ++ Test()

  trait TestDependencies {
    lazy val scope: String = "it,test"
    val test: Seq[ModuleID]
  }

  object Test {
    def apply(): Seq[sbt.ModuleID] = new TestDependencies {
      override lazy val test = Seq(
        "org.scalatestplus.play"       %% "scalatestplus-play"   % "5.1.0"             % scope,
        "org.pegdown"                  %  "pegdown"              % "1.6.0"             % scope,
        "com.typesafe.play"            %% "play-test"            % PlayVersion.current % scope,
        "org.mockito"                  %  "mockito-core"         % "4.7.0"             % scope,
        "org.scalatestplus"            %% "mockito-3-12"         % "3.2.10.0"          % scope,
        "com.vladsch.flexmark"         %  "flexmark-all"         % "0.35.10"           % scope,
        "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.13.3"            % scope,
        "com.github.tomakehurst"       %  "wiremock-jre8"        % "2.33.2"            % IntegrationTest withSources()
      )
    }.test
  }

}
