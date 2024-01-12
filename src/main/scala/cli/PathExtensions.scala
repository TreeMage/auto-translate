package cli

import java.io.File
import java.nio.file.{Path, Paths}

object PathExtensions:
  extension (path: Path)
    def resolveHome: Path =
      if (!path.startsWith("~" + File.separator)) path
      else
        val home = sys.props("user.home")
        Paths.get(home, path.toString.substring(2))
