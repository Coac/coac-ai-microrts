package ai.coac;

import ai.abstraction.AbstractionLayerAIWait1;
import ai.abstraction.pathfinding.AStarPathFinding;
import ai.core.AI;
import ai.core.ParameterSpecification;
import rts.*;
import rts.units.Unit;
import rts.units.UnitType;
import rts.units.UnitTypeTable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author https://github.com/Coac
 */
public class CoacAI extends AbstractionLayerAIWait1 {
    protected UnitTypeTable utt;
    UnitType workerType;
    UnitType baseType;
    UnitType barracksType;
    UnitType rangedType;
    UnitType heavyType;
    UnitType lightType;
    AStarPathFinding astar;
    Player p;
    Player enemyPlayer;

    List<Unit> resources;
    List<Unit> myClosestResources;
    List<Unit> enemyBases;
    List<Unit> allBases;
    List<Unit> myBases;
    List<Unit> myWorkers;
    List<Unit> myWorkersBusy;
    List<Unit> enemies;
    List<Unit> myUnits;
    List<Unit> aliveEnemies;
    List<Unit> myCombatUnits;
    List<Unit> enemyCombatUnits;
    List<Unit> myCombatUnitsBusy;
    List<Unit> myBarracks;
    List<Unit> enemyBarracks;
    List<Unit> myWorkersCombat;
    String map;
    private GameState gs;
    private PhysicalGameState pgs;
    private int defenseBaseDistance = 2;

    Map<Long, List<Integer>> baseDefensePositions;
    Map<Long, List<Integer>> baseDefensePositionsRanged;
    private Map<Long, Integer> damages; // enemyID -> damage taken
    private Map<Long, Long> harvesting; // workerID -> baseID
    private HashMap<Long, Boolean> constructingBarracksForBase; // baseID -> constructing
    private HashMap<Long, Boolean> baseSeparated; // baseID -> separated

    private boolean debug = false;
    private int resourceUsed;
    private boolean attackAll = false;
    private boolean attackWithCombat = false;
    private int MAXCYCLES;
    private boolean wasSeparated = false;

    public CoacAI(UnitTypeTable a_utt) {
        this(a_utt, new AStarPathFinding());
    }


    public CoacAI(UnitTypeTable a_utt, AStarPathFinding a_pf) {
        super(a_pf);
        harvesting = new HashMap<>();
        astar = a_pf;
        reset(a_utt);
    }


    public void reset() {
        super.reset();
    }


    public void reset(UnitTypeTable a_utt) {
        utt = a_utt;
        workerType = utt.getUnitType("Worker");
        baseType = utt.getUnitType("Base");
        barracksType = utt.getUnitType("Barracks");
        rangedType = utt.getUnitType("Ranged");
        heavyType = utt.getUnitType("Heavy");
        lightType = utt.getUnitType("Light");
    }

    @Override
    public AI clone() {
        return new CoacAI(utt, astar);
    }

    private void init() {
        if (pgs.getWidth() <= 8) {
            defenseBaseDistance = 2;
        } else if (pgs.getWidth() <= 16) {
            defenseBaseDistance = 3;
        } else if (pgs.getWidth() <= 24) {
            defenseBaseDistance = 4;
        } else if (pgs.getWidth() <= 32) {
            defenseBaseDistance = 5;
        } else if (pgs.getWidth() <= 64) {
            defenseBaseDistance = 7;
        }

        MAXCYCLES = 12000;
        if (pgs.getWidth() <= 8) {
            MAXCYCLES = 3000;
        } else if (pgs.getWidth() <= 16) {
            MAXCYCLES = 4000;
        } else if (pgs.getWidth() <= 24) {
            MAXCYCLES = 5000;
        } else if (pgs.getWidth() <= 32) {
            MAXCYCLES = 6000;
        } else if (pgs.getWidth() <= 64) {
            MAXCYCLES = 8000;
        }

        resourceUsed = 0;
        myWorkersCombat = new ArrayList<>();
        resources = new ArrayList<>();
        allBases = new ArrayList<>();
        enemyBases = new ArrayList<>();
        myBases = new ArrayList<>();
        myWorkers = new ArrayList<>();
        myWorkersBusy = new ArrayList<>();
        enemyCombatUnits = new ArrayList<>();
        myCombatUnits = new ArrayList<>();
        myCombatUnitsBusy = new ArrayList<>();
        myBarracks = new ArrayList<>();
        enemyBarracks = new ArrayList<>();
        enemies = new ArrayList<>();
        myUnits = new ArrayList<>();
        aliveEnemies = new ArrayList<>();

        for (Unit u : pgs.getUnits()) {
            if (u.getType().isResource) {
                resources.add(u);
            } else if (u.getType().isStockpile) {
                if (isEnemyUnit(u)) {
                    enemyBases.add(u);
                } else {
                    myBases.add(u);
                }
                allBases.add(u);
            } else if (u.getType().canHarvest && isAllyUnit(u)) {
                if (gs.getActionAssignment(u) == null) {
                    myWorkers.add(u);
                } else {
                    myWorkersBusy.add(u);
                }
            } else if (!u.getType().canHarvest && u.getType().canAttack) {
                if (isAllyUnit(u)) {
                    if (gs.getActionAssignment(u) == null) {
                        myCombatUnits.add(u);
                    } else {
                        myCombatUnitsBusy.add(u);
                    }
                } else {
                    enemyCombatUnits.add(u);
                }
            } else if (u.getType().equals(barracksType)) {
                if (isAllyUnit(u)) {
                    myBarracks.add(u);
                } else {
                    enemyBarracks.add(u);
                }
            }

            if (isEnemyUnit(u)) {
                enemies.add(u);
                aliveEnemies.add(u);
            } else {
                myUnits.add(u);
            }
        }


        myClosestResources = new ArrayList<>();
        for (Unit resource : resources) {
            Unit base = closestUnit(resource, allBases);
            if (base != null && isAllyUnit(base)) {
                myClosestResources.add(resource);
            }
        }

        baseDefensePositions = new HashMap<>();
        baseDefensePositionsRanged = new HashMap<>();

        // Defense position specific to each base
        for (Unit base : myBases) {
            baseDefensePositions.put(base.getID(), getDefensePositions(base, defenseBaseDistance));
            baseDefensePositionsRanged.put(base.getID(), getDefensePositions(base, defenseBaseDistance - 1));
        }

        // Damages
        damages = new HashMap<>();
        List<Unit> myUnitsBusy = new LinkedList<>();
        myUnitsBusy.addAll(myCombatUnitsBusy);
        myUnitsBusy.addAll(myWorkersBusy);
        for (Unit unit : myUnitsBusy) {
            UnitAction action = gs.getUnitAction(unit);
            printDebug(unit + " is doing " + action);

            if (action == null || action.getType() != UnitAction.TYPE_ATTACK_LOCATION) {
                continue;
            }

            Unit enemy = pgs.getUnitAt(action.getLocationX(), action.getLocationY());
            if (enemy == null) {
                if (debug) {
                    throw new RuntimeException("Problem: we attack only sure hit enemy but " + unit + " attacking empty cell " + action);
                }
                continue;
            }

            registerAttackDamage(unit, enemy);
        }

        // Harvesting
        List<Long> toBeRemoved = new ArrayList<>();
        List<Unit> myWorkersBusyAndFree = new LinkedList<>();
        myWorkersBusyAndFree.addAll(myWorkers);
        myWorkersBusyAndFree.addAll(myWorkersBusy);
        for (Map.Entry<Long, Long> entry : harvesting.entrySet()) {
            long harvesterID = entry.getKey();
            // Check if worker is dead
            Optional<Unit> worker = myWorkersBusyAndFree.stream().filter(u -> u.getID() == harvesterID).findAny();
            if (!worker.isPresent()) {
                toBeRemoved.add(harvesterID);
                continue;
            }

            long baseID = entry.getValue();
            Optional<Unit> base = myBases.stream().filter(u -> u.getID() == baseID).findAny();
            if (!base.isPresent()) {
                toBeRemoved.add(harvesterID);
            }
        }
        for (long id : toBeRemoved) {
            harvesting.remove(id);
        }

        constructingBarracksForBase = new HashMap<>();
        // Constructing barracks
        for (Unit worker : myWorkersBusy) {
            UnitAction action = gs.getUnitAction(worker);
            if (action.getType() == UnitAction.TYPE_PRODUCE && action.getUnitType() == barracksType) {
                Unit base = closestUnit(worker, myBases);
                if (base != null) {
                    constructingBarracksForBase.put(base.getID(), true);
                }
            }
        }

        // Base separated
        baseSeparated = new HashMap<>();
        for (Unit base : myBases) {
            baseSeparated.put(base.getID(), isBaseSeparated(base));
        }
    }

    private List<Integer> getDefensePositions(Unit base, int defenseBaseDistance) {
        List<Integer> defensePositions = new ArrayList<>();
        for (int x = 0; x < pgs.getWidth(); x++) {
            for (int y = 0; y < pgs.getHeight(); y++) {
                if (distanceChebyshev(base, x, y) != defenseBaseDistance) {
                    continue;
                }

                if (isThereResourceAdjacent(x, y, 1)) {
                    // Prevent another unit to block resource
                    continue;
                }

                int pos = pgsPos(x, y);
                defensePositions.add(pos);
            }
        }
        return defensePositions;
    }


    @Override
    public PlayerAction getAction(int player, GameState gs) {
        long start_time = System.currentTimeMillis();

        this.gs = gs;
        this.pgs = gs.getPhysicalGameState();
        p = gs.getPlayer(player);
        enemyPlayer = gs.getPlayer(player == 0 ? 1 : 0);


        if (myWorkersCombat == null) { // Just started
            int hash = this.pgs.toString().hashCode();
            printDebug("Map hash: " + hash);
            if (hash == 1835166811) {
                this.map = "maps/16x16/basesWorkers16x16A.xml";
            } else {
                this.map = "";
            }
        }

        init();

        if (p.getResources() == 0 && resources.size() == 0) {
            attackAll = true;
            attackWithCombat = true;
        }
        int enemyCombatScore = enemyCombatUnits.stream().mapToInt(Unit::getCost).sum();
        int myCombatScore = myCombatUnits.stream().mapToInt(Unit::getCost).sum() + myCombatUnitsBusy.stream().mapToInt(Unit::getCost).sum();
        if (myCombatScore - 4 > enemyCombatScore) {
            attackWithCombat = true;
        } else {
            attackWithCombat = false;
        }

        this.computeWorkersAction();
        if (this.map.equals("maps/16x16/basesWorkers16x16A.xml")) {
            this.computeBarracksActionBasesWorkers16x16A();
        } else {
            this.computeBarracksAction();
        }
        this.computeBasesAction();
        this.computeCombatUnitsAction();

        printDebug("attackWithCombat " + attackWithCombat + " resources:" + p.getResources());

        long elapsedTime = System.currentTimeMillis() - start_time;
        printDebug(elapsedTime + "ms");
        if (TIME_BUDGET > 0 && elapsedTime >= TIME_BUDGET) {
            printDebug("TIMEOUT");
        }

        PlayerAction pa = translateActions(player, gs);
        printDebug("actions: " + pa);
        return pa;
    }

    // Flood fill to check if the base is separated from enemies
    private boolean isBaseSeparated(Unit base) {
        if (pgs.getWidth() >= 10) {
            // Map too big to flood fill
            return false;
        }
        List<Position> stack = new LinkedList<>();
        List<Position> visited = new LinkedList<>();

        stack.add(new Position(base.getX(), base.getY()));
        int visitedPos = 0;

        while (!stack.isEmpty()) {
            visitedPos++;

            Position pos = stack.remove(0);

            Unit unit = pgs.getUnitAt(pos.getX(), pos.getY());
            if (unit != null && isEnemyUnit(unit)) {
                return false;
            }

            List<Position> validAdjacentPos = pos.adjacentPos().stream()
                    .filter(this::isValidPos)
                    .filter(p -> !visited.contains(p))
                    .filter(p -> !stack.contains(p))
                    .filter(p -> {

                        if (pgs.getTerrain(pos.getX(), pos.getY()) == PhysicalGameState.TERRAIN_WALL) {
                            return false;
                        }

                        Unit unitAtPos = pgs.getUnitAt(pos.getX(), pos.getY());
                        if (unitAtPos == null) {
                            return true;
                        }

                        return !unitAtPos.getType().isResource;

                    }).collect(Collectors.toList());

            stack.addAll(validAdjacentPos);
            visited.add(pos);
        }

        printDebug("visited " + visitedPos);

        wasSeparated = true;
        return true;
    }

    private void computeBarracksAction() {
        long heavyCount = myCombatUnits.stream().filter(u -> u.getType() == heavyType).count() + myCombatUnitsBusy.stream().filter(u -> u.getType() == heavyType).count();
        for (Unit barrack : myBarracks) {
            if (heavyCount > 3) {
                train(barrack, rangedType);
            } else {
                if (wasSeparated) {
                    train(barrack, rangedType);
                } else {
                    train(barrack, heavyType);
                }
            }
        }
    }

    private void computeBarracksActionBasesWorkers16x16A() {
        long heavyCount = myCombatUnits.stream().filter(u -> u.getType() == heavyType).count() + myCombatUnitsBusy.stream().filter(u -> u.getType() == heavyType).count();
        for (Unit barrack : myBarracks) {
            if (heavyCount > 0) {
                train(barrack, rangedType);
            } else {
                if (wasSeparated) {
                    train(barrack, rangedType);
                } else {
                    train(barrack, heavyType);
                }
            }
        }
    }

    // Attack only sure hit enemies
    private void computeCombatAction(Unit unit) {
        List<Unit> aliveEnemies = this.aliveEnemies;
        if (aliveEnemies.isEmpty()) {
            printDebug(unit + " no enemies, idling");
            actions.put(unit, new TrueIdle(unit));
            return;
        }

        List<Unit> enemiesInRange = aliveEnemies.stream().filter(e -> enemyIsInRangeAttack(unit, e)).collect(Collectors.toList());
        List<Unit> attackableEnemies = enemiesInRange.stream().filter(e -> !willBeMoveBeforeAttack(unit, e)).collect(Collectors.toList());

        // Attack enemies in range that will not be moved
        // We are sure to record the attack
        if (!attackableEnemies.isEmpty()) {
            Unit closestEnemy = bestToAttack(unit, attackableEnemies); // TODO  instead of taking closest: get the enemy with least allies around to attack to avoid overkill
            printDebug(unit + " attacking " + closestEnemy + " damageBeforeAttack:" + damages.getOrDefault(closestEnemy.getID(), 0) + " enemyAction:" + gs.getActionAssignment(closestEnemy));

            attackAndRegisterDamage(unit, closestEnemy);
            return;
        }

        // There are moving enemies
        if (!enemiesInRange.isEmpty()) {
            if (unit.getAttackRange() > 1) {
                Unit movingEnemy = closestUnitAfterMoveAction(unit, enemiesInRange);

                // Next enemy position he still cant attack
                if (squareDist(unit, nextPos(movingEnemy, gs)) > movingEnemy.getAttackRange()) {
                    printDebug("ranged flee safe");
                    moveAwayFrom(unit, movingEnemy);
                    return;
                } else {
                    // Next position he can attack
                    if (gs.getActionAssignment(movingEnemy).time - gs.getTime() + movingEnemy.getAttackTime() >= unit.getMoveTime()) {
                        printDebug("ranged flee adjacent");
                        moveAwayFrom(unit, movingEnemy);
                    }
                }

            }

            // Otherwise wait for its move action
            printDebug(unit + " wait for moving unit");
            actions.put(unit, new TrueIdle(unit));
            return;
        }

        Unit closestEnemy = closestUnitAfterMoveAction(unit, aliveEnemies);

        if (attackWithCombat || distance(unit, closestEnemy) <= 5) {
            printDebug(unit + " chasing <=5 " + closestEnemy);

            chaseToAttack(unit, closestEnemy);
            return;
        }

        List<Unit> myUnits = new ArrayList<>();
        myUnits.addAll(myCombatUnits);
        myUnits.addAll(myCombatUnitsBusy);
        myUnits.addAll(myWorkersCombat);
        if (myUnits.isEmpty()) {
            if (gs.getTime() > MAXCYCLES / 2) {
                // Move to the closest enemy to attack
                printDebug(unit + " chasing " + closestEnemy);
                chaseToAttack(unit, closestEnemy);
                return;
            }

            printDebug(unit + " waiting for more unit ");
            actions.put(unit, new TrueIdle(unit));
            return;
        }

        Position centroid = centroid(myUnits);
        if (gs.getTime() > MAXCYCLES / 2 || distance(unit, centroid) <= 3) { // After half of the game, need to attack
            // Move to the closest enemy to attack
            printDebug(unit + " chasing " + closestEnemy);
            chaseToAttack(unit, closestEnemy);
        } else {
            printDebug(unit + " moving to centroid " + centroid);
            move(unit, centroid.getX(), centroid.getY());
        }
    }

    private void moveAwayFrom(Unit unit, Unit movingEnemy) {
        Position enemyPos = nextPos(movingEnemy, gs);
        int currentDist = distance(unit, enemyPos);

        Position newPos;
        Position posUp = new Position(unit.getX(), unit.getY() - 1);
        Position posRight = new Position(unit.getX() + 1, unit.getY());
        Position posLeft = new Position(unit.getX() - 1, unit.getY());
        Position posDown = new Position(unit.getX(), unit.getY() + 1);
        if (isValidPos(posUp) && isFreePos(posUp) && distance(enemyPos, posUp) > currentDist) {
            newPos = posUp;
        } else if (isValidPos(posDown) && isFreePos(posDown) && distance(enemyPos, posDown) > currentDist) {
            newPos = posDown;
        } else if (isValidPos(posRight) && isFreePos(posRight) && distance(enemyPos, posRight) > currentDist) {
            newPos = posRight;
        } else if (isValidPos(posLeft) && isFreePos(posLeft) && distance(enemyPos, posLeft) > currentDist) {
            newPos = posLeft;
        } else {
            printDebug(unit + " cant flee, blocked, waiting");
            actions.put(unit, new TrueIdle(unit));
            return;
        }

        if (enemyPos.x == unit.getX()) {
            if (isValidPos(posUp) && isFreePos(posUp) && distance(enemyPos, posUp) > currentDist) {
                newPos = posUp;
            } else if (isValidPos(posDown) && isFreePos(posDown) && distance(enemyPos, posDown) > currentDist) {
                newPos = posDown;
            }
        } else if (enemyPos.y == unit.getY()) {
            if (isValidPos(posRight) && isFreePos(posRight) && distance(enemyPos, posRight) > currentDist) {
                newPos = posRight;
            } else if (isValidPos(posLeft) && isFreePos(posLeft) && distance(enemyPos, posLeft) > currentDist) {
                newPos = posLeft;
            }
        }

        printDebug(unit + " fleeing away from " + movingEnemy + " going to " + newPos);
        move(unit, newPos.x, newPos.y);
    }

    private int sumDistanceFromEnemy(Position pos) {
        return aliveEnemies.stream().mapToInt(e -> distance(pos, nextPos(e, gs))).sum();
    }

    private void computeCombatUnitsAction() {
        for (Unit unit : myCombatUnits) {
            computeCombatAction(unit);
        }
    }


    private void computeBasesAction() {
        for (Unit base : myBases) {
            if (baseSeparated.getOrDefault(base.getID(), false)) {
                continue;
            }

            if (pgs.getWidth() <= 8) { // Always train worker on small map
                train(base, workerType);
                continue;
            }

        }

        final int workerPerBase = 2;
        int producingWorker = 0;
        long producingCount = myBases.stream().filter(b -> gs.getActionAssignment(b) != null).count();
        for (Unit base : myBases) {
            // Dont produce if not in abundance
            if (myWorkers.size() + myWorkersBusy.size() + producingWorker + producingCount >= workerPerBase * myBases.size() && p.getResources() - resourceUsed < 15) {
                return;
            }
            train(base, workerType);
            producingWorker++;
        }
    }


    private void computeWorkersAction() {
        if (attackAll) {
            for (Unit worker : myWorkers) {
                computeCombatAction(worker);
            }
            return;
        }

        // TODO build base if no and resource available
        // TODO build barrack if far and have resource
        // Build barrack
        buildBarracks();

        // Harvest
        if (!myWorkers.isEmpty() && !myBases.isEmpty() && !resources.isEmpty()) {
            for (int i = 0; i < myBases.size(); i++) { // numberOfBaseWithNoHarvester
                // Find worker to assign to harvest
                Optional<Unit> optionalUnit = myWorkers.stream().filter(w -> !harvesting.containsKey(w.getID())).min(Comparator.comparingInt(this::harvestScore));

                Unit harvesterWorker = null;
                if (optionalUnit.isPresent()) {
                    harvesterWorker = optionalUnit.get();
                }
                if (harvesterWorker == null || harvestScore(harvesterWorker) == Integer.MAX_VALUE) {
                    break;
                }
                harvestClosest(harvesterWorker);
            }
        }

        // Use the remaining to defend/attack/construct
        for (Unit worker : myWorkers) {
            boolean isHarvester = harvesting.containsKey(worker.getID());
            Unit closestEnemy = closestUnit(worker, this.aliveEnemies);

            if (isHarvester) { // Worker assign to harvesting is idling
                if (closestEnemy != null && distance(closestEnemy, worker) <= 2) {
                    harvesting.remove(worker.getID()); // Worker assign to fight, removing to harvesting
                    myWorkersCombat.add(worker);
                    computeCombatAction(worker);
                    continue;
                }
                long baseID = harvesting.get(worker.getID());
                Unit assignedBase = gs.getUnit(baseID);
                Unit closestResource = closestUnit(worker, resources);
                printDebug(worker + " new assigned harvest " + closestResource);
                actions.put(worker, new HarvestReturn(worker, closestResource, assignedBase, pf));
                continue;
            }

//            if (closestEnemy != null && distance(closestEnemy, worker) <= 5) {
//                computeCombatAction(worker);
//                continue;
//            }

//            if (moveDefensePosition(worker)) {
//                continue;
//            }
//            actions.put(worker, new TrueIdle(worker));

            myWorkersCombat.add(worker);
            computeCombatAction(worker);
        }
    }

    private void buildBarracks() {
        if (myBarracks.size() == 0 && p.getResources() - resourceUsed >= barracksType.cost + 1) {
            for (Unit base : myBases) {
                if (constructingBarracksForBase.containsKey(base.getID())) {
                    continue;
                }

                boolean safeDistanceToBuild = true;

                if (!baseSeparated.getOrDefault(base.getID(), false)) {
                    Unit enemy = closestUnit(base, this.aliveEnemies);
                    if (enemy != null) {
                        safeDistanceToBuild = (distance(enemy, base)) * workerType.moveTime > barracksType.produceTime;
                    }

                    // We have more resource, implicitly mean our harvester are safe, so can build barracks
                    if (p.getResources() > 10 && p.getResources() >= enemyPlayer.getResources()) {
                        safeDistanceToBuild = true;
                    }
                }

                if (!safeDistanceToBuild) {
                    continue;
                }

                Unit closestWorker = closestUnit(base, myWorkers);
                if (closestWorker == null) {
                    break;
                }

                Unit enemy = closestUnit(closestWorker, this.aliveEnemies);
                if (enemy != null && distance(closestWorker, enemy) <= 2) {
                    continue;
                }

                if (distance(closestWorker, base) > 2) {
                    continue;
                }

                Position workerPos = new Position(closestWorker.getX(), closestWorker.getY());
                List<Position> adjacentPos = workerPos.adjacentPos();

                // Build closest to enemies when base separated otherwise further
                Position bestPos = null;
                int closestDist = wasSeparated ? 9999999 : 0;
                for (Position pos : adjacentPos) {
                    if (!isValidPos(pos)) {
                        continue;
                    }

                    if (isThereResourceAdjacent(pos.getX(), pos.getY(), 1)) {
                        // Prevent another unit to block resource
                        continue;
                    }

                    if (pgs.getUnitAt(pos.x, pos.y) != null) {
                        continue;
                    }

                    if (enemy == null) {
                        break;
                    }

                    int dist = distance(enemy, pos);
                    if (wasSeparated && dist < closestDist || !wasSeparated && dist > closestDist) {
                        bestPos = pos;
                        closestDist = dist;
                    }
                }
                if (bestPos == null) {
                    continue;
                }

                actions.put(closestWorker, new BuildModified(closestWorker, barracksType, bestPos.x, bestPos.y, pf));
                printDebug(closestWorker + " BUILDING at " + bestPos);

                myWorkers.remove(closestWorker);
                myWorkersBusy.add(closestWorker);
                resourceUsed += barracksType.cost;
            }
        }
    }

    private void printDebug(String string) {
        if (!debug) {
            return;
        }
        System.out.println(string);
    }

    private boolean isThereResourceAdjacent(int x, int y, int distance) {
        Collection<Unit> unitsAround = pgs.getUnitsAround(x, y, distance, distance);
        Optional<Unit> resource = unitsAround.stream().filter(unit -> unit.getType().isResource).findAny();
        return resource.isPresent();
    }

    private boolean isThereBaseAdjacent(int x, int y, int distance) {
        Collection<Unit> unitsAround = pgs.getUnitsAround(x, y, distance, distance);
        Optional<Unit> resource = unitsAround.stream().filter(unit -> unit.getType().isStockpile).findAny();
        return resource.isPresent();
    }

    private void chaseToAttack(Unit worker, Unit closestEnemy) {
        attackAndRegisterDamage(worker, closestEnemy);
    }

    private void attackAndRegisterDamage(Unit worker, Unit closestEnemy) {
        if (enemyIsInRangeAttack(worker, closestEnemy)) {
            registerAttackDamage(worker, closestEnemy);
        }
        actions.put(worker, new CoacAttack(worker, closestEnemy, pf));
    }

    private void registerAttackDamage(Unit worker, Unit closestEnemy) {
        int damage = (worker.getMinDamage() + worker.getMaxDamage()) / 2;
        long enemyID = closestEnemy.getID();

        damages.put(enemyID, damage + damages.getOrDefault(enemyID, 0));
        if (damages.get(enemyID) >= closestEnemy.getHitPoints()) {
            aliveEnemies.remove(closestEnemy);
        }
    }

    private void defendRangedUnit(Unit unit) {
        List<Unit> myRanged = myUnits.stream().filter(u -> u.getAttackRange() > 1).collect(Collectors.toList());
        Unit closestRanged = closestUnitAfterMoveAction(unit, myRanged);
    }

    private boolean moveDefensePosition(Unit warrior) {
        Map<Long, List<Integer>> baseDefensePositions;
        if (warrior.getAttackRange() > 1) {
            baseDefensePositions = this.baseDefensePositionsRanged;
        } else {
            baseDefensePositions = this.baseDefensePositions;
        }

        // Defense position
        Unit closestBase = closestUnit(warrior, myBases);
        if (closestBase == null) {
            return false;
        }
        Unit closestEnemyFromBase = closestUnit(closestBase, aliveEnemies);
        if (closestEnemyFromBase == null) {
            return false;
        }

        List<Integer> defensePositions = baseDefensePositions.get(closestBase.getID());
        int bestPos = -1;
        int minDist = Integer.MAX_VALUE;

        for (Integer defensePos : defensePositions) {
            Unit existingUnit = pgs.getUnitAt(pgsIntToPosX(defensePos), pgsIntToPosY(defensePos));
            if (existingUnit != null && isAllyUnit(existingUnit)) {
                continue;
            }

            int dist = astar.findDistToPositionInRange(warrior, defensePos, 0, gs, gs.getResourceUsage());
            // Prioritize defense pos closest to enemy
            int enemyDist = distance(closestEnemyFromBase, pgsIntToPosX(defensePos), pgsIntToPosY(defensePos));
            if (dist != -1 && enemyDist < minDist) {
                minDist = enemyDist;
                bestPos = defensePos;
            }
        }
        if (bestPos == -1) {
            return false;
        }

        int workerPos = warrior.getPosition(pgs);
        if (defensePositions.contains(workerPos)) {
            if (minDist <= distance(closestEnemyFromBase, pgsIntToPosX(workerPos), pgsIntToPosY(workerPos))) {
                return false;
            }
        }

        move(warrior, pgsIntToPosX(bestPos), pgsIntToPosY(bestPos));
        return true;
    }


    private int pgsPos(int x, int y) {
        return x + pgs.getWidth() * y;
    }

    private int pgsIntToPosX(int pos) {
        return pos % pgs.getWidth();
    }

    private Position pgsIntToPos(int pos) {
        return new Position(pgsIntToPosX(pos), pgsIntToPosY(pos));
    }

    private int pgsIntToPosY(int pos) {
        return pos / pgs.getWidth();
    }

    private void harvestClosest(Unit worker) {
        Unit closestResource = closestUnit(worker, myClosestResources);
        Unit closestBase = closestUnit(worker, myBases.stream().filter(base -> !baseHasEnoughHarvester(base)).collect(Collectors.toList()));

        printDebug(worker + " harvesting " + closestResource);

        harvesting.put(worker.getID(), closestBase.getID());
        harvest(worker, closestResource, closestBase);
    }

    private int harvestScore(Unit worker) {
        Unit closestResource = closestUnit(worker, myClosestResources);
        Unit closestBase = closestUnit(worker, myBases.stream().filter(base -> !baseHasEnoughHarvester(base)).collect(Collectors.toList()));
        if (closestBase == null || closestResource == null) {
            return Integer.MAX_VALUE;
        }

        return distance(worker, closestBase) + distance(worker, closestResource);
    }

    private boolean baseHasEnoughHarvester(Unit base) {
        return harvesting.values().stream().filter(b -> b == base.getID()).count() >= 2;
    }

    private Unit closestUnit(Unit worker, List<Unit> enemies) {
        Unit closestEnemy = null;
        if (!enemies.isEmpty()) {
            closestEnemy = enemies.stream().min(Comparator.comparing(u -> distance(worker, u))).get();
        }
        return closestEnemy;
    }

    // Prioritize enemy that will be kill otherwise closest
    private Unit bestToAttack(Unit worker, List<Unit> enemies) {
        Unit closestEnemy = null;
        if (!enemies.isEmpty()) {
            closestEnemy = enemies.stream().max((a, b) -> {
                int aRemainingHP = a.getHitPoints() - damages.getOrDefault(a.getID(), 0) - worker.getMinDamage();
                int bRemainingHP = b.getHitPoints() - damages.getOrDefault(b.getID(), 0) - worker.getMinDamage();
                if (aRemainingHP <= 0 && bRemainingHP <= 0) {
                    return aRemainingHP - bRemainingHP;
                }

                if (aRemainingHP <= 0) {
                    return 1;
                }

                if (bRemainingHP <= 0) {
                    return -1;
                }

                return -distance(worker, a) + distance(worker, b);
            }).get();
        }
        return closestEnemy;
    }

    // Closest unit after its moving action
    private Unit closestUnitAfterMoveAction(Unit worker, List<Unit> enemies) {
        Unit closestEnemy = null;
        if (!enemies.isEmpty()) {
            closestEnemy = enemies.stream().min(Comparator.comparing(u -> distance(worker, nextPos(u, gs)))).get();
        }
        return closestEnemy;
    }

    private boolean isEnemyUnit(Unit u) {
        return u.getPlayer() >= 0 && u.getPlayer() != p.getID();
    }

    private boolean isAllyUnit(Unit u) {
        return u.getPlayer() == p.getID();
    }


    @Override
    public List<ParameterSpecification> getParameters() {
        return new ArrayList<>();
    }


    private boolean willBeMoveBeforeAttack(Unit unit, Unit closestEnemy) {
        UnitActionAssignment aa = gs.getActionAssignment(closestEnemy);
        if (aa == null) {
            return false;
        }

        if (aa.action.getType() != UnitAction.TYPE_MOVE) {
            return false;
        }

        int eta = aa.action.ETA(closestEnemy) - (gs.getTime() - aa.time);
        return eta <= unit.getAttackTime();
    }

    public void train(Unit u, UnitType unit_type) {
        if (gs.getActionAssignment(u) != null) {
            return;
        }
        if (p.getResources() - resourceUsed < unit_type.cost) {
            return;
        }
        resourceUsed += unit_type.cost;


        List<Integer> directions = new ArrayList<>();
        directions.add(UnitAction.DIRECTION_UP);
        directions.add(UnitAction.DIRECTION_DOWN);
        directions.add(UnitAction.DIRECTION_LEFT);
        directions.add(UnitAction.DIRECTION_RIGHT);

        int bestDirection = directions.stream().max(Comparator.comparingInt((d) -> scoreTrainDirection(u, d))).get();

        if (scoreTrainDirection(u, bestDirection) == Integer.MIN_VALUE) {
            printDebug(u + " failed to train");
            return;
        }

        actions.put(u, new TrainDirection(u, unit_type, bestDirection));
    }

    int scoreTrainDirection(Unit u, int direction) {
        int newPosX = u.getX() + UnitAction.DIRECTION_OFFSET_X[direction];
        int newPosY = u.getY() + UnitAction.DIRECTION_OFFSET_Y[direction];
        Position pos = new Position(newPosX, newPosY);
        if (!isValidPos(pos) || !gs.free(newPosX, newPosY)) {
            return Integer.MIN_VALUE;
        }

        Unit enemy = closestUnit(u, enemies);

        return -distance(enemy, pos);
    }

    public static class Position {
        int x;
        int y;

        public Position(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public List<Position> adjacentPos() {
            return Stream.of(
                    new Position(x, y + 1),
                    new Position(x, y - 1),
                    new Position(x + 1, y),
                    new Position(x - 1, y)
            ).collect(Collectors.toList());
        }

        @Override
        public String toString() {
            return "Position{" +
                    "x=" + x +
                    ", y=" + y +
                    '}';
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Position position = (Position) o;
            return x == position.x &&
                    y == position.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    public static Position nextPos(Unit target, GameState gs) {
        UnitAction targetAction = gs.getUnitAction(target);
        if (targetAction != null && targetAction.getType() == UnitAction.TYPE_MOVE) {

            if (targetAction.getDirection() != UnitAction.DIRECTION_NONE) {
                int newPosX = target.getX() + UnitAction.DIRECTION_OFFSET_X[targetAction.getDirection()];
                int newPosY = target.getY() + UnitAction.DIRECTION_OFFSET_Y[targetAction.getDirection()];

                return new Position(newPosX, newPosY);
            }
        }

        return new Position(target.getX(), target.getY());
    }

    private boolean isValidPos(Position pos) {
        return pos.x < pgs.getWidth() && pos.x >= 0 && pos.y < pgs.getHeight() && pos.y >= 0;
    }

    private boolean isFreePos(Position pos) {
        if (pgs.getTerrain(pos.getX(), pos.getY()) == PhysicalGameState.TERRAIN_WALL) {
            return false;
        }

        Unit unitAtPos = pgs.getUnitAt(pos.getX(), pos.getY());
        if (unitAtPos != null) {
            return false;
        }

        return true;
    }

    public static boolean enemyIsInRangeAttack(Unit ourUnit, Unit closestUnit) {
        return squareDist(ourUnit, closestUnit) <= ourUnit.getAttackRange();
    }

    static double squareDist(Unit ourUnit, Unit closestUnit) {
        int dx = closestUnit.getX() - ourUnit.getX();
        int dy = closestUnit.getY() - ourUnit.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    public static double squareDist(Unit ourUnit, Position pos2) {
        int dx = pos2.getX() - ourUnit.getX();
        int dy = pos2.getY() - ourUnit.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    int distance(Unit u1, Unit u2) {
//        int dist=  astar.findDistToPositionInRange(u1, pgsPos(u2.getX(), u2.getY()) , 1, gs, gs.getResourceUsage());
//        if (dist == -1) {
//            return Integer.MAX_VALUE;
//        }
//        return dist;
        return Math.abs(u1.getX() - u2.getX()) + Math.abs(u1.getY() - u2.getY());
    }

    static int distance(Unit u1, Position pos2) {
        return Math.abs(u1.getX() - pos2.getX()) + Math.abs(u1.getY() - pos2.getY());
    }

    static int distance(Position u1, Position pos2) {
        return Math.abs(u1.getX() - pos2.getX()) + Math.abs(u1.getY() - pos2.getY());
    }

    static int distance(Unit u1, int x, int y) {
        return Math.abs(u1.getX() - x) + Math.abs(u1.getY() - y);
    }

    static int distanceChebyshev(Unit u1, int x2, int y2) {
        return Math.max(Math.abs(x2 - u1.getX()), Math.abs(y2 - u1.getY()));
    }

    static Position centroid(List<Unit> units) {
        int centroidX = 0, centroidY = 0;
        for (Unit unit : units) {
            centroidX += unit.getX();
            centroidY += unit.getY();
        }
        return new Position(centroidX / units.size(), centroidY / units.size());
    }

    static int oppositeDirection(int direction) {
        if (direction == UnitAction.DIRECTION_UP) {
            return UnitAction.DIRECTION_DOWN;
        }

        if (direction == UnitAction.DIRECTION_DOWN) {
            return UnitAction.DIRECTION_UP;
        }

        if (direction == UnitAction.DIRECTION_LEFT) {
            return UnitAction.DIRECTION_RIGHT;
        }

        if (direction == UnitAction.DIRECTION_RIGHT) {
            return UnitAction.DIRECTION_LEFT;
        }
        throw new RuntimeException("direction not recognized");
    }

}
