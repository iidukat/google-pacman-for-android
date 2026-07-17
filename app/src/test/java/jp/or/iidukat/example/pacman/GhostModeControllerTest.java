package jp.or.iidukat.example.pacman;

import org.junit.Before;
import org.junit.Test;

import jp.or.iidukat.example.pacman.entity.Ghost;
import jp.or.iidukat.example.pacman.entity.Ghost.GhostMode;
import jp.or.iidukat.example.pacman.entity.Pacman;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class GhostModeControllerTest {

    private PacmanGame game;
    private LevelConfig levelConfig;
    private GameTimerManager timerManager;
    private Pacman pacman;
    private Ghost blinky;
    private Ghost pinky;
    private Ghost inky;
    private Ghost clyde;
    private GhostModeController controller;

    @Before
    public void setUp() {
        game = mock(PacmanGame.class);
        levelConfig = mock(LevelConfig.class);
        timerManager = mock(GameTimerManager.class);
        pacman = mock(Pacman.class);
        blinky = mock(Ghost.class);
        pinky = mock(Ghost.class);
        inky = mock(Ghost.class);
        clyde = mock(Ghost.class);
        when(game.getLevelConfig()).thenReturn(levelConfig);
        when(game.getGameTimerManager()).thenReturn(timerManager);
        when(game.getPacman()).thenReturn(pacman);
        when(game.getGhosts()).thenReturn(new Ghost[] { blinky, pinky, inky, clyde });
        when(game.getPinky()).thenReturn(pinky);
        when(game.getInky()).thenReturn(inky);
        when(game.getClyde()).thenReturn(clyde);
        controller = new GhostModeController(game);
    }

    // -----------------------------------------------------------------------
    // switchMainGhostMode
    // -----------------------------------------------------------------------

    @Test
    public void switchMainGhostMode_frightTimeZero_onlyReversesGhosts() {
        when(levelConfig.getFrightTime()).thenReturn(0);

        controller.switchMainGhostMode(GhostMode.FRIGHTENED, false);

        verify(blinky).setReverseDirectionsNext(true);
        verify(pinky).setReverseDirectionsNext(true);
        verify(inky).setReverseDirectionsNext(true);
        verify(clyde).setReverseDirectionsNext(true);
        verify(blinky, never()).followMainGhostMode(any(), anyBoolean());
        assertNull(controller.getMainGhostMode());
        verifyNoInteractions(pacman);
    }

    @Test
    public void switchMainGhostMode_delegatesToEachGhost() {
        controller.switchMainGhostMode(GhostMode.SCATTER, true);

        verify(blinky).followMainGhostMode(GhostMode.SCATTER, true);
        verify(pinky).followMainGhostMode(GhostMode.SCATTER, true);
        verify(inky).followMainGhostMode(GhostMode.SCATTER, true);
        verify(clyde).followMainGhostMode(GhostMode.SCATTER, true);
        assertEquals(GhostMode.SCATTER, controller.getMainGhostMode());
    }

    @Test
    public void switchMainGhostMode_updatesPacmanSpeeds() {
        when(levelConfig.getPlayerSpeed()).thenReturn(1.0);
        when(levelConfig.getDotEatingSpeed()).thenReturn(0.9);

        controller.switchMainGhostMode(GhostMode.CHASE, false);

        verify(pacman).setFullSpeed(1.0 * 0.8f);
        verify(pacman).setDotEatingSpeed(0.9 * 0.8f);
        verify(pacman).setTunnelSpeed(1.0 * 0.8f);
        verify(pacman).changeSpeed();
    }

    @Test
    public void switchMainGhostMode_frightened_savesLastMainModeAndStartsFrightTimer() {
        when(levelConfig.getFrightTime()).thenReturn(20);
        when(levelConfig.getFrightTotalTime()).thenReturn(30);
        when(levelConfig.getPlayerFrightSpeed()).thenReturn(0.95);
        when(levelConfig.getDotEatingFrightSpeed()).thenReturn(0.85);
        controller.mainGhostMode = GhostMode.SCATTER;

        controller.switchMainGhostMode(GhostMode.FRIGHTENED, false);

        assertEquals(GhostMode.SCATTER, controller.getLastMainGhostMode());
        assertEquals(GhostMode.FRIGHTENED, controller.getMainGhostMode());
        verify(timerManager).setFrightModeTime(30);
        verify(game).resetModeScoreMultiplier();
        verify(pacman).setFullSpeed(0.95 * 0.8f);
        verify(pacman).setDotEatingSpeed(0.85 * 0.8f);
    }

    @Test
    public void switchMainGhostMode_frightenedTwice_keepsOriginalLastMainMode() {
        when(levelConfig.getFrightTime()).thenReturn(20);
        controller.mainGhostMode = GhostMode.CHASE;

        controller.switchMainGhostMode(GhostMode.FRIGHTENED, false);
        controller.switchMainGhostMode(GhostMode.FRIGHTENED, false);

        assertEquals(GhostMode.CHASE, controller.getLastMainGhostMode());
    }

    @Test
    public void switchMainGhostMode_playsAmbientSound_onEnteringFright() {
        when(levelConfig.getFrightTime()).thenReturn(20);
        controller.mainGhostMode = GhostMode.SCATTER;

        controller.switchMainGhostMode(GhostMode.FRIGHTENED, false);

        verify(game).playAmbientSound();
    }

    @Test
    public void switchMainGhostMode_playsAmbientSound_onLeavingFright() {
        controller.mainGhostMode = GhostMode.FRIGHTENED;

        controller.switchMainGhostMode(GhostMode.CHASE, false);

        verify(game).playAmbientSound();
    }

    @Test
    public void switchMainGhostMode_noAmbientSound_betweenScatterAndChase() {
        controller.mainGhostMode = GhostMode.SCATTER;

        controller.switchMainGhostMode(GhostMode.CHASE, false);

        verify(game, never()).playAmbientSound();
    }

    // -----------------------------------------------------------------------
    // finishFrightMode
    // -----------------------------------------------------------------------

    @Test
    public void finishFrightMode_returnsToLastMainMode() {
        when(levelConfig.getFrightTime()).thenReturn(20);
        controller.mainGhostMode = GhostMode.SCATTER;
        controller.switchMainGhostMode(GhostMode.FRIGHTENED, false);

        controller.finishFrightMode();

        assertEquals(GhostMode.SCATTER, controller.getMainGhostMode());
        verify(blinky).followMainGhostMode(GhostMode.SCATTER, false);
    }

    // -----------------------------------------------------------------------
    // figureOutPenLeaving — personal dot counters (normal scheme)
    // -----------------------------------------------------------------------

    @Test
    public void figureOutPenLeaving_countsDotForFirstWaitingGhost_inPriorityOrder() {
        when(levelConfig.getPenLeavingLimits()).thenReturn(new int[] { 0, 7, 17, 32 });
        when(pinky.isWaitingInPen()).thenReturn(true);
        when(inky.isWaitingInPen()).thenReturn(true);
        when(clyde.isWaitingInPen()).thenReturn(true);

        controller.figureOutPenLeaving();

        verify(pinky).countDotForPenLeaving(7);
        verify(inky, never()).countDotForPenLeaving(anyInt());
        verify(clyde, never()).countDotForPenLeaving(anyInt());
    }

    @Test
    public void figureOutPenLeaving_countsForInky_whenPinkyIsOut() {
        when(levelConfig.getPenLeavingLimits()).thenReturn(new int[] { 0, 7, 17, 32 });
        when(inky.isWaitingInPen()).thenReturn(true);

        controller.figureOutPenLeaving();

        verify(pinky, never()).countDotForPenLeaving(anyInt());
        verify(inky).countDotForPenLeaving(17);
    }

    @Test
    public void figureOutPenLeaving_countsForClyde_whenOthersAreOut() {
        when(levelConfig.getPenLeavingLimits()).thenReturn(new int[] { 0, 7, 17, 32 });
        when(clyde.isWaitingInPen()).thenReturn(true);

        controller.figureOutPenLeaving();

        verify(clyde).countDotForPenLeaving(32);
    }

    @Test
    public void figureOutPenLeaving_noop_whenNoGhostWaits() {
        controller.figureOutPenLeaving();

        verify(pinky, never()).countDotForPenLeaving(anyInt());
        verify(inky, never()).countDotForPenLeaving(anyInt());
        verify(clyde, never()).countDotForPenLeaving(anyInt());
    }

    // -----------------------------------------------------------------------
    // figureOutPenLeaving — alternate (global) scheme after a life loss
    // -----------------------------------------------------------------------

    private void eatDots(int count) {
        for (int i = 0; i < count; i++) {
            controller.figureOutPenLeaving();
        }
    }

    @Test
    public void alternateScheme_freesPinkyAtSeventhDot() {
        controller.restartPenLeavingForNewLife();

        eatDots(6);
        verify(pinky, never()).setFreeToLeavePen(true);

        eatDots(1);
        verify(pinky).setFreeToLeavePen(true);
        verify(inky, never()).setFreeToLeavePen(true);
    }

    @Test
    public void alternateScheme_freesInkyAt17thDot() {
        controller.restartPenLeavingForNewLife();

        eatDots(17);

        verify(inky).setFreeToLeavePen(true);
        verify(clyde, never()).setFreeToLeavePen(true);
    }

    @Test
    public void alternateScheme_neverCountsPersonalDots() {
        controller.restartPenLeavingForNewLife();
        when(pinky.isWaitingInPen()).thenReturn(true);

        eatDots(10);

        verify(pinky, never()).countDotForPenLeaving(anyInt());
    }

    @Test
    public void alternateScheme_deactivates_whenClydeStillPennedAt32ndDot() {
        when(levelConfig.getPenLeavingLimits()).thenReturn(new int[] { 0, 7, 17, 32 });
        controller.restartPenLeavingForNewLife();
        when(clyde.getMode()).thenReturn(GhostMode.IN_PEN);
        eatDots(32);
        verify(clyde, never()).setFreeToLeavePen(true);

        // the 33rd dot goes to the personal counters
        when(pinky.isWaitingInPen()).thenReturn(true);
        controller.figureOutPenLeaving();

        verify(pinky).countDotForPenLeaving(7);
    }

    @Test
    public void alternateScheme_staysActive_whenClydeAlreadyOutAt32ndDot() {
        controller.restartPenLeavingForNewLife();
        when(clyde.getMode()).thenReturn(GhostMode.CHASE);
        eatDots(32);

        when(pinky.isWaitingInPen()).thenReturn(true);
        controller.figureOutPenLeaving();

        verify(pinky, never()).countDotForPenLeaving(anyInt());
    }

    @Test
    public void restartPenLeavingForNewLevel_disablesAlternateScheme() {
        when(levelConfig.getPenLeavingLimits()).thenReturn(new int[] { 0, 7, 17, 32 });
        controller.restartPenLeavingForNewLife();

        controller.restartPenLeavingForNewLevel();
        when(pinky.isWaitingInPen()).thenReturn(true);
        controller.figureOutPenLeaving();

        verify(pinky).countDotForPenLeaving(7);
    }
}