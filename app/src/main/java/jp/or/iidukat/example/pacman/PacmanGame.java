package jp.or.iidukat.example.pacman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jp.or.iidukat.example.pacman.entity.PlayfieldActor;
import jp.or.iidukat.example.pacman.entity.PlayfieldActor.CurrentSpeed;
import jp.or.iidukat.example.pacman.entity.Fruit;
import jp.or.iidukat.example.pacman.entity.Ghost;
import jp.or.iidukat.example.pacman.entity.Ghost.GhostMode;
import jp.or.iidukat.example.pacman.entity.Pacman;
import jp.or.iidukat.example.pacman.entity.PacmanCanvas;
import jp.or.iidukat.example.pacman.entity.Playfield;
import jp.or.iidukat.example.pacman.entity.Playfield.Door;
import jp.or.iidukat.example.pacman.entity.Playfield.PathElement;
import jp.or.iidukat.example.pacman.entity.Playfield.PathElement.Dot;
import jp.or.iidukat.example.pacman.entity.ScoreLabel;
import jp.or.iidukat.example.pacman.entity.Sound;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.MotionEvent;

public class PacmanGame {

    private static final int DEFAULT_KILL_SCREEN_LEVEL = 256;
    
    public static enum GameplayMode {
        ORDINARY_PLAYING(0), GHOST_DIED(1), PLAYER_DYING(2), PLAYER_DIED(3),
        NEWGAME_STARTING(4), NEWGAME_STARTED(5), GAME_RESTARTING(6), GAME_RESTARTED(7),
        GAMEOVER(8), LEVEL_BEING_COMPLETED(9), LEVEL_COMPLETED(10),
        TRANSITION_INTO_NEXT_SCENE(11), CUTSCENE(13), KILL_SCREEN(14);
        
        private final int mode;
        
        private GameplayMode(int mode) {
            this.mode = mode;
        }

        public int getMode() {
            return mode;
        }

    }

    private static final PacmanLogger LOG = new PacmanLogger("PacmanSound");

    private final Context context;
    GameView view;
    private Bitmap sourceImage;
    SoundManager soundManager;
    private InputHandler inputHandler;
    GameTimerManager gameTimerManager;
    CutsceneController cutsceneController;
    GhostModeController ghostModeController;
    ChromeController chromeController;

    private boolean paused;
    private boolean started;
    private long randSeed;

    PacmanCanvas canvasEl;

    long score;
    private boolean extraLifeAwarded;
    int lives = 3;
    int level = 0;
    private int killScreenLevel = DEFAULT_KILL_SCREEN_LEVEL;
    LevelConfig levelConfig;
    private long globalTime = 0;

    private int intervalTime = 0;
    double gameplayModeTime = 0;
    private boolean ghostExitingPenNow = false;
    private int ghostEyesCount = 0;
    private boolean tilesChanged = false;
    GameplayMode gameplayMode;
    Timing timing;
    private boolean lostLifeOnThisLevel;

    private double cruiseElroySpeed;
    private Map<Double, Boolean[]> speedIntervals;

    private int modeScoreMultiplier;
    private boolean fruitShown;
    private Ghost ghostBeingEaten;

    private TickClock tickClock;

    PacmanGame(Context context) {
        this.context = context;
        gameTimerManager = new GameTimerManager(this);
        cutsceneController = new CutsceneController(this);
        ghostModeController = new GhostModeController(this);
        chromeController = new ChromeController(this);
    }

    public double rand() {
        long b = 4294967296L;
        long c = 134775813L;
        c = c * randSeed + 1;
        return (randSeed = c % b) / (double) b;
    }

    private void seed(long s) {
        this.randSeed = s;
    }

    private void restartActors() {
        getPacman().arrange();

        for (PlayfieldActor ghost : getGhosts())
            ghost.arrange();
    }

    private void createPlayfield() {
        canvasEl.createPlayfield(this);
    }
    
    private void resetPlayfield() {
        getPlayfieldEl().reset();
    }
    
    private void createReadyElement() {
        getPlayfieldEl().createReadyElement();
    }
    
    private void removeReadyElement() {
        getPlayfieldEl().removeReady();
    }
    
    private void createGameOverElement() {
        getPlayfieldEl().createGameOverElement();
    }

    void handleTouchStart(MotionEvent e) {
        inputHandler.onTouchStart(e);
    }

    void handleTouchMove(MotionEvent e) {
        inputHandler.onTouchMove(e);
    }

    void handleTouchEnd(MotionEvent e) {
        inputHandler.onTouchEnd(e);
    }

    void setDpadDir(Direction dir) {
        inputHandler.setDpadDir(dir);
    }

    private void startGameplay() {
        score = 0;
        extraLifeAwarded = false;
        lives = 3;
        level = 0;
        globalTime = 0;
        newLevel(true);
    }

    private void restartGameplay(boolean newGame) {
        seed(0);
        intervalTime = 0;
        gameplayModeTime = 0;
        ghostExitingPenNow = false;
        ghostEyesCount = 0;
        tilesChanged = false;
        updateCruiseElroySpeed();
        hideFruit();
        gameTimerManager.restartAll(levelConfig);
        restartActors();
        updateActorPositions();
        switchMainGhostMode(GhostMode.SCATTER, true);
        for (int i = 1; i < 4; i++) {
            getGhosts()[i].switchGhostMode(GhostMode.IN_PEN);
        }
        soundManager.resetDotEatingSound();

        if (newGame && cutsceneController.hasDebugCutscene()) {
            cutsceneController.consumeDebugCutscene();
            changeGameplayMode(GameplayMode.CUTSCENE);
        } else if (newGame) {
            changeGameplayMode(GameplayMode.NEWGAME_STARTING);
        } else {
            changeGameplayMode(GameplayMode.GAME_RESTARTING);
        }
//      changeGameplayMode(GameplayMode.LEVEL_COMPLETED); // for Cutscene debug
    }

    private void newGame() {
        chromeController.createChrome();
        createPlayfield();
        startGameplay();
    }

    private void killScreen() {
        seed(0);
        canvasEl.setVisibility(true);
        getPlayfieldEl().killScreen();
        changeGameplayMode(GameplayMode.KILL_SCREEN);
    }

    void newLevel(boolean newGame) {
        level++;
        levelConfig =
            level >= LevelConfig.LEVEL_CONFIGS.length
                ? LevelConfig.LEVEL_CONFIGS[LevelConfig.LEVEL_CONFIGS.length - 1]
                : LevelConfig.LEVEL_CONFIGS[level];
        ghostModeController.restartPenLeavingForNewLevel();
        lostLifeOnThisLevel = false;
        chromeController.updateChrome();
        resetPlayfield();
        restartGameplay(newGame);
        if (level == killScreenLevel) {
            killScreen();
        }
    }

    void newLife() {
        lostLifeOnThisLevel = true;
        ghostModeController.restartPenLeavingForNewLife();
        lives--;
        chromeController.updateChromeLives();

        if (lives == -1) {
            changeGameplayMode(GameplayMode.GAMEOVER);
        } else {
            restartGameplay(false);
        }
    }

    void switchMainGhostMode(GhostMode ghostMode, boolean justRestartGame) {
        ghostModeController.switchMainGhostMode(ghostMode, justRestartGame);
    }

    public void dotEaten(int[] dotPos) {
        getPlayfieldEl().decrementDotsRemaining();
        getPlayfieldEl().incrementDotsEaten();
        getPacman().changeSpeed(CurrentSpeed.PACMAN_EATING_DOT);
        soundManager.playDotEatingSound(gameplayMode == GameplayMode.ORDINARY_PLAYING);
        if (getPathElement(dotPos[1], dotPos[0]).getDot() == Dot.ENERGIZER) { // when eating an energizer
            switchMainGhostMode(GhostMode.FRIGHTENED, false);
            addToScore(50);
        } else { // when eating a normal food
            addToScore(10);
        }

        getPlayfieldEl().clearDot(dotPos[1], dotPos[0]);
        updateCruiseElroySpeed();
        gameTimerManager.resetForcePenLeaveTime();
        ghostModeController.figureOutPenLeaving();
        if (getPlayfieldEl().getDotsEaten() == 70
                || getPlayfieldEl().getDotsEaten() == 170) {
            showFruit();
        }
        if (getPlayfieldEl().getDotsRemaining() == 0) {
            finishLevel();
        }
        playAmbientSound();
    }

    public PathElement getPathElement(int x, int y) {
        return getPlayfieldEl().getPathElement(x, y);
    }

    public int getDotsRemaining() {
        return getPlayfieldEl().getDotsRemaining();
    }

    void hideFruit() {
        fruitShown = false;
        getFruitEl().hide();
    }

    private void showFruit() {
        fruitShown = true;
        getFruitEl().show();
        gameTimerManager.fruitTime =
            (int) timing.fruitShowMin
                + (int) ((timing.fruitShowMax - timing.fruitShowMin) * rand());
    }

    public void eatFruit() {
        if (fruitShown) {
            soundManager.playTrack("fruit", 0);
            fruitShown = false;
            getFruitEl().eaten();
            gameTimerManager.fruitTime = (int) timing.fruitScore;
            addToScore(levelConfig.getFruitScore());
        }
    }

    private void updateActorTargetPositions() {
        Ghost[] ghosts = getGhosts();
        for (Ghost ghost : ghosts) {
            ghost.updateTargetPos();
        }
    }

    private void moveActors() {
        getPacman().move();
        Ghost[] ghosts = getGhosts();
        for (PlayfieldActor actor : ghosts) {
            actor.move();
        }
    }

    private void ghostDies(int index) {
        soundManager.playTrack("eating_ghost", 0);
        addToScore(200 * modeScoreMultiplier);
        modeScoreMultiplier *= 2;
        ghostBeingEaten = getPlayfieldEl().getGhosts()[index];
        changeGameplayMode(GameplayMode.GHOST_DIED);
    }

    private void pacmanDies() {
        changeGameplayMode(GameplayMode.PLAYER_DYING);
    }

    private void detectCollisions() {
        tilesChanged = false;
        Pacman pacman = getPacman();
        Ghost[] ghosts = getGhosts();
        for (int i = 0; i < 4; i++) {
            if (ghosts[i].getTilePos()[0] == pacman.getTilePos()[0]
                    && ghosts[i].getTilePos()[1] == pacman.getTilePos()[1]) {
                if (ghosts[i].getMode() == GhostMode.FRIGHTENED) {
                    ghostDies(i);
                    return;
                } else if (ghosts[i].getMode() != GhostMode.EATEN
                        && ghosts[i].getMode() != GhostMode.IN_PEN
                        && ghosts[i].getMode() != GhostMode.LEAVING_PEN
                        && ghosts[i].getMode() != GhostMode.RE_LEAVING_FROM_PEN
                        && ghosts[i].getMode() != GhostMode.ENTERING_PEN) {
                    pacmanDies();
                }
            }
        }
    }

    public void updateCruiseElroySpeed() {
        double speed = levelConfig.getGhostSpeed() * 0.8f;
        if (!lostLifeOnThisLevel || getClyde().getMode() != GhostMode.IN_PEN) {
            LevelConfig c = levelConfig;
            if (getPlayfieldEl().getDotsRemaining() < c.getElroyDotsLeftPart2()) {
                speed = c.getElroySpeedPart2() * 0.8f;
            } else if (getPlayfieldEl().getDotsRemaining() < c.getElroyDotsLeftPart1()) {
                speed = c.getElroySpeedPart1() * 0.8f;
            }
        }
        if (speed != cruiseElroySpeed) {
            cruiseElroySpeed = speed;
            getBlinky().changeSpeed(); // update the speed of Blinky.
        }
    }

    // argument:speed
    // return value: an array of boolean using intervalTime as its index.
    //               an element of this array represents whether it is necessary to move the character in a intervalTime.
    // ex) speed: 0.64 -> return value: [false, true, false, true, false, true, ...]
    public Boolean[] getSpeedIntervals(double speed) {
        Double key = Double.valueOf(speed);
        if (!speedIntervals.containsKey(key)) {
            double distance = 0;
            double lastPos = 0;
            List<Boolean> movabilityTimeTable = new ArrayList<Boolean>();
            for (int i = 0; i < GameConstants.DEFAULT_FPS; i++) {
                distance += speed;
                double pos = Math.floor(distance);
                if (pos > lastPos) {
                    movabilityTimeTable.add(true);
                    lastPos = pos;
                } else {
                    movabilityTimeTable.add(false);
                }
            }
            speedIntervals.put(key, movabilityTimeTable.toArray(new Boolean[0]));
        }
        return speedIntervals.get(key);
    }

    private void finishLevel() {
        changeGameplayMode(GameplayMode.LEVEL_BEING_COMPLETED);
    }

    void applyModeState(GameplayMode mode) {
        gameplayMode = mode;
        switch (mode) {
        case PLAYER_DYING:
            gameplayModeTime = timing.playerDying;
            break;
        case PLAYER_DIED:
            gameplayModeTime = timing.playerDied;
            break;
        case GAME_RESTARTING:
            gameplayModeTime = timing.gameRestarting;
            break;
        case GAME_RESTARTED:
            gameplayModeTime = timing.gameRestarted;
            break;
        case NEWGAME_STARTING:
            gameplayModeTime = timing.newgameStarting;
            break;
        case NEWGAME_STARTED:
            lives--;
            gameplayModeTime = timing.newgameStarted;
            break;
        case GAMEOVER:
        case KILL_SCREEN:
            gameplayModeTime = timing.gameover;
            break;
        case LEVEL_BEING_COMPLETED:
            gameplayModeTime = timing.levelBeingCompleted;
            break;
        case LEVEL_COMPLETED:
            gameplayModeTime = timing.levelCompleted;
            break;
        case TRANSITION_INTO_NEXT_SCENE:
            gameplayModeTime = timing.transition;
            break;
        case GHOST_DIED:
            gameplayModeTime = timing.ghostDied;
            break;
        }
    }

    void applyModeEffects(GameplayMode mode) {
        if (mode != GameplayMode.CUTSCENE) {
            getPacman().updateAppearance();
            Ghost[] ghosts = getGhosts();
            for (PlayfieldActor actor : ghosts) {
                actor.updateAppearance();
            }
        }
        switch (mode) {
        case ORDINARY_PLAYING:
            playAmbientSound();
            break;
        case PLAYER_DYING:
            LOG.d(() -> "applyModeEffects: PLAYER_DYING -> stopAll()");
            soundManager.stopAll();
            break;
        case PLAYER_DIED:
            soundManager.playTrack("death", 0);
            break;
        case GAME_RESTARTING:
            canvasEl.setVisibility(false);
            break;
        case GAME_RESTARTED:
            soundManager.stopAll();
            canvasEl.setVisibility(true);
            getDoorEl().setVisibility(true);
            createReadyElement();
            break;
        case NEWGAME_STARTING:
            getDoorEl().setVisibility(true);
            createReadyElement();
            soundManager.stopAll();
            soundManager.playTrack("start_music", 0, true);
            break;
        case NEWGAME_STARTED:
            chromeController.updateChromeLives();
            break;
        case GAMEOVER:
        case KILL_SCREEN:
            removeReadyElement();
            soundManager.stopAll();
            createGameOverElement();
            break;
        case LEVEL_BEING_COMPLETED:
            soundManager.stopAll();
            break;
        case LEVEL_COMPLETED:
            getDoorEl().setVisibility(false);
            break;
        case TRANSITION_INTO_NEXT_SCENE:
            canvasEl.setVisibility(false);
            break;
        case GHOST_DIED:
            break;
        case CUTSCENE:
            cutsceneController.start();
            break;
        }
    }

    void changeGameplayMode(GameplayMode mode) {
        applyModeState(mode);
        applyModeEffects(mode);
    }

    void toggleSound() {
        if (soundManager.isPacManSound()) {
            soundManager.stopAll();
            soundManager.setPacManSound(false);
        } else {
            soundManager.setPacManSound(true);
            playAmbientSound();
        }
    }

    private void updateActorPositions() {
        getPacman().updateElPos();
        Ghost[] ghosts = getGhosts();
        for (PlayfieldActor actor : ghosts) {
            actor.updateElPos();
        }
    }

    private void blinkEnergizers() {
        getPlayfieldEl().blinkEnergizers(gameplayMode, globalTime, timing.energizerBlink);
    }

    private void blinkScoreLabels() {
        getScoreLabelEl().update(gameplayMode, globalTime, timing.scoreLabelBlink);
    }

    void finishFrightMode() {
        ghostModeController.finishFrightMode();
    }

    void handleTimers() {
        gameTimerManager.handleTimers();
    }

    void onGameplayModeTimerExpired() {
        Ghost[] ghosts = getGhosts();
        switch (gameplayMode) {
        case GHOST_DIED:
            changeGameplayMode(GameplayMode.ORDINARY_PLAYING);
            incrementGhostEyesCount();
            playAmbientSound();
            ghostBeingEaten.resetDisplayOrder();
            ghostBeingEaten.switchGhostMode(GhostMode.EATEN);
            // If there is no ghost frightened, finish fright mode.
            boolean frightenedGhostExists = false;
            for (Ghost ghost : ghosts) {
                if (ghost.getMode() == GhostMode.FRIGHTENED
                    || (ghost.getMode() == GhostMode.IN_PEN
                            || ghost.getMode() == GhostMode.RE_LEAVING_FROM_PEN)
                        && !ghost.isEatenInThisFrightMode()) {
                    frightenedGhostExists = true;
                    break;
                }
            }
            if (!frightenedGhostExists) {
                finishFrightMode();
            }
            break;
        case PLAYER_DYING:
            changeGameplayMode(GameplayMode.PLAYER_DIED);
            break;
        case PLAYER_DIED:
            newLife();
            break;
        case NEWGAME_STARTING:
            changeGameplayMode(GameplayMode.NEWGAME_STARTED);
            break;
        case GAME_RESTARTING:
            changeGameplayMode(GameplayMode.GAME_RESTARTED);
            break;
        case GAME_RESTARTED:
        case NEWGAME_STARTED:
            getPlayfieldEl().removeReady();
            changeGameplayMode(GameplayMode.ORDINARY_PLAYING);
            break;
        case GAMEOVER:
            getPlayfieldEl().removeGameover();
            break;
        case LEVEL_BEING_COMPLETED:
            changeGameplayMode(GameplayMode.LEVEL_COMPLETED);
            break;
        case LEVEL_COMPLETED:
            changeGameplayMode(GameplayMode.TRANSITION_INTO_NEXT_SCENE);
            break;
        case TRANSITION_INTO_NEXT_SCENE:
            if (levelConfig.getCutsceneId() != 0) {
                cutsceneController.setCutsceneId(levelConfig.getCutsceneId());
                changeGameplayMode(GameplayMode.CUTSCENE);
            } else {
                canvasEl.setVisibility(true);
                newLevel(false);
            }
            break;
        }
    }

    void tick() {
        long now = new Date().getTime();
        if (paused) {
            tickClock.onPaused(now);
            return;
        }

        int latencyMultiplyer = tickClock.advance(now);
        if (gameplayMode == GameplayMode.CUTSCENE) { // Cutscene
            for (int i = 0; i < tickClock.tickMultiplier + latencyMultiplyer; i++) {
                // run multiple time depending on the tickMultiplier and latency
                cutsceneController.advance();
                intervalTime = (intervalTime + 1) % GameConstants.DEFAULT_FPS;
                globalTime++;
            }
            cutsceneController.check();
            blinkScoreLabels();
        } else {
            chromeController.updateSoundIcon();

            for (int i = 0; i < tickClock.tickMultiplier + latencyMultiplyer; i++) {
                // run multiple time depending on the tickMultiplier and latency
                inputHandler.processDpadInput();
                moveActors();
                if (gameplayMode == GameplayMode.ORDINARY_PLAYING) {
                    if (tilesChanged) {
                        detectCollisions();
                        updateActorTargetPositions();
                    }
                }

                globalTime++;
                intervalTime = (intervalTime + 1) % GameConstants.DEFAULT_FPS;
                blinkEnergizers();
                blinkScoreLabels();
                handleTimers();
            }
        }
        setTimeout();
    }

    private void extraLife() {
        soundManager.playTrack("extra_life", 0);
        extraLifeAwarded = true;
        lives++;
        if (lives > 5) {
            lives = 5;
        }
        chromeController.updateChromeLives();
    }

    private void addToScore(int s) {
        score += s;
        if (!extraLifeAwarded && score > 10000) {
            extraLife();
        }
        chromeController.updateChromeScore();
    }

    public void playAmbientSound() {
        final String ambient;
        if (gameplayMode == GameplayMode.ORDINARY_PLAYING
                || gameplayMode == GameplayMode.GHOST_DIED) {
            Playfield playfieldEl = getPlayfieldEl();
            ambient = ghostEyesCount != 0
                    ? "ambient_eyes"
                    : ghostModeController.getMainGhostMode() == GhostMode.FRIGHTENED
                        ? "ambient_fright"
                        : playfieldEl.getDotsEaten() > 241
                            ? "ambient_4"
                            : playfieldEl.getDotsEaten() > 207
                                ? "ambient_3"
                                : playfieldEl.getDotsEaten() > 138
                                    ? "ambient_2" : "ambient_1";
        } else {
            ambient = null;
        }
        LOG.d(() -> "playAmbientSound: mode=" + gameplayMode + " ambient=" + ambient);
        soundManager.playAmbient(ambient);
    }

    private void initTiming() {
        timing = new Timing(!soundManager.isPacManSound());
    }

    private void setTimeout() {
        view.redrawHandler.sleep(tickClock.getTickIntervalMs());
    }

    private void createCanvasElement() {
        canvasEl = new PacmanCanvas(sourceImage);
        canvasEl.init();
    }

    private void start() {
        started = true;
        setTimeout();
        newGame();
    }

    void startNewGame() {
        setDefaultKillScreenLevel();
        start();
    }

    void showKillScreen() {
        setKillScreenLevel(1);
        start();
    }

    void showCutscene(int id) {
        cutsceneController.setDebugCutsceneId(id);
        setDefaultKillScreenLevel();
        start();
    }

    void setOnDebugCutsceneFinished(Runnable callback) {
        cutsceneController.setOnFinished(callback);
    }

    private void setKillScreenLevel(int level) {
        this.killScreenLevel = level;
    }

    private void setDefaultKillScreenLevel() {
        this.killScreenLevel = DEFAULT_KILL_SCREEN_LEVEL;
    }

    void init() {
        sourceImage =
            BitmapFactory.decodeResource(
                context.getResources(),
                R.drawable.pacman_sprite);
        createCanvasElement();
        speedIntervals = new HashMap<Double, Boolean[]>();
        soundManager = new SoundManager(context);
        inputHandler = new InputHandler(this);
        tickClock = new TickClock();
        tickClock.init();
        initTiming();
    }

    void resume() {
        soundManager.reinit();
        if (started && paused) {
            tickClock.onResumed();
            paused = false;
            soundManager.resumeAmbient();
            tick();
        }
    }

    void pause() {
        paused = true;
        soundManager.destroy();
    }
    
    public PacmanCanvas getCanvasEl() {
        return canvasEl;
    }

    private ScoreLabel getScoreLabelEl() {
        if (canvasEl == null) {
            return null;
        }
        return canvasEl.getScoreLabel();
    }

    Sound getSoundEl() {
        if (canvasEl == null) {
            return null;
        }
        return canvasEl.getSound();
    }

    public Playfield getPlayfieldEl() {
        if (canvasEl == null) {
            return null;
        }
        return canvasEl.getPlayfield();
    }

    public Pacman getPacman() {
        if (canvasEl == null) {
            return null;
        }
        
        Playfield playfieldEl = canvasEl.getPlayfield();
        if (playfieldEl == null) {
            return null;
        }
        
        return playfieldEl.getPacman();
    }

    Ghost[] getGhosts() {
        if (canvasEl == null) {
            return null;
        }

        Playfield playfieldEl = canvasEl.getPlayfield();
        if (playfieldEl == null) {
            return null;
        }
        return playfieldEl.getGhosts();
    }
    
    public Ghost getBlinky() {
        return getPlayfieldEl().getBlinky();
    }
    
    public Ghost getPinky() {
        return getPlayfieldEl().getPinky();
    }
    
    public Ghost getInky() {
        return getPlayfieldEl().getInky();
    }

    public Ghost getClyde() {
        return getPlayfieldEl().getClyde();
    }
    
    private Fruit getFruitEl() {
        Playfield playfieldEl = canvasEl.getPlayfield();
        if (playfieldEl == null) {
            return null;
        }
        return playfieldEl.getFruit();
    }

    private Door getDoorEl() {
        Playfield playfieldEl = canvasEl.getPlayfield();
        if (playfieldEl == null) {
            return null;
        }
        return playfieldEl.getDoor();
    }

    SoundManager getSoundManager() {
        return soundManager;
    }

    GameTimerManager getGameTimerManager() {
        return gameTimerManager;
    }

    public boolean isPacManSound() {
        return soundManager.isPacManSound();
    }

    public void setPacManSound(boolean val) {
        soundManager.setPacManSound(val);
    }

    public LevelConfig getLevelConfig() {
        return levelConfig;
    }

    public long getGlobalTime() {
        return globalTime;
    }

    public int getFrightModeTime() {
        return gameTimerManager.getFrightModeTime();
    }

    public int getIntervalTime() {
        return intervalTime;
    }

    public double getGameplayModeTime() {
        return gameplayModeTime;
    }

    void decrementGameplayModeTime() {
        gameplayModeTime--;
    }

    void resetGameplayModeTime() {
        gameplayModeTime = 0;
    }

    Timing getTiming() {
        return timing;
    }

    public double getFrightBlinkTiming() {
        return timing.frightBlink;
    }

    public double getPlayerDiedTiming() {
        return timing.playerDied;
    }

    public GameplayMode getGameplayMode() {
        return gameplayMode;
    }

    public GhostMode getMainGhostMode() {
        return ghostModeController.getMainGhostMode();
    }

    public GhostMode getLastMainGhostMode() {
        return ghostModeController.getLastMainGhostMode();
    }

    void resetModeScoreMultiplier() {
        modeScoreMultiplier = 1;
    }

    public Ghost getGhostBeingEaten() {
        return ghostBeingEaten;
    }

    public int getModeScoreMultiplier() {
        return modeScoreMultiplier;
    }

    public boolean isLostLifeOnThisLevel() {
        return lostLifeOnThisLevel;
    }

    public boolean isGhostExitingPenNow() {
        return ghostExitingPenNow;
    }

    public boolean setGhostExitingPenNow(boolean ghostExitingPenNow) {
        return this.ghostExitingPenNow = ghostExitingPenNow;
    }

    public int getGhostEyesCount() {
        return ghostEyesCount;
    }

    public void incrementGhostEyesCount() {
        ghostEyesCount++;
    }

    public void decrementGhostEyesCount() {
        ghostEyesCount--;
    }

    public boolean isTilesChanged() {
        return tilesChanged;
    }

    public int getCutsceneId() {
        return cutsceneController.getCutsceneId();
    }

    void setCutsceneId(int cutsceneId) {
        cutsceneController.setCutsceneId(cutsceneId);
    }

    public int getCutsceneSequenceId() {
        return cutsceneController.getCutsceneSequenceId();
    }

    public double getCutsceneTime() {
        return cutsceneController.getCutsceneTime();
    }

    public void setTilesChanged(boolean tilesChanged) {
        this.tilesChanged = tilesChanged;
    }

    public double getCruiseElroySpeed() {
        return cruiseElroySpeed;
    }
    
    public static double getFieldX(double x) {
        return x + -32;
    }

    public static double getFieldY(double y) {
        return y + 0;
    }
}
