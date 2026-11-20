/**
 * The rewards mini-games (Pipe Flight, seven-card stud Poker) and their shared progress.
 *
 * <p>This package has its own main() - RewardsApp - so the games can run standalone
 * during development (it understands the --preview and --smoke flags). The app-side
 * integration lives in edu.utsa.cs3443.goodburger.controller.RewardsPageController,
 * which hosts the games inside the rewards page. Player progress (points,
 * achievements) is shared across both entry points through ProgressStore, which
 * persists to a file in the data/ folder.
 *
 * <p>Game internals: PipeFlightGame / PipeFlightCanvas / SpriteAtlas / GameAudio
 * (the Canvas game), Poker / PokerWindow (seven-card stud), RewardGameDriver and
 * EconomyRules (scoring shared by both games), Achievements (unlockable rewards).
 */
package rewards;
