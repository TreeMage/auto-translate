package extraction

import java.io.FileWriter
import java.nio.file.Path
import scala.util.Using

trait DiffFileManager:
  def readKeysFromFile(path: Path): Either[Throwable, Set[String]]

  def writeKeysToFile(path: Path, keys: Set[String]): Either[Throwable, Unit]

object DiffFileManager:
  val make: DiffFileManager = new DiffFileManager:
    override def readKeysFromFile(path: Path): Either[Throwable, Set[String]] =
      Using(io.Source.fromFile(path.toFile))(_.getLines().toSet).toEither

    override def writeKeysToFile(
        path: Path,
        keys: Set[String]
    ): Either[Throwable, Unit] =
      Using(new FileWriter(path.toFile)) { writer =>
        keys.toList.sorted.foreach(key => writer.write(s"$key\n"))
      }.toEither
