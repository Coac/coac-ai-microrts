package ai.coac;

import ai.abstraction.AbstractAction;
import rts.GameState;
import rts.ResourceUsage;
import rts.UnitAction;
import rts.units.Unit;
import util.XMLWriter;

/**
 * @author Coac
 */
public class TrueIdle extends AbstractAction {

    public TrueIdle(Unit u) {
        super(u);
    }

    public boolean completed(GameState gs) {
        return false;
    }


    public boolean equals(Object o) {
        return o instanceof TrueIdle;
    }


    public void toxml(XMLWriter w) {
        w.tagWithAttributes("TrueIdle", "");
        w.tag("/TrueIdle");
    }

    public UnitAction execute(GameState gs, ResourceUsage ru) {
        return new UnitAction(UnitAction.TYPE_NONE, 1);
    }
}
