package com.template.accountUtilities;

import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo;
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.identity.Party;

import java.util.Arrays;
import java.util.List;

public class ShareAccountTo extends FlowLogic<String> {

    private final Party shareTo;
    private final String acctName;

    public ShareAccountTo(Party shareTo, String acctName) {
        this.shareTo = shareTo;
        this.acctName = acctName;
    }

    @Override
    public String call() throws FlowException {
        List<StateAndRef<AccountInfo>> allAccounts = getServiceHub()
                .cordaService(KeyManagementBackedAccountService.class).ourAccounts();

        StateAndRef<AccountInfo> sharedAccount = allAccounts.stream()
                .filter(it -> it.getState().getData().getName().equals(acctName))
                .findAny().orElseThrow(() -> new RuntimeException("Could not find account"));

        subFlow(new ShareAccountInfo(sharedAccount, Arrays.asList(shareTo)));
        return "Shared " + acctName + " with " + shareTo;
    }
}
