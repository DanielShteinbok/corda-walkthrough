package com.template.flows;

import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
import com.r3.corda.lib.tokens.workflows.utilities.FungibleTokenBuilder;
import com.template.states.EnergyTokenType;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;

public class IssueTokensFlow extends FlowLogic<SignedTransaction> {

    // the amount to issue
    private long amount;

    // the node to which to issue
    private Party recipient;

    public IssueTokensFlow(long amount, Party recipient) {
        this.amount = amount;
        this.recipient = recipient;
    }
    @Override
    public SignedTransaction call() throws FlowException {
        // now we need to use the token builder to build a FungibleToken of the right amount
        FungibleToken energyToken = new FungibleTokenBuilder()
                .ofTokenType(new EnergyTokenType())
                .withAmount(amount)
                .issuedBy(getOurIdentity())
                .heldBy(recipient)
                .buildFungibleToken();

        // issue the tokens built above
        return subFlow(new IssueTokens(ImmutableList.of(energyToken)));
    }
}
