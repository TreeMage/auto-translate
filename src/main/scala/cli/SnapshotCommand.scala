package cli

import com.monovore.decline.{Command, Opts}
import extraction.{DiffFileManager, NewKeyExtractor}

import java.nio.file.{Path, Paths}
import PathExtensions.*
import cats.implicits._

case class SnapshotConfig(configurationPath: Path)

object SnapshotCommand:
  private lazy val options = Opts
    .option[Path](long = "config-file", help = "Path to the configuration file")
    .withDefault(
      Paths.get("").resolve(Constants.relativeConfigFilePath)
    )
    .map(_.resolveHome.toAbsolutePath)
    .map(SnapshotConfig.apply)
  lazy val command: Opts[Unit] = Opts.subcommand(
    Command(
      name = "snapshot",
      header = "Takes a snapshot of the translation keys currently in use"
    )(
      options.map { config =>
        val result =
          for
            appConfig <- ConfigIO.readConfig(config.configurationPath)
            keys      <- NewKeyExtractor.make.extractAllKeys(appConfig.srcDir)
            _ <- println(s"Extracted ${keys.size} keys from ${appConfig.srcDir}.").asRight
            _ <- DiffFileManager.make
              .writeKeysToFile(appConfig.keyFilePath, keys)
            _ <- println(s"Successfully wrote all keys to the key file at ${appConfig.keyFilePath}.").asRight
          yield ()
        result match
          case Left(throwable) =>
            Console.err.println(s"Failed to take snapshot due to $throwable")
          case Right(_) => ()
      }
    )
  )
