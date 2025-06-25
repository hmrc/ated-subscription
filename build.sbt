import play.routes.compiler.InjectedRoutesGenerator
import play.sbt.routes.RoutesKeys.routesGenerator
import sbt.Keys.{parallelExecution, *}
import sbt.{Def, *}
import uk.gov.hmrc.DefaultBuildSettings
import uk.gov.hmrc.DefaultBuildSettings.{defaultSettings, scalaSettings}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion

val appName: String = "ated-subscription"

ThisBuild / majorVersion := 2
ThisBuild / scalaVersion := "2.13.16"

lazy val appDependencies : Seq[ModuleID] = AppDependencies()
lazy val plugins : Seq[Plugins] = Seq(play.sbt.PlayScala, SbtDistributablesPlugin)

lazy val playSettings: Seq[Setting[?]] = Seq.empty

  lazy val scoverageSettings: Seq[Def.Setting[? >: String & Double & Boolean]] = {
    import scoverage.ScoverageKeys
    Seq(
      ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;app.Routes.*;prod.*;uk.gov.hmrc.*;testOnlyDoNotUseInAppConf.*;forms.*;config.*;",
      ScoverageKeys.coverageMinimumStmtTotal := 95,
      ScoverageKeys.coverageFailOnMinimum := true,
      ScoverageKeys.coverageHighlighting := true
    )
  }

lazy val microservice = Project(appName, file("."))
  .enablePlugins(plugins *)
  .settings(
    libraryDependencies ++= appDependencies,
    Test / parallelExecution := false,
    Test / fork := false,
    retrieveManaged := true,
    routesGenerator := InjectedRoutesGenerator,
    scalacOptions += "-Wconf:src=routes/.*:s",
    scoverageSettings,
    scalaSettings,
    defaultSettings(),
  )
  .settings(
    resolvers += Resolver.typesafeRepo("releases")
  )
  .enablePlugins(SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(DefaultBuildSettings.itSettings())
  .settings(libraryDependencies ++= AppDependencies.itDependencies)
