package com.template.states;

import com.r3.corda.lib.tokens.contracts.types.TokenType;
import org.jetbrains.annotations.NotNull;

public class EnergyTokenType extends TokenType {
    public EnergyTokenType(@NotNull String tokenIdentifier, int fractionDigits) {
        super(tokenIdentifier, fractionDigits);
    }
}
