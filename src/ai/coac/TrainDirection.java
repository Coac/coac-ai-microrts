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
import rts.units.UnitType;
import util.XMLWriter;

import java.util.Objects;

/**
 * @author santi
 */
public class TrainDirection extends AbstractAction {
    UnitType type;
    Unit unit;
    int direction;
    boolean completed = false;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrainDirection that = (TrainDirection) o;
        return direction == that.direction &&
                completed == that.completed &&
                Objects.equals(type, that.type);
    }

    public TrainDirection(Unit u, UnitType a_type, int direction) {
        super(u);
        unit = u;
        this.direction = direction;
        type = a_type;
    }

    public boolean completed(GameState pgs) {
        return completed;
    }


    public void toxml(XMLWriter w) {
        w.tagWithAttributes("Train", "unitID=\"" + unit.getID() + "\" type=\"" + type.name + "\"");
        w.tag("/Train");
    }

    public UnitAction execute(GameState gs, ResourceUsage ru) {
        completed = true;
        UnitAction ua = new UnitAction(UnitAction.TYPE_PRODUCE, direction, type);
        if (gs.isUnitActionAllowed(unit, ua)) return ua;

        System.out.println("WARNING: TrainDirection invalid direction");
        return new UnitAction(UnitAction.TYPE_NONE, 1);
    }
}
