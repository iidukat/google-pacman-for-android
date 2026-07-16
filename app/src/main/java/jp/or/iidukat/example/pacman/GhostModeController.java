package jp.or.iidukat.example.pacman;

import jp.or.iidukat.example.pacman.entity.Ghost;
import jp.or.iidukat.example.pacman.entity.Ghost.GhostMode;
import jp.or.iidukat.example.pacman.entity.Pacman;

class GhostModeController {

    // Thresholds (dots eaten after a life loss) at which each ghost leaves the pen.
    private static final int[] PEN_LEAVING_FOOD_LIMITS = { 0, 7, 17, 32 };

    private final PacmanGame game;

    GhostMode mainGhostMode;
    GhostMode lastMainGhostMode;
    private boolean alternatePenLeavingScheme;
    private int alternateDotCount;
    private double currentPlayerSpeed;
    private double currentDotEatingSpeed;

    GhostModeController(PacmanGame game) {
        this.game = game;
    }

    GhostMode getMainGhostMode() { return mainGhostMode; }

    GhostMode getLastMainGhostMode() { return lastMainGhostMode; }

    void restartPenLeavingForNewLevel() {
        alternatePenLeavingScheme = false;
    }

    void restartPenLeavingForNewLife() {
        alternatePenLeavingScheme = true;
        alternateDotCount = 0;
    }

    void switchMainGhostMode(GhostMode ghostMode, boolean justRestartGame) {
        Ghost[] ghosts = game.getGhosts();
        if (ghostMode == GhostMode.FRIGHTENED
                && game.getLevelConfig().getFrightTime() == 0) {
            for (Ghost ghost : ghosts) {
                ghost.setReverseDirectionsNext(true); // If frightTime is 0, a frightened ghost only reverses its direction.
            }
        } else {
            GhostMode oldMainGhostMode = mainGhostMode;
            if (ghostMode == GhostMode.FRIGHTENED
                    && mainGhostMode != GhostMode.FRIGHTENED) {
                lastMainGhostMode = mainGhostMode;
            }
            mainGhostMode = ghostMode;
            if (ghostMode == GhostMode.FRIGHTENED
                || oldMainGhostMode == GhostMode.FRIGHTENED) {
                game.playAmbientSound();
            }
            switch (ghostMode) {
            case CHASE:
            case SCATTER:
                currentPlayerSpeed = game.getLevelConfig().getPlayerSpeed() * 0.8f;
                currentDotEatingSpeed = game.getLevelConfig().getDotEatingSpeed() * 0.8f;
                break;
            case FRIGHTENED:
                currentPlayerSpeed = game.getLevelConfig().getPlayerFrightSpeed() * 0.8f;
                currentDotEatingSpeed = game.getLevelConfig().getDotEatingFrightSpeed() * 0.8f;
                game.getGameTimerManager().setFrightModeTime(game.getLevelConfig().getFrightTotalTime());
                game.resetModeScoreMultiplier();
                break;
            }
            for (Ghost ghost : ghosts) {
                if (ghostMode != GhostMode.ENTERING_PEN && !justRestartGame) {
                    ghost.setModeChangedWhileInPen(true);
                }
                if (ghostMode == GhostMode.FRIGHTENED) {
                    ghost.setEatenInThisFrightMode(false);
                }
                if (!ghost.isEatenOrInPenCycle() || justRestartGame) {

                    // If it is not immediately after restart the game (justRestartGame:false),
                    // a ghost reverses its direction
                    // when its mode changes from other than FRIGHTENED (CHASE or SCATTER) to another mode.
                    if (!justRestartGame && ghost.getMode() != GhostMode.FRIGHTENED
                            && ghost.getMode() != ghostMode) {
                        ghost.setReverseDirectionsNext(true);
                    }

                    // If it is not immediately after restart the game
                    // and a mode of each ghost is any of EATEN, IN_PEN, LEAVING_PEN, RE_LEAVING_FROM_PEN, or ENTERING_PEN,
                    // it is not updated.
                    ghost.switchGhostMode(ghostMode);
                }
            }

            Pacman pacman = game.getPacman();
            pacman.setFullSpeed(currentPlayerSpeed);
            pacman.setDotEatingSpeed(currentDotEatingSpeed);
            pacman.setTunnelSpeed(currentPlayerSpeed);
            pacman.changeSpeed();
        }
    }

    void figureOutPenLeaving() {
        Ghost pinky = game.getPinky();
        Ghost inky = game.getInky();
        Ghost clyde = game.getClyde();
        if (alternatePenLeavingScheme) {
            // By using a number of dots eaten after a level restart,
            // manage the timing of the ghosts leaving from the pen.
            alternateDotCount++;
            if (alternateDotCount == PEN_LEAVING_FOOD_LIMITS[1]) {
                pinky.setFreeToLeavePen(true);
            } else if (alternateDotCount == PEN_LEAVING_FOOD_LIMITS[2]) {
                inky.setFreeToLeavePen(true);
            } else if (alternateDotCount == PEN_LEAVING_FOOD_LIMITS[3]) {
                if (clyde.getMode() == GhostMode.IN_PEN) {
                    alternatePenLeavingScheme = false;
                }
            }
        } else if (pinky.getMode() == GhostMode.IN_PEN
                || pinky.getMode() == GhostMode.EATEN) {
            pinky.incrementDotCount();
            if (pinky.getDotCount() >= game.getLevelConfig().getPenLeavingLimits()[1]) {
                pinky.setFreeToLeavePen(true);
            }
        } else if (inky.getMode() == GhostMode.IN_PEN
                || inky.getMode() == GhostMode.EATEN) {
            inky.incrementDotCount();
            if (inky.getDotCount() >= game.getLevelConfig().getPenLeavingLimits()[2]) {
                inky.setFreeToLeavePen(true);
            }
        } else if (clyde.getMode() == GhostMode.IN_PEN
                || clyde.getMode() == GhostMode.EATEN) {
            clyde.incrementDotCount();
            if (clyde.getDotCount() >= game.getLevelConfig().getPenLeavingLimits()[3]) {
                clyde.setFreeToLeavePen(true);
            }
        }
    }

    void finishFrightMode() {
        switchMainGhostMode(lastMainGhostMode, false);
    }
}
