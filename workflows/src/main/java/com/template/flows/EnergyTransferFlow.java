package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount;
import com.r3.corda.lib.accounts.workflows.services.AccountService;
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService;
import com.r3.corda.lib.tokens.workflows.flows.move.MoveTokensUtilities;
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount;
import com.template.contracts.Commands;
import com.template.states.EnergyTokenType;
import net.corda.core.contracts.Amount;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import org.jetbrains.annotations.NotNull;


public class EnergyTransferFlow {

    @InitiatingFlow
    @StartableByRPC
    public class SendEnergyTokens extends FlowLogic<SignedTransaction> {
        private final long amount;
        private final String whereTo;
        private final Party sanctionsBody;

        public SendEnergyTokens(long amount, String whereTo, Party sanctionsBody) {
            this.amount = amount;
            this.whereTo =  whereTo;
            this.sanctionsBody = sanctionsBody;
        }

        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {
            // get the first notary we see; here we assume that there is one notary in the network
            Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            // turn KeyManagementBackedAccountService into a cordaService
            AccountService accountService = getServiceHub().cordaService(KeyManagementBackedAccountService.class);

            AccountInfo recipientAccountInfo = accountService.accountInfo(whereTo).get(0).getState().getData();
            AnonymousParty recipient = subFlow(new RequestKeyForAccount(recipientAccountInfo));

            TransactionBuilder transactionBuilder = new TransactionBuilder(notary);

            MoveTokensUtilities.addMoveFungibleTokens(
                    transactionBuilder,
                    getServiceHub(),
                    ImmutableList.of(new PartyAndAmount<>(recipient, new Amount<>(amount, new EnergyTokenType()))),
                    getOurIdentity()
            );

            transactionBuilder.addCommand(new Commands.EnergyTransferCommand(),
                    getOurIdentity().getOwningKey(),
                    recipient.getOwningKey(),
                    sanctionsBody.getOwningKey());

            transactionBuilder.verify(getServiceHub());

            final SignedTransaction meSignedTx = getServiceHub().signInitialTransaction(transactionBuilder);

            // get sessions for the counterparty and the sanctions body
            // initiate flow session with the host of the recipient, since the recipient is only an account
            FlowSession recipientSession = initiateFlow(recipientAccountInfo.getHost());
            FlowSession sanctionsBodySession = initiateFlow(sanctionsBody);

            final SignedTransaction fullySignedTx = subFlow(
                    new CollectSignaturesFlow(meSignedTx, ImmutableList.of(recipientSession, sanctionsBodySession))
            );

            return subFlow(new FinalityFlow(fullySignedTx, ImmutableList.of(recipientSession, sanctionsBodySession)));
        }
    }

    @InitiatedBy(SendEnergyTokens.class)
    public class ReceiveEnergyTokens extends FlowLogic<SignedTransaction> {

        private FlowSession initiatingSession;

        public ReceiveEnergyTokens(FlowSession initiatingSession) {
            this.initiatingSession = initiatingSession;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            SignTransactionFlow signTransactionFlow = new SignTransactionFlow(initiatingSession) {
                @Override
                protected void checkTransaction(@NotNull SignedTransaction stx) throws FlowException {

                }
            };

            // keep a handle on the transaction we just signed so you can receive and log the notarized transaction later
            SecureHash txId = subFlow(signTransactionFlow).getId();

            return subFlow(new ReceiveFinalityFlow(initiatingSession, txId));
        }
    }
}
