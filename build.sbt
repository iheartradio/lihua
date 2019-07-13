import org.typelevel.Dependencies._

addCommandAlias("gitSnapshots", ";set version in ThisBuild := git.gitDescribedVersion.value.get + \"-SNAPSHOT\"")

addCommandAlias("validate", ";clean;test")

val apache2 = "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html")
val gh = GitHubSettings(org = "iheartradio", proj = "lihua", publishOrg = "com.iheart", license = apache2)

val reactiveMongoVer = "0.17.0"

lazy val libs =
  org.typelevel.libraries
    .addJVM("reactivemongo", version = reactiveMongoVer, org = "org.reactivemongo", "reactivemongo", "reactivemongo-iteratees" )
    .addJVM("reactivemongo-play-json", version = reactiveMongoVer + "-play27", org = "org.reactivemongo")
    .addJava("caffeine", version = "2.7.0", org = "com.github.ben-manes.caffeine")
    .addJVM("scalacache-caffeine", version = "0.27.0", org = "com.github.cb372")
    .addJVM("play-json", version = "2.7.3", org = "com.typesafe.play")

lazy val lihua = project.in(file("."))
  .settings(commonSettings)
  .settings(noPublishSettings,
            crossScalaVersions := Nil)
  .aggregate(mongo, crypt, core)

lazy val core = project
  .settings(moduleName := "lihua-core")
  .settings(commonSettings)
  .settings(taglessSettings)

lazy val mongo = project
  .dependsOn(core)
  .aggregate(core)
  .settings(moduleName := "lihua-mongo")
  .settings(commonSettings)
  .settings(taglessSettings)
  .settings(
    crossScalaVersions := Seq(scalaVersion.value),
    libs.testDependencies("scalatest"),
    libs.dependency("simulacrum", Some("provided")),
    libs.dependencies(
      "cats-effect",
      "reactivemongo",
      "reactivemongo-iteratees",
      "reactivemongo-play-json",
      "play-json",
      "scalacache-caffeine",
      "caffeine"),
    libraryDependencies ++= Seq(
      "com.iheart" %% "ficus" % "1.4.3",
      "com.typesafe.akka" %% "akka-slf4j" % "2.5.19" % Test,
      "org.apache.logging.log4j" % "log4j-core" % "2.11.1" % Test,
      "org.log4s" %% "log4s" % "1.6.1",
      "com.google.code.findbugs" % "jsr305" % "3.0.2" //needed by scalacache-caffeine
    )
  )

lazy val crypt = project
  .dependsOn(mongo)
  .aggregate(mongo)
  .settings(name := "crypt")
  .settings(moduleName := "lihua-crypt")
  .settings(commonSettings)
  .settings(taglessSettings)
  .settings(
    crossScalaVersions := Seq(scalaVersion.value),
    libs.dependencies("cats-core"),
    libs.testDependencies("scalatest"),
    libraryDependencies ++= Seq(
      "io.github.jmcardon" %% "tsec-cipher-jca" % "0.0.1-M11"
    ))

lazy val taglessSettings = paradiseSettings(libs) ++ Seq(
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-tagless-macros" % "0.5"
  )
)

lazy val buildSettings = sharedBuildSettings(gh, libs)

lazy val commonSettings = buildSettings ++ publishSettings ++ unidocCommonSettings ++ scoverageSettings ++ sharedCommonSettings ++ scalacAllSettings ++ Seq(
  parallelExecution in Test := false,
  sources in (Compile, doc) :=  Nil, //todo: somehow sbt doc hang, disable it for now so that I can release.
  crossScalaVersions := Seq(libs.vers("scalac_2.11"), scalaVersion.value),
  developers := List(Developer("@kailuowang", "Kailuo Wang", "kailuo.wang@gmail.com", new URL("http://kailuowang.com")))
) ++ addCompilerPlugins(libs, "kind-projector")

lazy val commonJsSettings = Seq(scalaJSStage in Global := FastOptStage)

lazy val commonJvmSettings = Seq()

lazy val publishSettings = sharedPublishSettings(gh) ++ credentialSettings ++ sharedReleaseProcess

lazy val scoverageSettings = sharedScoverageSettings(60)

