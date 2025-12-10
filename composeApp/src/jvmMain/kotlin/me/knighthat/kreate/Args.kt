package me.knighthat.kreate

import picocli.CommandLine
import java.util.concurrent.Callable


@CommandLine.Command(
    name = "kreate",
    description = ["Music your way"]
)
class Args : Callable<Int> {

    companion object {

        var verbose: Boolean = false
            private set
    }

    @CommandLine.Option(
        names = ["-v", "--verbose"],
        description = ["Enable lowest logging level, shows everything"]
    )
    private var verbose: Boolean = false

    @CommandLine.Option(
        names = ["-h", "--help"],
        description = ["Print this menu"],
        usageHelp = true
    )
    private var helpRequested: Boolean = false

    override fun call(): Int {
        Args.verbose = this.verbose

        return 0
    }
}