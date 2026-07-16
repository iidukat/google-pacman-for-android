package jp.or.iidukat.example.pacman.entity;

import jp.or.iidukat.example.pacman.Direction;
import jp.or.iidukat.example.pacman.Direction.Move;
import jp.or.iidukat.example.pacman.PacmanGame;
import jp.or.iidukat.example.pacman.PacmanGame.GameplayMode;
import jp.or.iidukat.example.pacman.entity.Playfield.PathElement;
import android.graphics.Bitmap;

public abstract class Ghost extends PlayfieldActor {

    public static enum GhostMode {
        NONE(0), CHASE(1), SCATTER(2), FRIGHTENED(4), 
        EATEN(8), IN_PEN(16), LEAVING_PEN(32),
        ENTERING_PEN(64), RE_LEAVING_FROM_PEN(128);
        
        private final int mode;
        
        private GhostMode(int mode) {
            this.mode = mode;
        }

        public int getMode() {
            return mode;
        }
    }
    
    static final double LEAVING_PEN_SPEED = 0.8f * 0.4f;
    
    static class MoveInPen {
        final double x;
        final double y;
        final Direction dir;
        final double dest;
        final double speed;
        MoveInPen(double x, double y, Direction dir, double dest, double speed) {
            this.x = x;
            this.y = y;
            this.dir = dir;
            this.dest = dest;
            this.speed = speed;
        }
    }

    GhostMode mode = GhostMode.NONE;
    double[] targetPos;
    double[] scatterPos;
    private boolean followingRoutine;
    private boolean proceedToNextRoutineMove;
    private int routineMoveId;
    private boolean freeToLeavePen;
    private boolean modeChangedWhileInPen;
    private boolean eatenInThisFrightMode;
    private boolean reverseDirectionsNext;
    private int dotCount;

    Ghost(Bitmap sourceImage, PacmanGame game) {
        super(sourceImage, game);
    }

    @Override
    public final void arrange() {
        InitPosition p = getInitPosition();
        this.pos = new double[] {p.y * 8, p.x * 8};
        this.tilePos = new int[] {(int) p.y * 8, (int) p.x * 8};
        this.targetPos = new double[] {p.scatterY * 8, p.scatterX * 8};
        this.scatterPos = new double[] {p.scatterY * 8, p.scatterX * 8};
        this.lastActiveDir = this.dir = p.dir;
        this.physicalSpeed = 0;
        this.nextDir = Direction.NONE;
        this.changeSpeed(CurrentSpeed.NORMAL);
        this.reverseDirectionsNext = this.freeToLeavePen = this.modeChangedWhileInPen = this.eatenInThisFrightMode = false;
    }

    public abstract void updateTargetPos();

    public final void switchGhostMode(GhostMode mode) {
        GhostMode oldMode = this.mode;
        this.mode = mode;
        onGhostModeSwitched(oldMode, mode);
        switch (oldMode) {
        case LEAVING_PEN:
            game.setGhostExitingPenNow(false);
            break;
        case EATEN:
            if (game.getGhostEyesCount() > 0) game.decrementGhostEyesCount();
            if (game.getGhostEyesCount() == 0) game.playAmbientSound();
            break;
        }
        switch (mode) {
        case FRIGHTENED:
            this.fullSpeed = game.getLevelConfig().getGhostFrightSpeed() * 0.8f;
            this.tunnelSpeed = game.getLevelConfig().getGhostTunnelSpeed() * 0.8f;
            this.followingRoutine = false;
            break;
        case CHASE:
            this.fullSpeed = game.getLevelConfig().getGhostSpeed() * 0.8f;
            this.tunnelSpeed = game.getLevelConfig().getGhostTunnelSpeed() * 0.8f;
            this.followingRoutine = false;
            break;
        case SCATTER:
            this.targetPos = this.scatterPos;
            this.fullSpeed = game.getLevelConfig().getGhostSpeed() * 0.8f;
            this.tunnelSpeed = game.getLevelConfig().getGhostTunnelSpeed() * 0.8f;
            this.followingRoutine = false;
            break;
        case EATEN:
            this.tunnelSpeed = this.fullSpeed = 1.6f;
            this.targetPos =
                    new double[] {
                        Playfield.PEN_ENTRANCE[0],
                        Playfield.PEN_ENTRANCE[1]
                    };
            this.freeToLeavePen = this.followingRoutine = false;
            break;
        case LEAVING_PEN:
            this.followingRoutine = true;
            this.routineMoveId = -1;
            game.setGhostExitingPenNow(true);
            break;
        case IN_PEN:
        case ENTERING_PEN:
        case RE_LEAVING_FROM_PEN:
            this.followingRoutine = true;
            this.routineMoveId = -1;
            break;
        }
        this.changeSpeed();
    }
    
    // react to a change of the main ghost mode
    public final void onMainGhostModeChanged(GhostMode ghostMode, boolean justRestartGame) {
        if (ghostMode != GhostMode.ENTERING_PEN && !justRestartGame) {
            this.modeChangedWhileInPen = true;
        }
        if (ghostMode == GhostMode.FRIGHTENED) {
            this.eatenInThisFrightMode = false;
        }
        if (!isEatenOrInPenCycle() || justRestartGame) {
            // If it is not immediately after restart the game (justRestartGame:false),
            // a ghost reverses its direction
            // when its mode changes from other than FRIGHTENED (CHASE or SCATTER) to another mode.
            if (!justRestartGame && this.mode != GhostMode.FRIGHTENED
                    && this.mode != ghostMode) {
                this.reverseDirectionsNext = true;
            }

            // If it is not immediately after restart the game
            // and a mode of the ghost is any of EATEN, IN_PEN, LEAVING_PEN, RE_LEAVING_FROM_PEN, or ENTERING_PEN,
            // it is not updated.
            switchGhostMode(ghostMode);
        }
    }

    // hook invoked whenever the ghost mode has switched (see Clyde)
    void onGhostModeSwitched(GhostMode oldMode, GhostMode newMode) {
    }

    // whether the ghost goes out of the pen again soon after entering it as eaten (see Blinky)
    boolean reLeavesPenImmediately() {
        return false;
    }

    // whether the ghost is eaten (returning to the pen as eyes)
    // or somewhere in the pen cycle (in the pen, entering it, or leaving it)
    public final boolean isEatenOrInPenCycle() {
        return mode == GhostMode.EATEN
                || mode == GhostMode.IN_PEN
                || mode == GhostMode.LEAVING_PEN
                || mode == GhostMode.RE_LEAVING_FROM_PEN
                || mode == GhostMode.ENTERING_PEN;
    }

    @Override
    public final void changeSpeed() {
        double s = 0;
        switch (this.currentSpeed) {
        case NORMAL:
            s = getNormalSpeed();
            break;
        case PASSING_TUNNEL:
            s = this.tunnelSpeed;
            break;
        }
        if (this.physicalSpeed != s) {
          this.physicalSpeed = s;
          this.speedIntervals = game.getSpeedIntervals(this.physicalSpeed);
        }
    }
    
    double getNormalSpeed() {
        return fullSpeed;
    }    

    private void decideNextDir(boolean reversed) {
        int[] currentTilePos = tilePos;
        Move currentMove = dir.getMove();
        int[] newTilePos = new int[] {currentTilePos[0], currentTilePos[1]};
        newTilePos[currentMove.getAxis()] += currentMove.getIncrement() * 8; // anticipate the next tile by the direction of the progress
        PathElement destination = game.getPathElement(newTilePos[1], newTilePos[0]);
        if (reversed && !destination.isIntersection()) {
            // when the ghost has already reversed its direction and its destination is neither a dead end nor a intersection,
            // it return to the current position.
            destination = game.getPathElement(currentTilePos[1], currentTilePos[0]);
        }

        // The destination of the ghost is a dead end or a intersection.
        if (destination.isIntersection()) {
            switch (mode) {
            case SCATTER:
            case CHASE:
            case EATEN:
                if (destination.allowOnlyOpposite(dir)) {
                    // If the opposite direction is available and other directions is not available,
                    // choose the opposite direction.
                    nextDir = dir.getOpposite();
                } else {
                    // If the ghost can choose some directions except the opposite,
                    // the ghost choose the direction in which it is the closest to its target position.
                    double max = 99999999999f;
                    double distance = 0;
                    Direction dirCandidate = Direction.NONE;
                    for (Direction d : Direction.getAllMoves()) {
                        if (destination.allow(d) && dir != d.getOpposite()) {
                            double[] tilePosCandidate =
                                new double[] {(double) newTilePos[0], (double) newTilePos[1]};
                            tilePosCandidate[d.getMove().getAxis()] += d.getMove().getIncrement();
                            distance = getDistance(
                                            tilePosCandidate,
                                            new double[] {targetPos[0], targetPos[1]});
                            if (distance < max) {
                                max = distance;
                                dirCandidate = d;
                            }
                        }
                    }
                    if (dirCandidate != Direction.NONE) {
                        nextDir = dirCandidate;
                    }
                }
                break;
            case FRIGHTENED:
                if (destination.allowOnlyOpposite(dir)) {
                    // If the opposite direction is available and other directions is not available,
                    // choose the opposite direction.
                    this.nextDir = dir.getOpposite();
                } else {
                    // If the ghost can choose some directions except the opposite,
                    // the ghost randomly choose one of them.
                    Direction nDir = Direction.NONE;
                    do {
                        nDir =
                            Direction.getAllMoves().get(
                                (int) Math.floor(
                                        game.rand() * Direction.getAllMoves().size()));
                    } while (!destination.allow(nDir)
                                || nDir == dir.getOpposite());
                    nextDir = nDir;
                }
                break;
          }
        }
    }

    // when the ghost stays in the pen or goes out of the pen, manage its behaviors
    private void switchFollowingRoutine() {
        this.routineMoveId++;
        if (this.routineMoveId == getMovesInPen().length) { // the end of the routine
            if (this.mode == GhostMode.IN_PEN && this.freeToLeavePen
                    && !game.isGhostExitingPenNow()) { // the conditions of going out are met
                if (this.eatenInThisFrightMode) {
                    this.switchGhostMode(GhostMode.RE_LEAVING_FROM_PEN);
                } else {
                    this.switchGhostMode(GhostMode.LEAVING_PEN);
                }
                return;
            } else if (this.mode == GhostMode.LEAVING_PEN
                        || this.mode == GhostMode.RE_LEAVING_FROM_PEN) { // go out of the pen right now
                this.pos =
                    new double[] {
                        Playfield.PEN_ENTRANCE[0],
                        Playfield.PEN_ENTRANCE[1] + 4
                    };
                this.dir = this.modeChangedWhileInPen ? Direction.RIGHT : Direction.LEFT;
                GhostMode mainMode = game.getMainGhostMode();
                if (this.mode == GhostMode.RE_LEAVING_FROM_PEN
                        && mainMode == GhostMode.FRIGHTENED) {
                    mainMode = game.getLastMainGhostMode();
                }
                this.switchGhostMode(mainMode);
                return;
            } else if (this.mode == GhostMode.ENTERING_PEN) { // After the ghost has been eaten, it enters the pen.
                if (this.reLeavesPenImmediately() || this.freeToLeavePen) {
                    this.switchGhostMode(GhostMode.RE_LEAVING_FROM_PEN);
                } else {
                    this.eatenInThisFrightMode = true;
                    this.switchGhostMode(GhostMode.IN_PEN);
                }
                return;
            } else { // If the conditions of going out aren't met, repeat the routines.
                this.routineMoveId = 0;
            }
        }

        MoveInPen mv = getMovesInPen()[this.routineMoveId];
        this.pos[0] = mv.y * 8;
        this.pos[1] = mv.x * 8;
        this.dir = mv.dir;
        this.physicalSpeed = 0;
        this.speedIntervals = game.getSpeedIntervals(mv.speed);
        this.proceedToNextRoutineMove = false;
        this.updateAppearance();
    }
    
    // When the ghost stays in the pen or goes out of the pen, manage its behaviors
    // (switch display image and update the display position)
    private void continueFollowingRoutine() {
        
        MoveInPen mv = null;
        MoveInPen[] mvs = getMovesInPen();
        
        if (0 <= this.routineMoveId && this.routineMoveId < mvs.length) {
            mv = mvs[this.routineMoveId];
        }
        
        if (mv != null) {
            if (this.speedIntervals[game.getIntervalTime()]) {
                Move m = this.dir.getMove();
                this.pos[m.getAxis()] += m.getIncrement();
                switch (this.dir) {
                case UP:
                case LEFT:
                    if (this.pos[m.getAxis()] < mv.dest * 8) {
                        this.pos[m.getAxis()] = mv.dest * 8;
                        this.proceedToNextRoutineMove = true;
                    }
                    break;
                case DOWN:
                case RIGHT:
                    if (this.pos[m.getAxis()] > mv.dest * 8) {
                        this.pos[m.getAxis()] = mv.dest * 8;
                        this.proceedToNextRoutineMove = true;
                    }
                    break;
                }
                this.updateAppearance();
            }
        }
    }
    
    abstract MoveInPen[] getMovesInPen();
    
    // When the ghost stays in the pen or goes out of the pen, manage its behaviors
    private void followRoutine() {
        if (this.routineMoveId == -1 || this.proceedToNextRoutineMove) {
            this.switchFollowingRoutine();
        }
        
        this.continueFollowingRoutine();
    }

    @Override
    final void handleAnObjectWhenEncountering() {
        // enter into the pen
        if (this.mode == GhostMode.EATEN
                && this.pos[0] == Playfield.PEN_ENTRANCE[0]
                && this.pos[1] == Playfield.PEN_ENTRANCE[1]) {
            this.switchGhostMode(GhostMode.ENTERING_PEN);
        }
    }

    @Override
    final boolean supportShortcut() {
        return false;
    }
    
    @Override
    final void prepareShortcut() {
    }
    
    @Override
    final void adjustPosOnEnteringTile(int[] tilePos) {
    }
    
    @Override
    final void reverseOnEnteringTile() {
        if (this.reverseDirectionsNext) { // reverse its direction
            this.dir = this.dir.getOpposite();
            this.nextDir = Direction.NONE;
            this.reverseDirectionsNext = false;
            this.decideNextDir(true);
        }
    }
    
    @Override
    final boolean canChangeSpeedInTunnel() {
        return this.mode != GhostMode.EATEN;
    }
    
    @Override
    final void encounterDot(int[] tilePos) {
    }
    
    @Override
    final void decideNextDirOnEnteredTile() {
        decideNextDir(false);
    }
    
    @Override
    final void shortcutCorner() {
    }
    
    // determine the display image of the ghost
    @Override
    final int[] getImagePos() {
        int x = 0;
        int y = 0;
        if (game.getGameplayMode() == GameplayMode.LEVEL_COMPLETED
                || game.getGameplayMode() == GameplayMode.NEWGAME_STARTING
                || game.getGameplayMode() == GameplayMode.PLAYER_DIED) {
            // Immediately after the player dies, the ghost disappears.
            x = 3;
            y = 0;
        } else if (game.getGameplayMode() == GameplayMode.GHOST_DIED
                        && this== game.getGhostBeingEaten()) {
            // Determine the score to be displayed when the ghost has been eaten
            switch (game.getModeScoreMultiplier()) {
            case 2:
                x = 0;
                break;
            case 4:
                x = 1;
                break;
            case 8:
                x = 2;
                break;
            case 16:
                x = 3;
                break;
            }
            y = 11;
            getAppearance().setOrder(111);
        } else if (this.mode == GhostMode.FRIGHTENED
                      || (this.mode == GhostMode.IN_PEN || this.mode == GhostMode.LEAVING_PEN)
                          && game.getMainGhostMode() == GhostMode.FRIGHTENED
                          && !this.eatenInThisFrightMode) {
            // in the frighten mode, however, not eaten.
            x = 0;
            y = 8;
            // blinking before the end of the frighten mode
            if (game.getFrightModeTime() < game.getLevelConfig().getFrightTotalTime() - game.getLevelConfig().getFrightTime()
                    && Math.floor(game.getFrightModeTime() / game.getFrightBlinkTiming()) % 2 == 0) {
                x += 2;
            }
    
            x += (int) (Math.floor(game.getGlobalTime() / 16) % 2); // switch display image in frighten mode.
        } else if (this.mode == GhostMode.EATEN || this.mode == GhostMode.ENTERING_PEN) { // eyes only
            Direction ndir = this.nextDir;
            if (ndir != Direction.NONE) {
                ndir = this.dir;
            }
            switch (ndir) {
            case LEFT:
                x = 2;
                break;
            case RIGHT:
                x = 3;
                break;
            case UP:
                x = 0;
                break;
            case DOWN:
                x = 1;
                break;
            }
            y = 10;
        } else { // display the image in the ordinary way.
            Direction ndir = this.nextDir;
            if (ndir == Direction.NONE
                || game.getPathElement(this.tilePos[1], this.tilePos[0]).isTunnel()) {
                ndir = this.dir;
            }
            
            switch (ndir) {
            case LEFT:
                x = 4;
                break;
            case RIGHT:
                x = 6;
                break;
            case UP:
                x = 0;
                break;
            case DOWN:
                x = 2;
                break;
            }
            y = getOrdinaryImageRow();
            if (game.getGameplayMode() != GameplayMode.CUTSCENE) {
                x += (int) (Math.floor(game.getGlobalTime() / 16) % 2);
            }
        }
        return new int[] { y, x };
    }
    
    abstract int getOrdinaryImageRow();

    @Override
    public final void move() {
        if (game.getGameplayMode() == GameplayMode.ORDINARY_PLAYING
                || game.getGameplayMode() == GameplayMode.GHOST_DIED
                    && (this.mode == GhostMode.EATEN
                            || this.mode == GhostMode.ENTERING_PEN)) {
            if (this.followingRoutine) {
                this.followRoutine();
                if (this.mode == GhostMode.ENTERING_PEN) this.followRoutine();
            } else {
                this.step();
                if (this.mode == GhostMode.EATEN) this.step();
            }
        }
    }

    @Override
    public final double getFieldX() {
        return PacmanGame.getFieldX(pos[1]);
    }
    
    @Override
    public final double getFieldY() {
        return PacmanGame.getFieldY(pos[0]);
    }
    
    public final GhostMode getMode() {
        return mode;
    }

    public final void setReverseDirectionsNext(boolean reverseDirectionsNext) {
        this.reverseDirectionsNext = reverseDirectionsNext;
    }

    public final boolean isEatenInThisFrightMode() {
        return eatenInThisFrightMode;
    }

    public final void setFreeToLeavePen(boolean freeToLeavePen) {
        this.freeToLeavePen = freeToLeavePen;
    }

    // whether the ghost waits in the pen (or returns to it as eaten)
    // counting dots until it may leave
    public final boolean isWaitingInPen() {
        return mode == GhostMode.IN_PEN || mode == GhostMode.EATEN;
    }

    // count a dot toward the ghost's own pen-leaving limit;
    // the ghost becomes free to leave the pen once the limit is reached
    public final void countDotForPenLeaving(int limit) {
        dotCount++;
        if (dotCount >= limit) {
            freeToLeavePen = true;
        }
    }
    
    static double getDistance(int[] p1, int[] p2) {
        return Math.sqrt((p2[1] - p1[1]) * (p2[1] - p1[1]) + (p2[0] - p1[0]) * (p2[0] - p1[0]));
    }

    static double getDistance(double[] p1, double[] p2) {
        return Math.sqrt((p2[1] - p1[1]) * (p2[1] - p1[1]) + (p2[0] - p1[0]) * (p2[0] - p1[0]));
    }
    
}
