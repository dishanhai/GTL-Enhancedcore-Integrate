package com.gtl.enhancedcore.common.machine.hatch;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.ICleanroomProvider;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.capability.ICleanroomReceiver;
import com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType;
import java.util.Set;
import org.gtlcore.gtlcore.common.machine.multiblock.part.maintenance.AutoConfigurationMaintenanceHatchPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.maintenance.IGravityPartMachine;
import org.gtlcore.gtlcore.api.machine.multiblock.GTLCleanroomType;

/** MAX-tier automatic maintenance hatch for cleanroom and gravity-gated recipes. */
public final class AdaptiveMaintenanceHatchMachine extends AutoConfigurationMaintenanceHatchPartMachine
        implements ICleanroomProvider, IGravityPartMachine {
    private static final Set<CleanroomType> ENVIRONMENTS = Set.of(
            CleanroomType.CLEANROOM, CleanroomType.STERILE_CLEANROOM, GTLCleanroomType.LAW_CLEANROOM);

    public AdaptiveMaintenanceHatchMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public Set<CleanroomType> getTypes() {
        return ENVIRONMENTS;
    }

    @Override
    public boolean isClean() {
        return true;
    }

    @Override
    public int getCurrentGravity() {
        return 100;
    }

    @Override
    public void setCurrentGravity(int gravity) {
        // This hatch represents both gravity extremes; no configurable value is stored.
    }

    @Override
    public void addedToController(IMultiController controller) {
        super.addedToController(controller);
        if (controller instanceof ICleanroomReceiver receiver) receiver.setCleanroom(this);
    }

    @Override
    public void removedFromController(IMultiController controller) {
        super.removedFromController(controller);
        if (controller instanceof ICleanroomReceiver receiver && receiver.getCleanroom() == this) {
            receiver.setCleanroom(null);
        }
    }

    @Override
    public int getTier() {
        return com.gregtechceu.gtceu.api.GTValues.MAX;
    }
}
