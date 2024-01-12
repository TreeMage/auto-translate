package cli

import java.nio.file.{Path, Paths}

object Constants:
  val configDirectoryName = ".auto-translate"
  val configFileName      = "config.json"
  val keyFileName         = "keys"

  val relativeConfigFilePath: Path =
    Paths.get(configDirectoryName, configFileName)
  val relativeKeyFilePath: Path = Paths.get(configDirectoryName, keyFileName)
