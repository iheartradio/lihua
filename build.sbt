import org.typelevel.Dependencies._

addCommandAlias("gitSnapshots", ";set version in ThisBuild := git.gitDescribedVersion.value.get + \"-SNAPSHOT\"")

val apache2 = "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html")
val gh = GitHubSettings(org = "iheartradio", proj = "lihua", publishOrg = "com.iheart", license = apache2)

val vAll = Versions(versions, libraries, scalacPlugins)
val reactiveMongoVer = "0.12.6"


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
  .settings(
    libraryDependencies ++= Seq(
      "org.reactivemongo" %% "reactivemongo" % reactiveMongoVer,
      "org.reactivemongo" %% "reactivemongo-play-json" % (reactiveMongoVer + "-play26"),
      "org.reactivemongo" %% "reactivemongo-iteratees" % reactiveMongoVer,
      "com.iheart" %% "ficus" % "1.4.3",
      "com.github.cb372" %% "scalacache-caffeine" % "0.22.0",

      "com.typesafe.play" %% "play-json" % "2.6.2",
      "com.typesafe.akka" %% "akka-slf4j" % "2.5.9" % Test,
      "org.log4s" %% "log4s" % "1.3.4",
      "com.github.mpilquist" %% "simulacrum" % vAll.vers("simulacrum") % Provided,
      "com.google.code.findbugs" % "jsr305" % "3.0.0" //needed by scalacache-caffeine
    )
  )

lazy val crypt = project
  .dependsOn(mongo)
  .aggregate(mongo)
  .settings(name := "crypt")
  .settings(moduleName := "lihua-crypt")
  .settings(commonSettings)
  .settings(mainecoonSettings)
  .settings(addLibs(vAll, "cats-core"))
  .settings(addTestLibs(vAll, "scalatest"))
  .settings(
    resolvers += Resolver.bintrayRepo("jmcardon", "tsec"),
    libraryDependencies ++= Seq(
      "io.github.jmcardon" %% "tsec-symmetric-cipher" % "0.0.1-M7"
    ))

lazy val mainecoonSettings = Seq(
  addCompilerPlugin(
    ("org.scalameta" % "paradise" % "3.0.0-M10").cross(CrossVersion.full)
  ),
  libraryDependencies ++= Seq(
    "com.kailuowang" %% "mainecoon-macros" % "0.6.2"
  )
)

lazy val buildSettings = sharedBuildSettings(gh, vAll)

lazy val commonSettings = buildSettings ++ publishSettings ++ unidocCommonSettings ++ scoverageSettings ++ sharedCommonSettings ++ scalacAllSettings ++ Seq(
  parallelExecution in Test := false,
  sources in (Compile, doc) :=  Nil, //todo: somehow sbt doc hang, disable it for now so that I can release.
  crossScalaVersions := Seq(scalaVersion.value),
  developers := List(Developer("@kailuowang", "Kailuo Wang", "kailuo.wang@gmail.com", new URL("http://kailuowang.com")))
) ++ addCompilerPlugins(vAll, "kind-projector")

lazy val commonJsSettings = Seq(scalaJSStage in Global := FastOptStage)

lazy val commonJvmSettings = Seq()

lazy val publishSettings = sharedPublishSettings(gh) ++ credentialSettings ++ sharedReleaseProcess

lazy val scoverageSettings = sharedScoverageSettings(60)

lazy val disciplineDependencies = addLibs(vAll, "discipline", "scalacheck")


