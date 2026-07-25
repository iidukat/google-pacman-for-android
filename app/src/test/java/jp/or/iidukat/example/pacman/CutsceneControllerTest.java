package jp.or.iidukat.example.pacman;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import jp.or.iidukat.example.pacman.entity.CutsceneActor;
import jp.or.iidukat.example.pacman.entity.CutsceneBlinky;
import jp.or.iidukat.example.pacman.entity.CutsceneField;
import jp.or.iidukat.example.pacman.entity.CutscenePacman;
import jp.or.iidukat.example.pacman.entity.PacmanCanvas;
import jp.or.iidukat.example.pacman.entity.Playfield;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CutsceneControllerTest {

    private PacmanGame game;
    private Playfield playfieldEl;
    private PacmanCanvas canvasEl;
    private CutsceneField cutsceneField;
    private GameTimerManager timerManager;
    private LevelConfig levelConfig;
    private SoundManager soundManager;
    private CutsceneActor actor1;
    private CutsceneActor actor2;
    private CutsceneController controller;

    @Before
    public void setUp() {
        game = mock(PacmanGame.class);
        playfieldEl = mock(Playfield.class);
        canvasEl = mock(PacmanCanvas.class);
        cutsceneField = mock(CutsceneField.class);
        timerManager = mock(GameTimerManager.class);
        levelConfig = mock(LevelConfig.class);
        soundManager = mock(SoundManager.class);
        actor1 = mock(CutsceneActor.class);
        actor2 = mock(CutsceneActor.class);

        when(game.getPlayfieldEl()).thenReturn(playfieldEl);
        when(game.getCanvasEl()).thenReturn(canvasEl);
        when(game.getGameTimerManager()).thenReturn(timerManager);
        when(game.getLevelConfig()).thenReturn(levelConfig);
        when(game.getSoundManager()).thenReturn(soundManager);
        when(canvasEl.getCutsceneField()).thenReturn(cutsceneField);
        when(cutsceneField.getActors()).thenReturn(new CutsceneActor[] { actor1, actor2 });
        when(levelConfig.getFrightTotalTime()).thenReturn(30);

        controller = new CutsceneController(game);
    }

    private void exhaustCurrentSequence() {
        while (controller.getCutsceneTime() > 0) {
            controller.advance();
        }
        controller.check();
    }

    // -----------------------------------------------------------------------
    // start
    // -----------------------------------------------------------------------

    @Test
    public void start_configuresVisualsAndSound() {
        controller.setCutsceneId(1);

        controller.start();

        verify(playfieldEl).setVisibility(false);
        verify(canvasEl).setVisibility(true);
        verify(canvasEl).showChrome(false);
        verify(canvasEl).createCutsceneField();
        verify(timerManager).setFrightModeTime(30);
        verify(soundManager).stopAll();
        verify(soundManager).playCutsceneAmbient();
    }

    @Test
    public void start_createsActorsForConfiguredCutscene() {
        controller.setCutsceneId(1);

        controller.start();

        ArgumentCaptor<Class[]> actorTypes = ArgumentCaptor.forClass(Class[].class);
        verify(cutsceneField).createActors(eq(game), eq(1), actorTypes.capture());
        assertArrayEquals(
            new Class<?>[] { CutscenePacman.class, CutsceneBlinky.class },
            actorTypes.getValue());
    }

    @Test
    public void start_beginsFirstSequence() {
        controller.setCutsceneId(1);

        controller.start();

        assertEquals(0, controller.getCutsceneSequenceId());
        assertEquals(5.5 * GameConstants.DEFAULT_FPS, controller.getCutsceneTime(), 0.0001);
        verify(actor1).setupSequence();
        verify(actor1).updateAppearance();
        verify(actor2).setupSequence();
        verify(actor2).updateAppearance();
    }

    // -----------------------------------------------------------------------
    // advance
    // -----------------------------------------------------------------------

    @Test
    public void advance_movesAllActorsAndDecrementsTime() {
        controller.setCutsceneId(1);
        controller.start();
        double timeBeforeAdvance = controller.getCutsceneTime();

        controller.advance();

        verify(actor1).move();
        verify(actor2).move();
        assertEquals(timeBeforeAdvance - 1, controller.getCutsceneTime(), 0.0001);
    }

    // -----------------------------------------------------------------------
    // check / sequence transitions
    // -----------------------------------------------------------------------

    @Test
    public void check_noop_whileSequenceTimeRemains() {
        controller.setCutsceneId(1);
        controller.start();

        controller.check();

        assertEquals(0, controller.getCutsceneSequenceId());
        verify(actor1, times(1)).setupSequence();
    }

    @Test
    public void check_advancesToNextSequence_whenTimeExpires() {
        controller.setCutsceneId(1);
        controller.start();

        exhaustCurrentSequence();

        assertEquals(1, controller.getCutsceneSequenceId());
        assertEquals(0.1 * GameConstants.DEFAULT_FPS, controller.getCutsceneTime(), 0.0001);
        verify(actor1, times(2)).setupSequence();
    }

    @Test
    public void check_stopsCutscene_whenLastSequenceFinishes() {
        controller.setCutsceneId(3);
        controller.start();

        exhaustCurrentSequence();
        exhaustCurrentSequence();

        verify(game).newLevel(false);
        verify(canvasEl).removeCutsceneField();
        verify(canvasEl).showChrome(true);
        verify(playfieldEl).setVisibility(true);
        verify(soundManager).stopCutsceneAmbient();
    }

    // -----------------------------------------------------------------------
    // stop
    // -----------------------------------------------------------------------

    @Test
    public void stop_normalFlow_startsNewLevel() {
        controller.stop();

        verify(game).newLevel(false);
        verify(soundManager).stopCutsceneAmbient();
        verify(playfieldEl).setVisibility(true);
        verify(canvasEl).removeCutsceneField();
        verify(canvasEl).showChrome(true);
    }

    @Test
    public void stop_debugMode_invokesCallbackInsteadOfNewLevel() {
        Runnable onFinished = mock(Runnable.class);
        controller.setDebugCutsceneId(1);
        controller.setOnFinished(onFinished);

        controller.stop();

        verify(onFinished).run();
        verify(game, never()).newLevel(anyBoolean());
    }

    @Test
    public void stop_debugMode_withoutCallback_doesNotThrow() {
        controller.setDebugCutsceneId(1);

        controller.stop();

        verify(game, never()).newLevel(anyBoolean());
    }

    // -----------------------------------------------------------------------
    // debug cutscene bookkeeping
    // -----------------------------------------------------------------------

    @Test
    public void hasDebugCutscene_falseByDefault() {
        assertFalse(controller.hasDebugCutscene());
    }

    @Test
    public void setDebugCutsceneId_marksDebugCutsceneAsPending() {
        controller.setDebugCutsceneId(2);

        assertTrue(controller.hasDebugCutscene());
    }

    @Test
    public void consumeDebugCutscene_movesIdIntoCutsceneIdAndClearsPendingFlag() {
        controller.setDebugCutsceneId(2);

        controller.consumeDebugCutscene();

        assertEquals(2, controller.getCutsceneId());
        assertFalse(controller.hasDebugCutscene());
    }
}