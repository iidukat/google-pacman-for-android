package jp.or.iidukat.example.pacman.entity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import jp.or.iidukat.example.pacman.Direction;
import jp.or.iidukat.example.pacman.Direction.Move;
import jp.or.iidukat.example.pacman.PacmanGame;
import android.graphics.Bitmap;

public class Inky extends Ghost {

    private static final InitPosition INIT_POS =
        InitPosition.createGhostInitPosition(37.625f, 7, Direction.UP, 57, 20);

    // movements of Inky in the pen
    private static final Map<GhostMode, MoveInPen[]> MOVES_IN_PEN;
    static {
        Map<GhostMode, MoveInPen[]> m =
            new EnumMap<GhostMode, MoveInPen[]>(GhostMode.class);
        m.put(
            GhostMode.IN_PEN,
            new MoveInPen[] {
                new MoveInPen(37.6f, 7, Direction.UP, 6.375f, 0.48f),
                new MoveInPen(37.6f, 6.375f, Direction.DOWN, 7.625f, 0.48f),
                new MoveInPen(37.6f, 7.625f, Direction.UP, 7, 0.48f),
            });            
        m.put(
            GhostMode.LEAVING_PEN,
            new MoveInPen[] {
                new MoveInPen(37.6f, 7, Direction.RIGHT, 39.5f, LEAVING_PEN_SPEED),
                new MoveInPen(39.5f, 7, Direction.UP, 4, LEAVING_PEN_SPEED),
            });            
        m.put(
            GhostMode.ENTERING_PEN,
            new MoveInPen[] {
                new MoveInPen(39.5f, 4, Direction.DOWN, 7, 1.6f),
                new MoveInPen(39.5f, 7, Direction.LEFT, 37.625f, 1.6f),
            });            
        m.put(
            GhostMode.RE_LEAVING_FROM_PEN,
            new MoveInPen[] {
                new MoveInPen(37.6f, 7, Direction.RIGHT, 39.5f, LEAVING_PEN_SPEED),
                new MoveInPen(39.5f, 7, Direction.UP, 4, LEAVING_PEN_SPEED),
            });            
        MOVES_IN_PEN = Collections.unmodifiableMap(m);
    }

    Inky(Bitmap sourceImage, PacmanGame game) {
        super(sourceImage, game);
    }
    
    @Override
    InitPosition getInitPosition() {
        return INIT_POS;
    }
    
    @Override
    public void updateTargetPos() {
        if (this.mode != GhostMode.CHASE) {
            return;
        }
        
        // the player are attacked on both sides by Inky and Blinky
        PlayfieldActor pacman = game.getPacman();
        Move pacmanMove = pacman.dir.getMove();
        PlayfieldActor blinky = game.getBlinky();
        double[] pacmanDest = new double[] { pacman.tilePos[0], pacman.tilePos[1] };
        pacmanDest[pacmanMove.getAxis()] += 16 * pacmanMove.getIncrement();
        if (pacman.dir == Direction.UP) {
            pacmanDest[1] -= 16;
        }
        // The in-place update mirrors the original JS (pacman10-hp.js): when
        // targetPos still aliases scatterPos (assigned on switching to SCATTER),
        // this write also mutates scatterPos. Keep it to stay faithful to the
        // original doodle's behavior.
        this.targetPos[0] = pacmanDest[0] * 2 - blinky.tilePos[0];
        this.targetPos[1] = pacmanDest[1] * 2 - blinky.tilePos[1];
    }
    
    @Override
    MoveInPen[] getMovesInPen() {
        if (MOVES_IN_PEN.containsKey(mode)) {
            return MOVES_IN_PEN.get(mode);
        } else {
            return new MoveInPen[0];
        }
    }
    
    @Override
    int getOrdinaryImageRow() {
        return 6;
    }
}
