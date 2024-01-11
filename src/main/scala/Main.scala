import extraction.processor.IEighteenNextProcessor

import java.nio.file.Paths

@main def main: Unit =
  val processor = new IEighteenNextProcessor();
  val result = processor.process(Paths.get("/Users/johannes/modulize/takeoff-ui-wolf/src"))
  println(result.getProcessedFiles)
