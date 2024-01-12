package cli

import common.AppConfig
import upickle.default.read

import java.nio.file.Path
import scala.util.{Failure, Success, Try}

object ConfigIO:
  def readConfig(path: Path): Either[Throwable, AppConfig] =
    Try(io.Source.fromFile(path.toFile))
      .map(source => read[AppConfig](source.mkString))
      .toEither
