package jp.or.iidukat.example.pacman;

import jp.or.iidukat.example.pacman.entity.Sound;

class ChromeController {

    private final PacmanGame game;

    ChromeController(PacmanGame game) {
        this.game = game;
    }

    void createChrome() {
        game.canvasEl.reset();
        game.canvasEl.createScoreLabel();
        game.canvasEl.createScore();
        game.canvasEl.createLives();
        game.canvasEl.createLevel();
        game.canvasEl.createSoundIcon();
        updateSoundIcon();
    }

    void updateChrome() {
        updateChromeLevel();
        updateChromeLives();
        updateChromeScore();
    }

    void updateChromeLives() {
        if (game.canvasEl == null) return;
        game.canvasEl.getLives().update(game.lives);
    }

    void updateChromeLevel() {
        if (game.canvasEl == null) return;
        game.canvasEl.getLevel().update(game.level, LevelConfig.LEVEL_CONFIGS);
    }

    void updateChromeScore() {
        if (game.canvasEl == null) return;
        game.canvasEl.getScore().update(game.score);
    }

    void updateSoundIcon() {
        Sound soundEl = game.getSoundEl();
        if (game.soundManager.isAvailable()) {
            soundEl.setVisibility(true);
            if (game.soundManager.isPacManSound()) {
                soundEl.turnOn();
            } else {
                soundEl.turnOff();
            }
        } else {
            soundEl.setVisibility(false);
        }
    }
}
