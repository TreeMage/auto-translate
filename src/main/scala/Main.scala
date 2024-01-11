import Output.{Empty, Error, Info, Multiple, Success}
import State.{
  FirstTranslation,
  LanguageChosen,
  ProvidedTranslation,
  ReadKeys,
  Translated
}
import cli.CliApp
import common.Language
import extraction.{DiffFileManager, NewKeyExtractor}
import translate.DeepLTranslator
import cats.implicits.*
import slack.{MessageFormatter, SlackClient}

import scala.io.*
import java.nio.file.{Path, Paths}
import scala.annotation.targetName

val sourcePath = Paths.get("/Users/johannes/modulize/takeoff-ui-wolf/src")
val diffPath   = Paths.get("diff")
val apiKey     = "XXX"
val slackWebHookUrl =
  "XXX"

@main def main(): Unit =
  val config =
    Config(sourcePath, diffPath, apiKey, SlackConfig(slackWebHookUrl))
  val app = makeApp(config)
  app.run(State.Start)

case class Config(
    sourcePath: Path,
    diffFilePath: Path,
    deepLApiKey: String,
    slackConfig: SlackConfig
)
case class SlackConfig(webhookUrl: String)

enum State:
  case Start
  case ReadKeys(newKeys: Set[String])
  case LanguageChosen(newKeys: Set[String], language: Language)

  case ContextProvided(
      newKeys: Set[String],
      language: Language,
      context: String
  )
  case FirstTranslation(
      newKeys: Set[String],
      language: Language,
      remainingKeys: List[String],
      providedTranslations: Map[String, String],
      context: String
  )
  case ProvidedTranslation(
      newKeys: Set[String],
      language: Language,
      remainingKeys: List[String],
      providedTranslations: Map[String, String],
      context: String
  )
  case Translate(
      language: Language,
      providedTranslations: Map[String, String],
      context: String
  )
  case Translated(
      translations: Map[Language, Map[String, String]],
      context: String
  )

  case SentSlackMessage(translations: Map[Language, Map[String, String]])
  case Exit(status: Int)

enum Output:
  case Empty
  case Info(value: String)
  case Error(value: String)
  case Success(value: String)
  case Multiple(values: List[Output])

extension (that: Output)
  @targetName("append")
  def >>(other: Output): Output = Output.Multiple(List(that, other))

case class Input(value: String)

private def formatOutput(output: Output): String = output match
  case Empty            => ""
  case Info(value)      => s"$value"
  case Error(value)     => s"${AnsiColor.RED}$value${AnsiColor.RESET}"
  case Success(value)   => s"${AnsiColor.GREEN}$value${AnsiColor.RESET}"
  case Multiple(values) => values.map(formatOutput).mkString("\n")

private def step(
    config: Config
)(state: State, input: Option[Input]): (State, Output) =
  state match
    case State.Start =>
      val keys =
        for
          oldKeys <- DiffFileManager.make.readKeysFromFile(config.diffFilePath)
          newKeys <- NewKeyExtractor.make.extractNewKeys(
            config.sourcePath,
            oldKeys
          )
        yield newKeys
      keys match
        case Left(exception) =>
          (State.Exit(1), Output.Error(s"Key extraction failed: $exception."))
        case Right(newKeys) =>
          if (newKeys.isEmpty)
            (
              State.Exit(0),
              Output.Info(s"Did not find any new keys in ${config.sourcePath}.")
            )
          else
            (
              State.ReadKeys(newKeys),
              Output.Success(
                s"Extracted ${newKeys.size} new keys from $sourcePath"
              ) >> Output.Info("Please select a language: ")
            )
    case State.ReadKeys(newKeys) =>
      val inputValue = input.get.value
      Language.fromString(inputValue) match
        case Some(language) =>
          (
            State.LanguageChosen(newKeys, language),
            Output.Success(s"Selected language: $language") >> Output.Info(
              "Please provide context for the translations. This will be supplied to the translation model and posted on Slack"
            )
          )
        case None =>
          (
            State.ReadKeys(newKeys),
            Output.Error(s"Invalid language $inputValue")
          )
    case LanguageChosen(newKeys, language) =>
      val context = input.get.value
      (
        State.ContextProvided(newKeys, language, context),
        Output.Success(s"Using context: '$context'")
      )
    case State.ContextProvided(newKeys, language, context) =>
      val sortedKeys = newKeys.toList.sorted
      (
        State.FirstTranslation(
          newKeys,
          language,
          sortedKeys,
          Map.empty,
          context
        ),
        Output.Info(
          s"Please provide the meaning of key '${sortedKeys.head}' in $language."
        )
      )
    case State.FirstTranslation(
          newKeys,
          language,
          remainingKeys,
          providedTranslations,
          context
        ) =>
      val inputValue = input.get.value
      val updatedTranslations =
        providedTranslations + (remainingKeys.head -> inputValue)
      if (remainingKeys.length > 1)
        (
          State.ProvidedTranslation(
            newKeys,
            language,
            remainingKeys.tail,
            updatedTranslations,
            context
          ),
          Output.Info(
            s"Please provide the meaning of key '${remainingKeys(1)}' in $language."
          )
        )
      else
        (
          State.Translate(language, updatedTranslations, context),
          Output.Info(s"Translating ${updatedTranslations.size} keys ...")
        )
    case State.ProvidedTranslation(
          newKeys,
          language,
          remainingKeys,
          providedTranslations,
          context
        ) =>
      val inputValue = input.get.value
      val updatedTranslations =
        providedTranslations + (remainingKeys.head -> inputValue)
      if (remainingKeys.length > 1)
        (
          State.ProvidedTranslation(
            newKeys,
            language,
            remainingKeys.tail,
            updatedTranslations,
            context
          ),
          Output.Info(
            s"Please provide the meaning of key '${remainingKeys.head}' in $language."
          )
        )
      else
        (
          State.Translate(language, updatedTranslations, context),
          Output.Info(s"Translating ${updatedTranslations.size} keys ...")
        )

    case State.Translate(sourceLanguage, providedTranslations, context) =>
      val translator =
        DeepLTranslator.make("https://api-free.deepl.com", apiKey)
      val keys        = providedTranslations.toList.map(_._1)
      val sourceTexts = providedTranslations.toList.map(_._2)
      val languagesToTranslate =
        Language.values.toList.filterNot(_ == sourceLanguage)
      val result = languagesToTranslate.map { targetLanguage =>
        translator.translateWithContext(
          sourceLanguage,
          targetLanguage,
          context,
          sourceTexts
        )
      }.sequence
      result match
        case Left(error) =>
          (State.Exit(1), Output.Error(s"Translation failed with $error"))
        case Right(translations) =>
          val translationsWithLanguages = languagesToTranslate
            .zip(translations)
            .map { case (language, translation) =>
              language -> keys.zip(translation).toMap
            }
            .toMap
          (
            State.Translated(
              Map(
                sourceLanguage -> providedTranslations
              ) ++ translationsWithLanguages,
              context
            ),
            Output.Success(
              s"Translated to ${languagesToTranslate.length} languages successfully!"
            ) >>
              Output.Info(s"Sending translations to Slack...")
          )
    case State.Translated(translations, context) =>
      val slackClient = SlackClient.make(config.slackConfig.webhookUrl)
      val message = MessageFormatter.make.format(translations, Some(context))
      slackClient.send(message) match
        case Left(value) =>
          (
            State.Exit(1),
            Output.Error(s"Failed to post translations to slack: $value")
          )
        case Right(value) =>
          (
            State.SentSlackMessage(translations),
            Output.Success("Successfully posted translations to Slack!")
          )
    case State.SentSlackMessage(translations) =>
      (State.Exit(0), Output.Empty)
    case State.Exit(status) => (State.Exit(status), Output.Info("Exiting"))

private def makeApp(config: Config) = CliApp.make[State, Input, Output](
  step = step(config),
  parseInput = line => Some(Input(line)),
  formatOutput = formatOutput,
  initialOutput = Output.Info(
    "Auto-translations. Follow the steps below to generate translations for new keys automatically!"
  ),
  exit = {
    case s @ State.Exit(_) => true
    case _                 => false
  },
  requiresInput = {
    case State.ReadKeys(_)                        => true
    case State.FirstTranslation(_, _, _, _, _)    => true
    case State.ProvidedTranslation(_, _, _, _, _) => true
    case State.LanguageChosen(_, _)               => true
    case _                                        => false
  }
)
