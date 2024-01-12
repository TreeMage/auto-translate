package common

import upickle.default.*

import java.nio.file.{Path, Paths}

case class AppConfig(
    srcDir: Path,
    keyFilePath: Path,
    deepLConfig: DeepLConfig,
    slackConfig: SlackConfig,
    simpleLocalizeConfig: SimpleLocalizeConfig
) derives ReadWriter

object AppConfig:
  given ReadWriter[Path] =
    readwriter[String].bimap[Path](_.toString, Paths.get(_))

case class DeepLConfig(baseUrl: String, apiKey: String) derives ReadWriter

case class SlackConfig(webhookUrl: String) derives ReadWriter

case class SimpleLocalizeConfig(
    baseUrl: String,
    apiKey: String
) derives ReadWriter
