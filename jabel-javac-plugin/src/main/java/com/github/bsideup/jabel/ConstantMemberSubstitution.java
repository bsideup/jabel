package com.github.bsideup.jabel;

import net.bytebuddy.asm.MemberSubstitution;
import net.bytebuddy.description.ByteCodeElement;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.implementation.bytecode.Removal;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.constant.IntegerConstant;

class ConstantMemberSubstitution implements MemberSubstitution.Substitution {

    static MemberSubstitution.Substitution.Factory of(boolean value) {
        return (instrumentedType, instrumentedMethod, typePool) -> {
            return new ConstantMemberSubstitution(IntegerConstant.forValue(value));
        };
    }

    private final StackManipulation value;

    private ConstantMemberSubstitution(StackManipulation value) {
        this.value = value;
    }

    @Override
    public StackManipulation resolve(
            TypeDescription targetType,
            ByteCodeElement target,
            TypeList.Generic parameters,
            TypeDescription.Generic result,
            int freeOffset
    ) {
        return new StackManipulation.Compound(
                // remove aload_0
                Removal.of(targetType),
                value
        );
    }
}
