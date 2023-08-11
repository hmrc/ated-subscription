import play.routes.compiler.InjectedRoutesGenerator
import play.sbt.routes.RoutesKeys.routesGenerator
import sbt.Keys._
import sbt.{CrossVersion, Def, compilerPlugin, _}
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion

val appName: String = "ated-subscription"

lazy val appDependencies : Seq[ModuleID] = AppDependencies()
lazy val plugins: Seq[Plugins] = Seq(play.sbt.PlayScala, SbtDistributablesPlugin)
lazy val playSettings: Seq[Setting[_]] = Seq.empty

  lazy val scoverageSettings: Seq[Def.Setting[_ >: String with Double with Boolean]] = {
    import scoverage.ScoverageKeys
    Seq(
      ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;app.Routes.*;prod.*;uk.gov.hmrc.*;testOnlyDoNotUseInAppConf.*;forms.*;config.*;",
      ScoverageKeys.coverageMinimumStmtTotal := 95,
      ScoverageKeys.coverageFailOnMinimum := true,
      ScoverageKeys.coverageHighlighting := true
    )
  }

val silencerVersion = "1.7.1"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(plugins: _*)
  .settings(playSettings ++ scoverageSettings: _*)
  .settings( majorVersion := 2 )
  .settings(scalaSettings: _*)
  .configs(IntegrationTest)
  .settings(defaultSettings(): _*)
  .settings(
    addTestReportOption(IntegrationTest, "int-test-reports"),
    inConfig(IntegrationTest)(Defaults.itSettings),
    scalaVersion := "2.12.11",
    libraryDependencies ++= appDependencies,
    Test / parallelExecution := false,
    Test / fork := false,
    retrieveManaged := true,
    routesGenerator := InjectedRoutesGenerator,
    IntegrationTest / Keys.fork :=  false,
    IntegrationTest / unmanagedSourceDirectories :=  (IntegrationTest / baseDirectory)(base => Seq(base / "it")).value,
    IntegrationTest / parallelExecution := false,
    scalacOptions += "-P:silencer:pathFilters=views;routes",
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
      "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
    )
  )
  .settings(
    resolvers += Resolver.typesafeRepo("releases"),
    resolvers += Resolver.jcenterRepo
  )
  .enablePlugins(SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
