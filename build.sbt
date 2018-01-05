import org.typelevel.Dependencies._

addCommandAlias("gitSnapshots", ";set version in ThisBuild := git.gitDescribedVersion.value.get + \"-SNAPSHOT\"")

val apache2 = "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html")
val gh = GitHubSettings(org = "iheartradio", proj = "lihua", publishOrg = "com.iheart", license = apache2)
val devs = Seq(Dev("Kailuo Wang", "@kailuowang"))

val vAll = Versions(versions, libraries, scalacPlugins)
val reactiveMongoVer = "0.12.1"

val newTypeSettings = libraryDependencies += "io.estatico" %% "newtype" % "0.1.0"

lazy val lihua = project.in(file("."))
  .settings(commonSettings)
  .settings(noPublishSettings)
  .aggregate(mongo)

lazy val mongo = project
  .settings(name := "mongo")
  .settings(moduleName := "lihua-mongo")
  .settings(commonSettings)
  .settings(mainecoonSettings)
  .settings(addLibs(vAll, "cats-core", "cats-effect"))
  .settings(addTestLibs(vAll, "scalatest"))
  .settings(simulacrumSettings(vAll))
  .settings(
    libraryDependencies ++= Seq(
      "org.reactivemongo" %% "reactivemongo" % reactiveMongoVer,
      "org.reactivemongo" %% "reactivemongo-play-json" % reactiveMongoVer,
      "org.reactivemongo" %% "reactivemongo-iteratees" % reactiveMongoVer,
      "com.iheart" %% "ficus" % "1.4.3",
      "com.github.cb372" %% "scalacache-caffeine" % "0.9.3",
      "com.typesafe.play" %% "play-json" % "2.6.7",
      "org.log4s" %% "log4s" % "1.3.4",
      "com.google.code.findbugs" % "jsr305" % "3.0.0" //needed by scalacache-caffeine
    )
  )


lazy val mainecoonSettings = Seq(
  addCompilerPlugin(
    ("org.scalameta" % "paradise" % "3.0.0-M10").cross(CrossVersion.full)
  ),
  libraryDependencies ++= Seq(
    "com.kailuowang" %% "mainecoon-macros" % "0.6.0"
  )
)


lazy val buildSettings = sharedBuildSettings(gh, vAll)

lazy val commonSettings = buildSettings ++ publishSettings ++ scoverageSettings ++ sharedCommonSettings ++ scalacAllSettings ++ Seq(
  parallelExecution in Test := false,
  organization := "lihua",
  crossScalaVersions := Seq(vAll.vers("scalac_2.11"), scalaVersion.value)
)

lazy val commonJsSettings = Seq(scalaJSStage in Global := FastOptStage)

lazy val commonJvmSettings = Seq()

lazy val publishSettings = sharedPublishSettings(gh, devs) ++ credentialSettings ++ sharedReleaseProcess

lazy val scoverageSettings = sharedScoverageSettings(60)

lazy val disciplineDependencies = addLibs(vAll, "discipline", "scalacheck")


