package slack

import common.Language

trait MessageFormatter:
  def format(
      translations: Map[Language, Map[String, String]],
      context: Option[String]
  ): String

object MessageFormatter:
  extension (language: Language)
    private def asFlag: String = language match
      case Language.English   => ":flag-gb:"
      case Language.Norwegian => ":flag-no:"

  val make: MessageFormatter = new MessageFormatter:
    override def format(
        translations: Map[Language, Map[String, String]],
        context: Option[String]
    ): String =
      val languages = translations.keys.toList
      val keys      = translations.head._2.keys.toList
      val translationsBlock =
        keys
          .map(key =>
            languages
              .map(language =>
                s"${language.asFlag} ${translations(language)(key)}"
              )
              .mkString("\n") + "\n"
          )
          .mkString("\n")

      context match
        case Some(value) =>
          s"""Context: $value
             |
             |$translationsBlock""".stripMargin
        case None => translationsBlock
