import java.nio.file.Paths

val scala3Version = "3.3.1"

lazy val root = project
  .in(file("."))
  .settings(
    name         := "auto-translate",
    version      := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    assembly / assemblyOutputPath := Paths
      .get("dist/auto-translate.jar")
      .toFile,
    libraryDependencies ++= Seq(
      "org.scalameta"                 %% "munit"     % "0.7.29" % Test,
      "com.softwaremill.sttp.client3" %% "core"      % "3.9.1",
      "org.typelevel"                 %% "cats-core" % "2.9.0",
      "com.lihaoyi"                   %% "upickle"   % "3.1.0",
      "com.monovore"                  %% "decline"   % "2.4.1"
    )
  )
