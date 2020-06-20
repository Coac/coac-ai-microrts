/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ai.coac;

import ai.abstraction.AbstractAction;
import rts.GameState;
import rts.ResourceUsage;
import rts.UnitAction;
import rts.units.Unit;
import util.XMLWriter;

/**
 * @author santi
 */
public class AttackPosition extends AbstractAction {
    private final int targetX;
    private final int targetY;
    Unit unit;

    public AttackPosition(Unit u, int x, int y) {
        super(u);
        unit = u;
        targetX = x;
        targetY = y;
    }


    public boolean completed(GameState gs) {
        return true;
    }


    public boolean equals(Object o) {
        if (!(o instanceof AttackPosition)) return false;
        AttackPosition a = (AttackPosition) o;
        return targetX == a.targetX && targetY == a.targetY;
    }


    public void toxml(XMLWriter w) {
        w.tagWithAttributes("Attack", "unitID=\"" + unit.getID() + "\" target=\"" + targetX + targetY);
        w.tag("/Attack");
    }


    public UnitAction execute(GameState gs, ResourceUsage ru) {
        int dx = targetX - unit.getX();
        int dy = targetY - unit.getY();
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d <= unit.getAttackRange()) {
            return new UnitAction(UnitAction.TYPE_ATTACK_LOCATION, targetX, targetY);
        } else {
            return null;
        }
    }
}
