package cli

import Output.{Empty, Error, Info, Multiple, Success}
import State.{FirstTranslation, LanguageChosen, ProvidedTranslation, ReadKeys, Translated}
import cli.InteractiveCliApp
import common.{AppConfig, Language}
import extraction.{DiffFileManager, NewKeyExtractor}
import translate.DeepLTranslator
import cats.implicits.*
import slack.{MessageFormatter, SlackClient}
import upload.SimpleLocalizeTranslationUploader

import scala.io.*
import java.nio.file.{Path, Paths}
import scala.annotation.targetName

object RunLogic:
  def run(appConfig: AppConfig): Unit =
    val app = makeApp(appConfig)
    app.run(State.Start)

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
  case UploadedToSimpleLocalize(
      translations: Map[Language, Map[String, String]]
  )
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

extension (text: String)
  def info: Output    = Output.Info(text)
  def error: Output   = Output.Error(text)
  def success: Output = Output.Success(text)

case class Input(value: String)

private def formatOutput(output: Output): String = output match
  case Empty            => ""
  case Info(value)      => s"$value\n"
  case Error(value)     => s"${AnsiColor.RED}$value${AnsiColor.RESET}\n"
  case Success(value)   => s"${AnsiColor.GREEN}$value${AnsiColor.RESET}\n"
  case Multiple(values) => values.map(formatOutput).mkString("\n")

private def step(
    config: AppConfig
)(state: State, input: Option[Input]): (State, Output) =
  state match
    case State.Start =>
      val keys =
        for
          oldKeys <- DiffFileManager.make.readKeysFromFile(config.keyFilePath)
          newKeys <- NewKeyExtractor.make.extractNewKeys(
            config.srcDir,
            oldKeys
          )
        yield newKeys
      keys match
        case Left(exception) =>
          (State.Exit(1), s"Key extraction failed: $exception.".error)
        case Right(newKeys) =>
          if (newKeys.isEmpty)
            (
              State.Exit(0),
              s"Did not find any new keys in ${config.srcDir}. Nothing to do!".success
            )
          else
            (
              State.ReadKeys(newKeys),
              s"Extracted ${newKeys.size} new keys from ${config.srcDir}".success
                >>
                  s"Please select a language (${Language.all.mkString(", ")}): ".info
            )
    case State.ReadKeys(newKeys) =>
      val inputValue = input.get.value
      Language.fromString(inputValue) match
        case Some(language) =>
          (
            State.LanguageChosen(newKeys, language),
            s"Selected language: $language".success >>
              "Please provide context for the translations. This will be supplied to the translation model and posted on Slack".info >>
              "If you do not want to provide context, leave this empty and just press Enter".info
          )
        case None =>
          (
            State.ReadKeys(newKeys),
            s"Invalid language $inputValue".error
          )
    case LanguageChosen(newKeys, language) =>
      val context = input.get.value
      (
        State.ContextProvided(newKeys, language, context),
        (if (context.nonEmpty) s"Using context: '$context'".success else Empty)
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
        s"Please provide the meaning of key '${sortedKeys.head}' in $language.".info
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
          s"Please provide the meaning of key '${remainingKeys(1)}' in $language.".info
        )
      else
        (
          State.Translate(language, updatedTranslations, context),
          s"Translating ${updatedTranslations.size} keys ...".info
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
          s"Please provide the meaning of key '${remainingKeys.head}' in $language.".info
        )
      else
        (
          State.Translate(language, updatedTranslations, context),
          s"Translating ${updatedTranslations.size} keys ...".info
        )

    case State.Translate(sourceLanguage, providedTranslations, context) =>
      val deepLConfig = config.deepLConfig
      val translator =
        DeepLTranslator.make(deepLConfig.baseUrl, deepLConfig.apiKey)
      val keys        = providedTranslations.toList.map(_._1)
      val sourceTexts = providedTranslations.toList.map(_._2)
      val languagesToTranslate =
        Language.values.toList.filterNot(_ == sourceLanguage)
      val result = languagesToTranslate.map { targetLanguage =>
        if (context.nonEmpty)
          translator.translateWithContext(
            sourceLanguage,
            targetLanguage,
            context,
            sourceTexts
          )
        else
          translator.translate(
            sourceLanguage,
            targetLanguage,
            sourceTexts
          )
      }.sequence
      result match
        case Left(error) =>
          (State.Exit(1), s"Translation failed with $error".error)
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
            s"Translated to ${languagesToTranslate.length} languages successfully!".success
              >>
                (if (config.slackConfig.webhookUrl.nonEmpty)
                   s"Sending translations to Slack...".info
                 else
                   "No slack webhook provided. Not sending any messages to slack.".info)
          )
    case State.Translated(translations, context) =>
      if (config.slackConfig.webhookUrl.isEmpty)
        (
          State.SentSlackMessage(translations),
          "Uploading translations to SimpleLocalize ...".info
        )
      else
        val slackClient = SlackClient.make(config.slackConfig.webhookUrl)
        val message = MessageFormatter.make.format(translations, Some(context))
        slackClient.send(message) match
          case Left(value) =>
            (
              State.Exit(1),
              s"Failed to post translations to slack: $value".error
            )
          case Right(value) =>
            (
              State.SentSlackMessage(translations),
              "Successfully posted translations to Slack!".success
                >> "Uploading translations to SimpleLocalize ...".info
            )
    case State.SentSlackMessage(translations) =>
      val slConfig = config.simpleLocalizeConfig
      SimpleLocalizeTranslationUploader
        .make(slConfig.baseUrl, slConfig.apiKey)
        .upload(translations) match
        case Left(error) =>
          (
            State.Exit(1),
            s"Failed to upload translations: $error".error
          )
        case Right(value) =>
          (
            State.UploadedToSimpleLocalize(translations),
            "Successfully uploaded translations to SimpleLocalize.".success
              >> "Updating key file ...".info
          )
    case State.UploadedToSimpleLocalize(translations) =>
      val keys = translations.head._2.keys.toSet
      DiffFileManager.make.addKeysToFile(config.keyFilePath, keys) match
        case Left(throwable) =>
          (
            State.Exit(1),
            s"Failed to update key file at ${config.keyFilePath} due to: $throwable".error
          )
        case Right(value) =>
          (
            State.Exit(0),
            s"Successfully updated key file at ${config.keyFilePath}".success
          )
    case State.Exit(status) => (State.Exit(status), "Exiting".info)

private def makeApp(config: AppConfig) =
  InteractiveCliApp.make[State, Input, Output](
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
