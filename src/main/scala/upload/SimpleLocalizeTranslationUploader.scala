package upload

import sttp.client3.*
import common.Language
import sttp.model.StatusCode
import upickle.default.*
import cats.implicits.*

case class SimpleLocalizeTranslationKey(
    key: String,
    namespace: String,
    description: String
) derives ReadWriter
object SimpleLocalizeTranslationKey:
  def fromKey(key: String): SimpleLocalizeTranslationKey =
    SimpleLocalizeTranslationKey(key, "", "")

case class SimpleLocalizeAddTranslationKeysRequest(
    translationKeys: List[SimpleLocalizeTranslationKey]
) derives ReadWriter

case class SimpleLocalizeTranslation(
    key: String,
    language: String,
    text: String,
    namespace: String
) derives ReadWriter
case class SimpleLocalizeTranslationUploadRequest(
    translations: List[SimpleLocalizeTranslation]
) derives ReadWriter

object SimpleLocalizeTranslationUploader:
  extension (language: Language)
    private def asLanguageCode: String =
      language match
        case Language.English   => "en"
        case Language.Norwegian => "no"

  def make(
      baseUrl: String,
      apiKey: String
  ): TranslationUploader = new TranslationUploader:
    private val backend = HttpClientSyncBackend()
    private def createTranslationKeys(
        keys: List[String]
    ): Either[TranslationUploadError, Unit] =
      val body = write(
        SimpleLocalizeAddTranslationKeysRequest(
          keys.map(SimpleLocalizeTranslationKey.fromKey)
        )
      )
      val request = basicRequest
        .post(uri"${baseUrl}/api/v1/translation-keys/bulk")
        .header("X-SimpleLocalize-Token", apiKey)
        .header("Content-Type", "application/json")
        .body(
          body
        )
      val response = request.send(backend)
      if (response.code != StatusCode.Ok)
        TranslationUploadError(response.code.code, response.statusText).asLeft
      else
        ().asRight

    private def createTranslations(
        translations: Map[Language, Map[String, String]]
    ): Either[TranslationUploadError, Unit] =
      val content = translations.flatMap { (language, translationMap) =>
        translationMap.map { (key, text) =>
          SimpleLocalizeTranslation(
            key,
            language.asLanguageCode,
            text,
            ""
          )
        }
      }
      val body = write(SimpleLocalizeTranslationUploadRequest(content.toList))
      val request = basicRequest
        .patch(uri"${baseUrl}/api/v2/translations/bulk")
        .header("X-SimpleLocalize-Token", apiKey)
        .header("Content-Type", "application/json")
        .body(body)

      val response = request.send(backend)
      if (response.code != StatusCode.Ok)
        TranslationUploadError(response.code.code, response.statusText).asLeft
      else
        ().asRight
    override def upload(
        translations: Map[Language, Map[String, String]]
    ): Either[TranslationUploadError, Unit] =
      val keys = translations.head._2.keys.toList
      for
        _ <- createTranslationKeys(keys)
        _ <- createTranslations(translations)
      yield ()
