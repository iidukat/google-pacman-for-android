package jp.or.iidukat.example.pacman.entity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import jp.or.iidukat.example.pacman.Direction;
import jp.or.iidukat.example.pacman.PacmanGame;
import android.graphics.Bitmap;

public class Clyde extends Ghost {

    private static final InitPosition INIT_POS =
        InitPosition.createGhostInitPosition(41.375f, 7, Direction.UP, 0, 20);

    // movements of Clyde in the pen
    private static final Map<GhostMode, MoveInPen[]> MOVES_IN_PEN;
    static {
        Map<GhostMode, MoveInPen[]> m =
            new EnumMap<GhostMode, MoveInPen[]>(GhostMode.class);
        m.put(
            GhostMode.IN_PEN,
            new MoveInPen[] {
                new MoveInPen(41.4f, 7, Direction.UP, 6.375f, 0.48f),
                new MoveInPen(41.4f, 6.375f, Direction.DOWN, 7.625f, 0.48f),
                new MoveInPen(41.4f, 7.625f, Direction.UP, 7, 0.48f),
              });
        m.put(
            GhostMode.LEAVING_PEN,
            new MoveInPen[] {
                new MoveInPen(41.4f, 7, Direction.LEFT, 39.5f, LEAVING_PEN_SPEED),
                new MoveInPen(39.5f, 7, Direction.UP, 4, LEAVING_PEN_SPEED),
            });
        m.put(
            GhostMode.ENTERING_PEN,
            new MoveInPen[] {
                new MoveInPen(39.5f, 4, Direction.DOWN, 7, 1.6f),
                new MoveInPen(39.5f, 7, Direction.RIGHT, 41.375f, 1.6f),
            });
        m.put(
            GhostMode.RE_LEAVING_FROM_PEN,
            new MoveInPen[] {
                new MoveInPen(41.4f, 7, Direction.LEFT, 39.5f, LEAVING_PEN_SPEED),
                new MoveInPen(39.5f, 7, Direction.UP, 4, LEAVING_PEN_SPEED),
            });
        MOVES_IN_PEN = Collections.unmodifiableMap(m);
    }

    Clyde(Bitmap sourceImage, PacmanGame game) {
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
        // If near the player, trace it.
        // If distant from the player,  go to the location assigned in the scatter mode.
        PlayfieldActor pacman = game.getPacman();
        double distance = getDistance(pacman.tilePos, this.tilePos);
        this.targetPos =
            distance > 64
                ? new double[] { pacman.tilePos[0], pacman.tilePos[1] }
                : this.scatterPos;
    }

    @Override
    void onGhostModeSwitched(GhostMode oldMode, GhostMode newMode) {
        // Clyde entering or leaving the pen affects Blinky's cruise elroy speed.
        if (newMode == GhostMode.IN_PEN || oldMode == GhostMode.IN_PEN) {
            game.updateCruiseElroySpeed();
        }
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
        return 7;
    }
}
