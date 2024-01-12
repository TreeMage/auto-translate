package cli

import com.monovore.decline.{Command, Opts}

import java.nio.file.{Path, Paths}
import scala.util.{Failure, Success, Try}
import upickle.default.*
import cli.PathExtensions.*
import common.AppConfig

case class RunConfig(configFilePath: Path)

object RunCommand:
  private lazy val options = Opts
    .option[Path](long = "config-file", help = "Path to the configuration file")
    .withDefault(
      Paths.get("").resolve(Constants.relativeConfigFilePath)
    )
    .map(_.resolveHome.toAbsolutePath)
    .map(RunConfig.apply)
  lazy val command: Opts[Unit] = Opts.subcommand(
    Command(
      name = "run",
      header = "Run the auto-translate tool."
    )(options.map { config =>
      Try(io.Source.fromFile(config.configFilePath.toFile)).map(source =>
        read[AppConfig](source.mkString)
      ) match
        case Failure(exception) =>
          Console.err.println(
            s"Failed to read config file at ${config.configFilePath} due to $exception"
          )
          sys.exit(1)
        case Success(config) =>
          RunLogic.run(config)
    })
  )
