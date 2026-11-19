# GoodBurger First Edition

GoodBurger includes the ordering app and a seven-card stud poker game on the
Rewards page. Both sides are dealt seven cards: two stay hidden, five are shown
one per street, and you bet, call, raise, or fold on each street. The best five
of the seven cards wins the pot at showdown.

Completing a showdown can claim the daily bonus. The first poker win earns the
one-time Card Shark award of 200 points. Existing balances and redemptions are
read from the same progress file.

## Windows

Extract the entire ZIP and double-click `run-windows.bat`. The package includes
the compiled app and Windows x64 JavaFX 21.0.6 libraries. It uses an installed
64-bit Java 21 or newer through JAVA_HOME or PATH.

## Build from source

With JDK 21 and Maven installed, run `./build.sh` to build,
`./run.sh` to launch, or `./test.sh` to check poker and rewards logic.
