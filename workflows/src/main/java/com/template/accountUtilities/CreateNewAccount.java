package com.template.accountUtilities;

import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;

import java.util.concurrent.ExecutionException;

public class CreateNewAccount extends FlowLogic<String> {
    private final String accountName;

    public CreateNewAccount(String name) {
        this.accountName = name;
    }

    @Override
    public String call() throws FlowException {
        StateAndRef<AccountInfo> newAccount = null;
        StateAndRef<AccountInfo> alreadyFound = getServiceHub().cordaService(KeyManagementBackedAccountService.class)
                .accountInfo(accountName).get(0);
        try {
            newAccount = getServiceHub().cordaService(KeyManagementBackedAccountService.class)
                    .createAccount(accountName).get();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return (alreadyFound == null ? newAccount : alreadyFound).getState().getData().getName()
                + " account created. UUID is: "
                + (alreadyFound == null ? newAccount : alreadyFound).getState().getData().getIdentifier();
    }
}