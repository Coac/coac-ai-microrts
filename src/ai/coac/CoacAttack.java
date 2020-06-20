/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ai.coac;

import ai.abstraction.AbstractAction;
import ai.abstraction.pathfinding.PathFinding;
import rts.GameState;
import rts.PhysicalGameState;
import rts.ResourceUsage;
import rts.UnitAction;
import rts.units.Unit;
import util.XMLWriter;

import java.util.Random;

/**
 * Attack action
 * Wait for the enemy if he is coming to range
 * If no path, do random move
 *
 * @author Coac
 */
public class CoacAttack extends AbstractAction {
    Unit target;
    Unit unit;
    PathFinding pf;

    public CoacAttack(Unit u, Unit a_target, PathFinding a_pf) {
        super(u);
        unit = u;
        target = a_target;
        pf = a_pf;
    }


    public boolean completed(GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        return !pgs.getUnits().contains(target);
    }


    public boolean equals(Object o) {
        if (!(o instanceof CoacAttack)) return false;
        CoacAttack a = (CoacAttack) o;
        return target.getID() == a.target.getID() && pf.getClass() == a.pf.getClass();
    }


    public void toxml(XMLWriter w) {
        w.tagWithAttributes("Attack", "unitID=\"" + unit.getID() + "\" target=\"" + target.getID() + "\" pathfinding=\"" + pf.getClass().getSimpleName() + "\"");
        w.tag("/Attack");
    }


    public UnitAction execute(GameState gs, ResourceUsage ru) {
        if (CoacAI.enemyIsInRangeAttack(unit, target)) {
            return new UnitAction(UnitAction.TYPE_ATTACK_LOCATION, target.getX(), target.getY());
        }

        CoacAI.Position newPosition = CoacAI.nextPos(target, gs);
        if (CoacAI.squareDist(unit, newPosition) <= unit.getAttackRange()) {
            return new UnitAction(UnitAction.TYPE_NONE, 1); // Wait if the enemy is coming in range
        }

        UnitAction move = pf.findPathToPositionInRange(unit, target.getX() + target.getY() * gs.getPhysicalGameState().getWidth(), unit.getAttackRange(), gs, ru);
        if (move != null && gs.isUnitActionAllowed(unit, move)) return move;


        // Random move if no path
        int m = new Random().nextInt(4);
        UnitAction m1 = new UnitAction(UnitAction.TYPE_MOVE, m);
        UnitAction m2 = new UnitAction(UnitAction.TYPE_MOVE, (m + 1) % 4);
        UnitAction m3 = new UnitAction(UnitAction.TYPE_MOVE, (m + 2) % 4);
        UnitAction m4 = new UnitAction(UnitAction.TYPE_MOVE, (m + 3) % 4);
        if (gs.isUnitActionAllowed(unit, m1)) return m1;
        if (gs.isUnitActionAllowed(unit, m2)) return m2;
        if (gs.isUnitActionAllowed(unit, m3)) return m3;
        if (gs.isUnitActionAllowed(unit, m4)) return m4;

        return new UnitAction(UnitAction.TYPE_NONE, 1);
    }
}
