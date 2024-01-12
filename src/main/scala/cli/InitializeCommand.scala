package cli

import com.monovore.decline.{Command, Opts}
import config.{AppConfig, DeepLConfig, SimpleLocalizeConfig, SlackConfig}

import java.io.PrintWriter
import java.nio.file.{Path, Paths}
import scala.util.{Failure, Success, Using}
import upickle.default.*
import cli.PathExtensions.*

case class InitializeConfig(projectPath: Path)

object InitializeCommand:
  lazy val command: Opts[Unit] = Opts.subcommand(
    Command(
      name = "init",
      header =
        "Generates config files and sets up auto-translate for the current project."
    )(
      options.map { config =>
        val configDirectory =
          config.projectPath.resolve(Constants.configDirectoryName)
        println(s"Creating configuration in $configDirectory.")
        if (!configDirectory.toFile.exists)
          val directoryCreated = configDirectory.toFile.mkdirs()
          if (!directoryCreated)
            Console.err.println("Failed to create configuration directory.")
            sys.exit(1)
        Using(
          new PrintWriter(
            configDirectory.resolve(Constants.configFileName).toFile
          )
        ) { writer =>
          writer.write(write(makeDefaultConfig(config.projectPath)))
        } match
          case Failure(exception) =>
            Console.err.println(
              s"Failed to generate config skeleton: $exception"
            )
            sys.exit(1)
          case Success(_) =>
            println(s"Successfully created config skeleton in $configDirectory")

      }
    )
  )
  private lazy val options = Opts
    .option[Path](
      long = "project-directory",
      help = "Project directory to initialize"
    )
    .withDefault(Paths.get(""))
    .map(_.resolveHome.toAbsolutePath)
    .map(InitializeConfig.apply)

  private def makeDefaultConfig(projectPath: Path) =
    AppConfig(
      projectPath.resolve("src"),
      projectPath.resolve(Constants.relativeKeyFilePath),
      DeepLConfig("https://api-free.deepl.com", "YOUR_API_KEY_HERE"),
      SlackConfig(""),
      SimpleLocalizeConfig("https://api.simplelocalize.io", "YOUR_APU_KEY_HERE")
    )
