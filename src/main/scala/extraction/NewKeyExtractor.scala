package extraction

import extraction.processor.IEighteenNextProcessor

import java.nio.file.Path
import scala.jdk.CollectionConverters.*
import scala.util.Try

trait NewKeyExtractor:
  def extractAllKeys(path: Path): Either[Throwable, Set[String]]

  def extractNewKeys(
      path: Path,
      oldKeys: Set[String]
  ): Either[Throwable, Set[String]]

object NewKeyExtractor:
  val make: NewKeyExtractor = new NewKeyExtractor:
    override def extractAllKeys(path: Path): Either[Throwable, Set[String]] =
      val processor = new IEighteenNextProcessor()
      Try(processor.process(path)).map(_.getKeys.asScala.toSet).toEither

    override def extractNewKeys(
        path: Path,
        oldKeys: Set[String]
    ): Either[Throwable, Set[String]] =
      for allKeys <- extractAllKeys(path)
      yield allKeys.diff(oldKeys)
