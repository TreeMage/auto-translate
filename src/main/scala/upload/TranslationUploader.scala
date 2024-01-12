package upload

import common.Language

case class TranslationUploadError(code: Int, message: String)

trait TranslationUploader:
  def upload(
      translations: Map[Language, Map[String, String]]
  ): Either[TranslationUploadError, Unit]
