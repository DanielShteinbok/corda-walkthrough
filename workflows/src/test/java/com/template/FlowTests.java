package com.template;

import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.services.AccountService;
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.node.NetworkParameters;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import com.template.accountUtilities.CreateNewAccount;
import com.template.accountUtilities.ShareAccountTo;
import com.template.flows.EnergyTransferFlow;
import com.template.flows.IssueTokenFlow;
import com.template.states.EnergyTokenType;
import net.corda.testing.node.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

// WHAT WE ARE TESTING:
//  * Accounts can be created on BMW's node
//  * Account can be shared to Grid's node
//  * Grid's node can be issued tokens
//  * Grid's node can send its tokens by EnergyTransferFlow.SendEnergyTokens to BMW's node
//      * VW's node sees an increased balance of tokens
//      * Grid's node sees a decreased balance of tokens

public class FlowTests {
    private MockNetwork mockNetwork;
    private StartedMockNode volkswagen;
    private StartedMockNode grid;
    private StartedMockNode parsedata;
    private NetworkParameters testNetworkParameters =
            new NetworkParameters(4,
                    Arrays.asList(),
                    10485760,
                    (10485760 * 5),
                    Instant.now(),
                    1,
                    new LinkedHashMap<>());


    @Before
    public void setup() {
        mockNetwork = new MockNetwork(new MockNetworkParameters().withCordappsForAllNodes(ImmutableList.of(
                TestCordapp.findCordapp("com.template.contracts"),
                TestCordapp.findCordapp("com.template.flows"),
                TestCordapp.findCordapp("com.template.accountUtilities"),
                TestCordapp.findCordapp("com.r3.corda.lib.accounts.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows")
        )).withNetworkParameters(testNetworkParameters));

        volkswagen = mockNetwork.createPartyNode(new CordaX500Name("Volkswagen", "Wolfsburg", "DE"));
        grid = mockNetwork.createPartyNode(new CordaX500Name("Hydro One", "Toronto", "CA"));
        parsedata = mockNetwork.createPartyNode(new CordaX500Name("Parsedata", "Toronto", "CA"));

        volkswagen.registerInitiatedFlow(EnergyTransferFlow.ReceiveEnergyTokens.class);
        parsedata.registerInitiatedFlow(EnergyTransferFlow.ReceiveEnergyTokens.class);
        grid.registerInitiatedFlow(EnergyTransferFlow.ReceiveEnergyTokens.class);
        mockNetwork.runNetwork();
    }

    @After
    public void tearDown() {
        mockNetwork.stopNodes();
    }

    @Test
    public void AccountCreation() throws ExecutionException, InterruptedException {
        volkswagen.startFlow(new CreateNewAccount("Batmobile"));
        mockNetwork.runNetwork();
        AccountService accountService = volkswagen.getServices().cordaService(KeyManagementBackedAccountService.class);
        assert (accountService.accountInfo("Batmobile").size() != 0);
    }

    @Test
    public void AccountSharing() throws ExecutionException, InterruptedException {
        volkswagen.startFlow(new CreateNewAccount("Batmobile"));
        mockNetwork.runNetwork();

        volkswagen.startFlow(new ShareAccountTo("Batmobile",
                grid.getInfo().getLegalIdentities().get(0)));
        mockNetwork.runNetwork();

        AccountService accountService = grid.getServices().cordaService(KeyManagementBackedAccountService.class);
        assert (accountService.accountsForHost(volkswagen.getInfo().getLegalIdentities().get(0)).size() != 0);
    }

    @Test
    public void IssueTokensTest() throws ExecutionException, InterruptedException {

        IssueTokenFlow issueFlow = new IssueTokenFlow((long) 100, grid.getInfo().getLegalIdentities().get(0));

        Future<SignedTransaction> future = parsedata.startFlow(issueFlow);
        mockNetwork.runNetwork();

        // check that grid's balance matches expected 100 tokens
        StateAndRef<FungibleToken> gridTokenStateAndRef = grid.getServices().getVaultService()
                .queryBy(FungibleToken.class).getStates().stream()
                //.filter(sf->sf.getState().getData().getAmount().equals(20))
                .findAny()
                .orElseThrow(()-> new IllegalArgumentException("FungibleTokenState not found from vault"));
        assert(gridTokenStateAndRef.getState().getData().getAmount().getQuantity()==100);
    }

    @Test
    public void TokenSendTest() throws ExecutionException, InterruptedException {
        volkswagen.startFlow(new CreateNewAccount("Batmobile"));
        mockNetwork.runNetwork();

        volkswagen.startFlow(new ShareAccountTo("Batmobile",
                grid.getInfo().getLegalIdentities().get(0)));
        mockNetwork.runNetwork();

        parsedata.startFlow(new IssueTokenFlow((long) 100, grid.getInfo().getLegalIdentities().get(0)));
        mockNetwork.runNetwork();

        grid.startFlow(new EnergyTransferFlow.SendEnergyTokens(20,
                "Batmobile",
                parsedata.getInfo().getLegalIdentities().get(0)));
        mockNetwork.runNetwork();

        // not actually looking for tokens that belong *specifically* to Batmobile, since for some reason
        // queryBy doesn't adhere to the behaviour specified in the documentation
        // (it doesn't accept QueryCriteria.VaultQueryCriteria as the criteria to query by)
        /*
        AccountInfo batmobileAccount = volkswagen.getServices().cordaService(KeyManagementBackedAccountService.class)
                .accountInfo("Batmobile").get(0).getState().getData();
        QueryCriteria.VaultQueryCriteria inBatmobile = new QueryCriteria.VaultQueryCriteria()
                .withExternalIds(ImmutableList.of(batmobileAccount.getIdentifier().getId()));
         */
        StateAndRef<FungibleToken> batmobileTokens = volkswagen.getServices().getVaultService()
                .queryBy(FungibleToken.class).getStates().stream()
                //.filter(sf->sf.getState().getData().getAmount().equals(20))
                .findAny()
                .orElseThrow(()-> new IllegalArgumentException("FungibleTokenState not found from vault"));

        assert(batmobileTokens.getState().getData().getAmount().getQuantity()==20);
    }



}