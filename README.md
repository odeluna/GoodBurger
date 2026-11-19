# GoodBurger First Edition

GoodBurger includes the ordering app and a free eight-card poker game on the
Rewards page. Each side plays its best five cards. Reveal the dealer's hand to
see who wins, then deal again or return to Rewards.

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
