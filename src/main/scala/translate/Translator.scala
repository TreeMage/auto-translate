package translate

import common.Language

trait Translator[+E]:
  def translate(
      sourceLanguage: Language,
      targetLanguage: Language,
      inputs: List[String]
  ): Either[E, List[String]]
  def translateWithContext(
      sourceLanguage: Language,
      targetLanguage: Language,
      context: String,
      inputs: List[String]
  ): Either[E, List[String]]
