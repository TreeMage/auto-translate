package translate

import common.Language
import sttp.client3.{HttpClientSyncBackend, UriContext, basicRequest}
import sttp.model.StatusCode
import upickle.default.*
import upickle.implicits.key

case class DeepLTranslationError(statusCode: Int, message: String)

object DeepLTranslator:
  case class TranslationRequest(
      @key("source_lang") sourceLanguage: Language,
      @key("target_lang") targetLanguage: Language,
      text: List[String]
  ) derives ReadWriter
  case class TranslationRequestWithContext(
      @key("source_lang") sourceLanguage: Language,
      @key("target_lang") targetLanguage: Language,
      context: String,
      text: List[String]
  ) derives ReadWriter
  case class Translation(
      @key("detected_source_language") detectedSourceLanguage: String,
      text: String
  ) derives ReadWriter
  case class TranslationResponse(translations: List[Translation])
      derives ReadWriter

  private def formatLanguage(language: Language): String = language match
    case Language.English   => "EN"
    case Language.Norwegian => "NB"

  def make(baseUrl: String, apiKey: String): Translator[DeepLTranslationError] =
    new Translator[DeepLTranslationError]:
      private val backend = HttpClientSyncBackend()

      override def translate(
          sourceLanguage: Language,
          targetLanguage: Language,
          inputs: List[String]
      ): Either[DeepLTranslationError, List[String]] =
        _translate(sourceLanguage, targetLanguage, None, inputs)

      override def translateWithContext(
          sourceLanguage: Language,
          targetLanguage: Language,
          context: String,
          inputs: List[String]
      ): Either[DeepLTranslationError, List[String]] =
        _translate(sourceLanguage, targetLanguage, Some(context), inputs)

      private def _translate(
          sourceLanguage: Language,
          targetLanguage: Language,
          context: Option[String],
          inputs: List[String]
      ): Either[DeepLTranslationError, List[String]] =
        val body = context.fold(
          write(TranslationRequest(sourceLanguage, targetLanguage, inputs))
        )(context =>
          write(
            TranslationRequestWithContext(
              sourceLanguage,
              targetLanguage,
              context,
              inputs
            )
          )
        )
        val request = basicRequest
          .post(uri"$baseUrl/v2/translate")
          .header("Authorization", s"DeepL-Auth-Key $apiKey")
          .header("Content-Type", "application/json")
          .body(body)
        val response = request.send(backend)
        if (response.code != StatusCode.Ok)
          Left(DeepLTranslationError(response.code.code, response.statusText))
        response.body match
          case Left(value) => Left(DeepLTranslationError(500, value))
          case Right(value) =>
            Right(read[TranslationResponse](value).translations.map(_.text))
