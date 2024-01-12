package cli

import com.monovore.decline.Command

object CliApp {
  private val command = Command(
    name = "auto-translate",
    header =
      "A tool for discovering, translating and posting new translation keys in projects using react/i18n-next."
  )(
    InitializeCommand.command orElse SnapshotCommand.command orElse RunCommand.command
  )
  def run(args: Seq[String], env: Map[String, String]): Unit =
    command.parse(args, env) match
      case Left(help) if help.errors.isEmpty =>
        println(help)
        sys.exit(0)

      case Left(help) =>
        System.err.println(help)
        sys.exit(1)

      case Right(_) => ()
}
