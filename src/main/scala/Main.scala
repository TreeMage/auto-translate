import cli.CliApp

@main def main(args: String*): Unit =
  CliApp.run(args, sys.env)
