package com.gtl.enhancedcore.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;

/**
 * 超级粒子对撞机（{@code gtceu:super_particle_collider}，GTLCore 注册）。
 *
 * <p>用户 2026-10-12 指定：加 <b>ADD 跨配方线程</b> 128 条，且**天球分歧引擎（Ω）叠加**。
 * <b>不接入本模组的跨配方类型并行（IV 隔离订单）</b>——见 {@link AddThreadMutableMachine}。
 */
public final class SuperParticleColliderMutableMachine extends AddThreadMutableMachine {

    public SuperParticleColliderMutableMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, "super_particle_collider", args);
    }
}
