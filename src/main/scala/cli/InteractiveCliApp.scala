package cli

trait InteractiveCliApp[State, Input, Output]:
  def run(initialState: State): Unit

object InteractiveCliApp:
  def make[State, Input, Output](
      step: (State, Option[Input]) => (State, Output),
      parseInput: String => Option[Input],
      formatOutput: Output => String,
      initialOutput: Output,
      exit: State => Boolean,
      requiresInput: State => Boolean
  ): InteractiveCliApp[State, Input, Output] =
    new InteractiveCliApp[State, Input, Output]:
      override def run(initialState: State): Unit =
        var state = initialState
        println(formatOutput(initialOutput))
        while (!exit(state))
          if (requiresInput(state))
            parseInput(io.StdIn.readLine()) match
              case input @ Some(_) =>
                val (newState, output) = step(state, input)
                println(formatOutput(output))
                state = newState
              case None => Console.err.println("Invalid input.")
          else
            val (newState, output) = step(state, None)
            println(formatOutput(output))
            state = newState
