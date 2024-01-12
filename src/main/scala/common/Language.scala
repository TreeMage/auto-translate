package common

import upickle.default._

enum Language:
  case English
  case Norwegian

object Language:
  def fromString(value: String): Option[Language] =
    value.toLowerCase.trim match
      case "english"   => Some(Language.English)
      case "norwegian" => Some(Language.Norwegian)
      case _           => None

  def all: List[Language] = Language.values.toList
  given ReadWriter[Language] = readwriter[String].bimap[Language](
    {
      case English   => "EN"
      case Norwegian => "NB"
    },
    {
      case "EN" => Language.English
      case "NB" => Language.Norwegian
      case fail =>
        throw new RuntimeException(s"Cannot decode $fail to a valid language.")
    }
  )
