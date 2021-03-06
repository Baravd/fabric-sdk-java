/*
 *  Copyright 2016, 2017 DTCC, Fujitsu Australia Software Technology, IBM - All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *        http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.hyperledger.fabric.sdk;

//Allow throwing undeclared checked execeptions in mock code.
//CHECKSTYLE.OFF: IllegalImport

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.hyperledger.fabric.protos.common.Common;
import org.hyperledger.fabric.protos.orderer.Ab;
import org.hyperledger.fabric.protos.peer.Chaincode;
import org.hyperledger.fabric.protos.peer.FabricProposal;
import org.hyperledger.fabric.protos.peer.FabricProposalResponse;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.PeerException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.hyperledger.fabric.sdk.exception.TransactionException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric.sdk.testutils.TestUtils;
import org.hyperledger.fabric.sdk.transaction.InstallProposalBuilder;
import org.hyperledger.fabric.sdk.transaction.TransactionContext;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import sun.misc.Unsafe;

import static org.hyperledger.fabric.sdk.Channel.PeerOptions.createPeerOptions;
import static org.hyperledger.fabric.sdk.testutils.TestUtils.assertArrayListEquals;
import static org.hyperledger.fabric.sdk.testutils.TestUtils.getMockUser;
import static org.hyperledger.fabric.sdk.testutils.TestUtils.matchesRegex;
import static org.hyperledger.fabric.sdk.testutils.TestUtils.setField;
import static org.hyperledger.fabric.sdk.testutils.TestUtils.tarBytesToEntryArrayList;

//CHECKSTYLE.ON: IllegalImport

public class ChannelTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private static HFClient hfclient = null;
    private static Channel shutdownChannel = null;
    private static final String BAD_STUFF = "this is bad!";
    private static Orderer throwOrderer = null;
    private static Channel throwChannel = null;
    private static final String CHANNEL_NAME = "channel3";

    @BeforeClass
    public static void setupClient() {

        try {
            hfclient = TestHFClient.newInstance();

            shutdownChannel = new Channel("shutdown", hfclient);

            setField(shutdownChannel, "shutdown", true);

            throwOrderer = new Orderer("foo", "grpc://localhost:8", null) {

                @Override
                Ab.BroadcastResponse sendTransaction(Common.Envelope transaction) throws Exception {
                    throw new Exception(BAD_STUFF);
                }

                @Override
                Ab.DeliverResponse[] sendDeliver(Common.Envelope transaction) throws TransactionException {
                    throw new TransactionException(BAD_STUFF);
                }

            };

            throwChannel = new Channel("throw", hfclient);

            throwChannel.addOrderer(throwOrderer);

        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected Exception " + e.getMessage());

        }
    }

    @Test
    public void testChannelCreation() {

        try {

            final String channelName = "channel3";
            Channel testchannel = new Channel(channelName, hfclient);
            Assert.assertEquals(channelName, testchannel.getName());
            Assert.assertEquals(testchannel.client, hfclient);
            Assert.assertEquals(testchannel.getOrderers().size(), 0);
            Assert.assertEquals(testchannel.getPeers().size(), 0);
            Assert.assertEquals(testchannel.isInitialized(), false);

        } catch (InvalidArgumentException e) {
            Assert.fail("Unexpected exception " + e.getMessage());
        }

    }

    @Test
    public void testChannelAddPeer() throws Exception {

        final String channelName = "channel3";
        final Channel testchannel = new Channel(channelName, hfclient);
        final Peer peer = hfclient.newPeer("peer_", "grpc://localhost:7051");

        testchannel.addPeer(peer);

        Assert.assertEquals(testchannel.getPeers().size(), 1);
        Assert.assertEquals(testchannel.getPeers().iterator().next(), peer);

    }

    @Test
    public void testChannelAddOrder() throws Exception {

        final Channel testChannel = new Channel(CHANNEL_NAME, hfclient);
        final Orderer orderer = hfclient.newOrderer("testorder", "grpc://localhost:7051");

        testChannel.addOrderer(orderer);

        Assert.assertEquals(testChannel.getOrderers().size(), 1);
        Assert.assertEquals(testChannel.getOrderers().iterator().next(), orderer);

    }

    @Test
    public void testChannelNullClient() throws Exception {

        thrown.expect(InvalidArgumentException.class);
        thrown.expectMessage("Channel client is invalid can not be null.");

        new Channel(CHANNEL_NAME, null);

    }

    @Test
    public void testChannelAddNullPeer() {
        Channel testChannel = null;

        try {

            testChannel = new Channel(CHANNEL_NAME, hfclient);

            testChannel.addPeer(null);

            Assert.fail("Expected set null peer to throw exception.");

        } catch (InvalidArgumentException e) {
            Assert.assertEquals(testChannel.getPeers().size(), 0);
            Assert.assertTrue(e.getClass() == InvalidArgumentException.class);
        }

    }

    @Test
    public void testChannelAddNoNamePeer() {
        Channel testChannel = null;

        try {

            testChannel = new Channel(CHANNEL_NAME, hfclient);
            final Peer peer = hfclient.newPeer(null, "grpc://localhost:7051");

            testChannel.addPeer(peer);
            Assert.fail("Expected no named peer to throw exception.");

        } catch (Exception e) {
            Assert.assertEquals(testChannel.getPeers().size(), 0);
            Assert.assertTrue(e.getClass() == InvalidArgumentException.class);
        }

    }

    @Test
    public void testChannelAddNullOrder() {
        Channel testChannel = null;

        try {

            testChannel = new Channel(CHANNEL_NAME, hfclient);

            testChannel.addOrderer(null);

            Assert.fail("Expected set null order to throw exception.");

        } catch (InvalidArgumentException e) {
            Assert.assertEquals(testChannel.getOrderers().size(), 0);
            Assert.assertTrue(e.getClass() == InvalidArgumentException.class);
        }

    }

    @Test
    public void testChannelAddNullEventhub() {
        Channel testChannel = null;

        try {

            testChannel = new Channel(CHANNEL_NAME, hfclient);

            testChannel.addEventHub(null);

            Assert.fail("Expected set null peer to throw exception.");

        } catch (InvalidArgumentException e) {
            Assert.assertEquals(testChannel.getEventHubs().size(), 0);
            Assert.assertEquals(e.getClass(), InvalidArgumentException.class);
        }

    }

    @Test
    public void testChannelInitialize() throws Exception { //test may not be doable once initialize is done

        class MockChannel extends Channel {

            MockChannel(String name, HFClient client) throws InvalidArgumentException {
                super(name, client);
            }

            @Override
            protected void parseConfigBlock() {

            }

            @Override
            protected void loadCACertificates() {

            }
        }

        final Channel testChannel = new MockChannel(CHANNEL_NAME, hfclient);
        final Peer peer = hfclient.newPeer("peer_", "grpc://localhost:7051");

        testChannel.addPeer(peer, createPeerOptions().setPeerRoles(Peer.PeerRole.NO_EVENT_SOURCE));
        Assert.assertFalse(testChannel.isInitialized());
        testChannel.initialize();
        Assert.assertTrue(testChannel.isInitialized());

    }
//     Allow no peers
//    @Test
//    public void testChannelInitializeNoPeer() {
//        Channel testChannel = null;
//
//        try {
//
//            testChannel = new Channel(CHANNEL_NAME, hfclient);
//
//            Assert.assertEquals(testChannel.isInitialized(), false);
//            testChannel.initialize();
//            Assert.fail("Expected initialize to throw exception with no peers.");
//
//        } catch (Exception e) {
//
//            Assert.assertTrue(e.getClass() == InvalidArgumentException.class);
//            Assert.assertFalse(testChannel.isInitialized());
//        }
//
//    }

    //Shutdown channel tests

    @Test
    public void testChannelShutdown() {

        try {

            Assert.assertTrue(shutdownChannel.isShutdown());

        } catch (Exception e) {

            Assert.assertTrue(e.getClass() == InvalidArgumentException.class);
            Assert.assertTrue(shutdownChannel.isInitialized());
        }

    }

    @Test
    public void testChannelShutdownAddPeer() throws Exception {

        thrown.expect(InvalidArgumentException.class);
        thrown.expectMessage("Channel shutdown has been shutdown.");

        Assert.assertTrue(shutdownChannel.isShutdown());
        shutdownChannel.addPeer(hfclient.newPeer("name", "grpc://myurl:90"));

    }

    @Test
    public void testChannelShutdownAddOrderer() throws Exception {

        thrown.expect(InvalidArgumentException.class);
        thrown.expectMessage("Channel shutdown has been shutdown.");

        Assert.assertTrue(shutdownChannel.isShutdown());
        shutdownChannel.addOrderer(hfclient.newOrderer("name", "grpc://myurl:90"));

    }

    @Test
    public void testChannelShutdownAddEventHub() throws Exception {

        thrown.expect(InvalidArgumentException.class);
        thrown.expectMessage("Channel shutdown has been shutdown.");

        Assert.assertTrue(shutdownChannel.isShutdown());
        shutdownChannel.addEventHub(hfclient.newEventHub("name", "grpc://myurl:90"));

    }

    @Test
    public void testChannelShutdownJoinPeer() throws Exception {

        thrown.expect(ProposalException.class);
        thrown.expectMessage("Channel shutdown has been shutdown.");

        Assert.assertTrue(shutdownChannel.isShutdown());
        shutdownChannel.joinPeer(hfclient.newPeer("name", "grpc://myurl:90"));

    }

    @Test
    public void testChannelShutdownInitialize() throws Exception {

        thrown.expect(InvalidArgumentException.class);
        thrown.expectMessage("Channel shutdown has been shutdown.");

        shutdownChannel.initialize();

    }

    @Test
    public void testChannelShutdownInstiateProposal() throws Exception {

        thrown.expect(InvalidArgumentException.class);
        thrown.expectMessage("Channel shutdown has been shutdown.");

        Assert.assertTrue(shutdownChannel.isShutdown());
        shutdownChannel.sendInstantiationProposal(hfclient.newInstantiationProposalRequest());

    }

    @Test
    public void testChannelShutdownQueryTransactionByIDl() throws Exception {

        thrown.expect(InvalidArgumentException.class);

        thrown.expectMessage("Channel shutdown has been shutdown.");

        Assert.assertTrue(shutdownChannel.isShutdown());
        shutdownChannel.queryBlockByHash(new byte[] {});

    }

    @Test
    public void testChannelBadOrderer() throws Exception {
        thrown.expect(java.util.concurrent.ExecutionException.class);
        thrown.expectMessage("Channel shutdown has been shutdown.");

        CompletableFuture<BlockEvent.TransactionEvent> future = shutdownChannel.sendTransaction(null);
        future.get();

    }

    @Test
    public void testChannelBadPeerNull() throws Exception {

        thrown.expect(InvalidArgumentException.class);
        thrown.expectMessage("Peer value is null.");

        final Channel channel = createRunningChannel(null);
        channel.queryBlockByHash(null, "rick".getBytes());
    }

    @Test
    public void testChannelBadPeerDoesNotBelong() throws Exception {

        thrown.expect(InvalidArgumentException.class);
        thrown.expectMessage("Channel channel does not have peer peer2");

        final Channel channel = createRunningChannel(null);

        Collection<Peer> peers = Arrays.asList((Peer[]) new Peer[] {hfclient.newPeer("peer2", "grpc://localhost:22")});

        createRunningChannel("testChannelBadPeerDoesNotBelong", peers);

        channel.sendInstantiationProposal(hfclient.newInstantiationProposalRequest(), peers);

    }

    @Test
    public void testChannelBadPeerDoesNotBelong2() throws Exception {

        thrown.expect(InvalidArgumentException.class);
        thrown.expectMessage("Peer peer1 not set for channel channel");

        final Channel channel = createRunningChannel(null);

        Peer peer = channel.getPeers().iterator().next();

        final Channel channel2 = createRunningChannel("testChannelBadPeerDoesNotBelong2", null);

        setField(peer, "channel", channel2);

        channel.sendInstantiationProposal(hfclient.newInstantiationProposalRequest());

    }

    @Test
    public void testChannelBadPeerCollection() throws Exception {

        thrown.expect(InvalidArgumentException.class);
        thrown.expectMessage("Peer value is null.");

        final Channel channel = createRunningChannel(null);

        channel.queryByChaincode(hfclient.newQueryProposalRequest(),
                Arrays.asList((Peer[]) new Peer[] {null}));

    }

    @Test
    public void testChannelBadPeerCollectionEmpty() throws Exception {

        thrown.expect(InvalidArgumentException.class);
        thrown.expectMessage("Collection of peers is empty.");

        final Channel channel = createRunningChannel(null);

        channel.sendUpgradeProposal(hfclient.newUpgradeProposalRequest(),
                Arrays.asList((Peer[]) new Peer[] {})
        );

    }

    @Test
    public void testChannelBadPeerCollectionNull() throws Exception {

        thrown.expect(InvalidArgumentException.class);
        thrown.expectMessage("Collection of peers is null.");

        final Channel channel = createRunningChannel(null);

        channel.sendTransactionProposal(hfclient.newTransactionProposalRequest(), null);

    }

    @Test
    public void testTwoChannelsSameName() throws Exception {

        thrown.expect(InvalidArgumentException.class);
        thrown.expectMessage("Channel by the name testTwoChannelsSameName already exists");

        createRunningChannel("testTwoChannelsSameName", null);
        createRunningChannel("testTwoChannelsSameName", null);

    }

    static final String CHANNEL_NAME2 = "channel";

    public static Channel createRunningChannel(Collection<Peer> peers) throws InvalidArgumentException, NoSuchFieldException, IllegalAccessException {
        Channel prevChannel = hfclient.getChannel(CHANNEL_NAME2);
        if (null != prevChannel) { //cleanup remove default channel.
            prevChannel.shutdown(false);
        }
        return createRunningChannel(CHANNEL_NAME2, peers);
    }

    public static Channel createRunningChannel(String channelName, Collection<Peer> peers) throws InvalidArgumentException, NoSuchFieldException, IllegalAccessException {

        Channel channel = hfclient.newChannel(channelName);
        if (peers == null) {
            Peer peer = hfclient.newPeer("peer1", "grpc://localhost:22");
            channel.addPeer(peer);
        } else {
            for (Peer peer : peers) {
                channel.addPeer(peer);

            }
        }

        setField(channel, "initialized", true);

        return channel;

    }

    @Test
    public void testChannelBadPeerDoesNotBelongJoin() throws Exception {

        thrown.expect(ProposalException.class);
        thrown.expectMessage("Can not add peer peer2 to channel testChannelBadPeerDoesNotBelongJoin because it already belongs to channel testChannelBadPeerDoesNotBelongJoin2");

        final Channel channel = createRunningChannel("testChannelBadPeerDoesNotBelongJoin", null);

        Collection<Peer> peers = Arrays.asList((Peer[]) new Peer[] {hfclient.newPeer("peer2", "grpc://localhost:22")});

        createRunningChannel("testChannelBadPeerDoesNotBelongJoin2", peers);

        //Peer joining channel when it belongs to another channel.

        channel.joinPeer(peers.iterator().next());

    }

    @Test
    public void testChannelPeerJoinNoOrderer() throws Exception {

        thrown.expect(ProposalException.class);
        thrown.expectMessage("Channel channel does not have any orderers associated with it.");

        final Channel channel = createRunningChannel(null);

        //Peer joining channel were no orderer is there .. not likely.

        channel.joinPeer(hfclient.newPeer("peerJoiningNOT", "grpc://localhost:22"));

    }

    @Test
    public void testChannelInitNoname() throws Exception {

        thrown.expect(InvalidArgumentException.class);
        thrown.expectMessage("Can not initialize channel without a valid name.");

        final Channel channel = hfclient.newChannel("del");
        setField(channel, "name", null);

        channel.initialize();

    }

    @Test
    public void testChannelInitNullClient() throws Exception {

        thrown.expect(InvalidArgumentException.class);
        thrown.expectMessage("Can not initialize channel without a client object.");

        final Channel channel = hfclient.newChannel("testChannelInitNullClient");
        setField(channel, "client", null);

        channel.initialize();

    }

    @Test
    public void testChannelsendInstantiationProposalNull() throws Exception {

        thrown.expect(InvalidArgumentException.class);
        thrown.expectMessage("InstantiateProposalRequest is null");

        final Channel channel = createRunningChannel(null);

        channel.sendInstantiationProposal(null);

    }

    @Test
    public void testChannelsendInstallProposalNull() throws Exception {

        thrown.expect(InvalidArgumentException.class);
        thrown.expectMessage("InstallProposalRequest is null");

        final Channel channel = createRunningChannel(null);

        channel.sendInstallProposal(null);

    }

    @Test
    public void testChannelsendUpgradeProposalNull() throws Exception {

        thrown.expect(InvalidArgumentException.class);
        thrown.expectMessage("Upgradeproposal is null");

        final Channel channel = createRunningChannel(null);

        channel.sendUpgradeProposal(null);

    }

    //queryBlockByHash

    @Test
    public void testChannelQueryBlockByHashNull() throws Exception {

        thrown.expect(InvalidArgumentException.class);
        thrown.expectMessage("blockHash parameter is null.");

        final Channel channel = createRunningChannel(null);

        channel.queryBlockByHash(null);

    }

    @Test
    public void testChannelQueryBlockByHashNotInitialized() throws Exception {

        thrown.expect(InvalidArgumentException.class);
        thrown.expectMessage("Channel channel has not been initialized.");

        final Channel channel = createRunningChannel(null);
        setField(channel, "initialized", false);

        channel.queryBlockByHash("hyper this hyper that".getBytes());

    }

    @Test
    public void testChannelQueryBlockByTransactionIDNull() throws Exception {

        thrown.expect(InvalidArgumentException.class);
        thrown.expectMessage("TxID parameter is null.");

        final Channel channel = createRunningChannel(null);

        channel.queryBlockByTransactionID(null);

    }

    @Test
    public void testChannelQueryTransactionByIDNull() throws Exception {

        thrown.expect(InvalidArgumentException.class);
        thrown.expectMessage("TxID parameter is null.");

        final Channel channel = createRunningChannel(null);

        channel.queryTransactionByID(null);

    }

    @Test
    public void testQueryInstalledChaincodesThrowInterrupted() throws Exception {

        thrown.expect(ProposalException.class);
        thrown.expectMessage("You interrupting me?");

        final Channel channel = createRunningChannel(null);
        Peer peer = channel.getPeers().iterator().next();

        setField(peer, "endorserClent", new MockEndorserClient(new InterruptedException("You interrupting me?")));

        hfclient.queryChannels(peer);

    }

    @Test
    public void testQueryInstalledChaincodesThrowPeerException() throws Exception {

        thrown.expect(ProposalException.class);
        thrown.expectMessage("rick did this:)");

        final Channel channel = createRunningChannel(null);
        Peer peer = channel.getPeers().iterator().next();

        setField(peer, "endorserClent", new MockEndorserClient(new PeerException("rick did this:)")));

        hfclient.queryChannels(peer);

    }

    @Test
    public void testQueryInstalledChaincodesThrowTimeoutException() throws Exception {

        thrown.expect(ProposalException.class);
        thrown.expectMessage("What time is it?");

        final Channel channel = createRunningChannel(null);
        Peer peer = channel.getPeers().iterator().next();

        setField(peer, "endorserClent", new MockEndorserClient(new PeerException("What time is it?")));

        hfclient.queryChannels(peer);

    }

    @Test
    public void testQueryInstalledChaincodesERROR() throws Exception {

        thrown.expect(Error.class);
        thrown.expectMessage("Error bad bad bad");

        final Channel channel = createRunningChannel(null);
        Peer peer = channel.getPeers().iterator().next();

        final SettableFuture<FabricProposalResponse.ProposalResponse> settableFuture = SettableFuture.create();
        settableFuture.setException(new Error("Error bad bad bad"));
        setField(peer, "endorserClent", new MockEndorserClient(settableFuture));

        hfclient.queryChannels(peer);

    }

    @Test
    public void testQueryInstalledChaincodesStatusRuntimeException() throws Exception {

        thrown.expect(ProposalException.class);
        thrown.expectMessage("ABORTED");

        final Channel channel = createRunningChannel(null);
        Peer peer = channel.getPeers().iterator().next();

        final SettableFuture<FabricProposalResponse.ProposalResponse> settableFuture = SettableFuture.create();
        settableFuture.setException(new StatusRuntimeException(Status.ABORTED));
        setField(peer, "endorserClent", new MockEndorserClient(settableFuture));

        hfclient.queryChannels(peer);

    }

    private static final String SAMPLE_GO_CC = "src/test/fixture/sdkintegration/gocc/sample1";

    @Test
    public void testProposalBuilderWithMetaInf() throws Exception {
        InstallProposalBuilder installProposalBuilder = InstallProposalBuilder.newBuilder();

        installProposalBuilder.setChaincodeLanguage(TransactionRequest.Type.GO_LANG);
        installProposalBuilder.chaincodePath("github.com/example_cc");
        installProposalBuilder.setChaincodeSource(new File(SAMPLE_GO_CC));
        installProposalBuilder.chaincodeName("example_cc.go");
        installProposalBuilder.setChaincodeMetaInfLocation(new File("src/test/fixture/meta-infs/test1"));
        installProposalBuilder.chaincodeVersion("1");

        Channel channel = hfclient.newChannel("testProposalBuilderWithMetaInf");

        TestUtils.MockUser mockUser = getMockUser("rick", "rickORG");
        TransactionContext transactionContext = new TransactionContext(channel, mockUser, CryptoSuite.Factory.getCryptoSuite());

        installProposalBuilder.context(transactionContext);

        FabricProposal.Proposal proposal = installProposalBuilder.build(); // Build it get the proposal. Then unpack it to see if it's what we expect.

        FabricProposal.ChaincodeProposalPayload chaincodeProposalPayload = FabricProposal.ChaincodeProposalPayload.parseFrom(proposal.getPayload());
        Chaincode.ChaincodeInvocationSpec chaincodeInvocationSpec = Chaincode.ChaincodeInvocationSpec.parseFrom(chaincodeProposalPayload.getInput());
        Chaincode.ChaincodeSpec chaincodeSpec = chaincodeInvocationSpec.getChaincodeSpec();
        Chaincode.ChaincodeInput input = chaincodeSpec.getInput();

        Chaincode.ChaincodeDeploymentSpec chaincodeDeploymentSpec = Chaincode.ChaincodeDeploymentSpec.parseFrom(input.getArgs(1));
        ByteString codePackage = chaincodeDeploymentSpec.getCodePackage();
        ArrayList tarBytesToEntryArrayList = tarBytesToEntryArrayList(codePackage.toByteArray());

        ArrayList<String> expect = new ArrayList(Arrays.asList(new String[] {
                "META-INF/statedb/couchdb/indexes/MockFakeIndex.json",
                "src/github.com/example_cc/example_cc.go"
        }));

        assertArrayListEquals("Tar in Install Proposal's codePackage does not have expected entries. ", expect, tarBytesToEntryArrayList);
    }

    @Test
    public void testProposalBuilderWithOutMetaInf() throws Exception {
        InstallProposalBuilder installProposalBuilder = InstallProposalBuilder.newBuilder();

        installProposalBuilder.setChaincodeLanguage(TransactionRequest.Type.GO_LANG);
        installProposalBuilder.chaincodePath("github.com/example_cc");
        installProposalBuilder.setChaincodeSource(new File(SAMPLE_GO_CC));
        installProposalBuilder.chaincodeName("example_cc.go");
        installProposalBuilder.chaincodeVersion("1");

        Channel channel = hfclient.newChannel("testProposalBuilderWithOutMetaInf");
        TransactionContext transactionContext = new TransactionContext(channel, getMockUser("rick", "rickORG"), CryptoSuite.Factory.getCryptoSuite());

        installProposalBuilder.context(transactionContext);

        FabricProposal.Proposal proposal = installProposalBuilder.build(); // Build it get the proposal. Then unpack it to see if it's what we expect.
        FabricProposal.ChaincodeProposalPayload chaincodeProposalPayload = FabricProposal.ChaincodeProposalPayload.parseFrom(proposal.getPayload());
        Chaincode.ChaincodeInvocationSpec chaincodeInvocationSpec = Chaincode.ChaincodeInvocationSpec.parseFrom(chaincodeProposalPayload.getInput());
        Chaincode.ChaincodeSpec chaincodeSpec = chaincodeInvocationSpec.getChaincodeSpec();
        Chaincode.ChaincodeInput input = chaincodeSpec.getInput();

        Chaincode.ChaincodeDeploymentSpec chaincodeDeploymentSpec = Chaincode.ChaincodeDeploymentSpec.parseFrom(input.getArgs(1));
        ByteString codePackage = chaincodeDeploymentSpec.getCodePackage();
        ArrayList tarBytesToEntryArrayList = tarBytesToEntryArrayList(codePackage.toByteArray());

        ArrayList<String> expect = new ArrayList(Arrays.asList(new String[] {"src/github.com/example_cc/example_cc.go"
        }));

        assertArrayListEquals("Tar in Install Proposal's codePackage does not have expected entries. ", expect, tarBytesToEntryArrayList);
    }

    @Test
    public void testProposalBuilderWithNoMetaInfDir() throws Exception {

        thrown.expect(java.lang.IllegalArgumentException.class);
        thrown.expectMessage(matchesRegex("The META-INF directory does not exist in.*src/test/fixture/meta-infs/test1/META-INF"));

        InstallProposalBuilder installProposalBuilder = InstallProposalBuilder.newBuilder();

        installProposalBuilder.setChaincodeLanguage(TransactionRequest.Type.GO_LANG);
        installProposalBuilder.chaincodePath("github.com/example_cc");
        installProposalBuilder.setChaincodeSource(new File(SAMPLE_GO_CC));
        installProposalBuilder.chaincodeName("example_cc.go");
        installProposalBuilder.chaincodeVersion("1");
        installProposalBuilder.setChaincodeMetaInfLocation(new File("src/test/fixture/meta-infs/test1/META-INF")); // points into which is not what's expected.

        Channel channel = hfclient.newChannel("testProposalBuilderWithNoMetaInfDir");
        TransactionContext transactionContext = new TransactionContext(channel, getMockUser("rick", "rickORG"), CryptoSuite.Factory.getCryptoSuite());

        installProposalBuilder.context(transactionContext);

        installProposalBuilder.build(); // Build it get the proposal. Then unpack it to see if it's what we epect.
    }

    @Test
    public void testProposalBuilderWithMetaInfExistsNOT() throws Exception {

        thrown.expect(java.lang.IllegalArgumentException.class);
        thrown.expectMessage("Directory to find chaincode META-INF /tmp/fdsjfksfj/fjksfjskd/fjskfjdsk/should never exist does not exist");

        InstallProposalBuilder installProposalBuilder = InstallProposalBuilder.newBuilder();

        installProposalBuilder.setChaincodeLanguage(TransactionRequest.Type.GO_LANG);
        installProposalBuilder.chaincodePath("github.com/example_cc");
        installProposalBuilder.setChaincodeSource(new File(SAMPLE_GO_CC));
        installProposalBuilder.chaincodeName("example_cc.go");
        installProposalBuilder.chaincodeVersion("1");
        installProposalBuilder.setChaincodeMetaInfLocation(new File("/tmp/fdsjfksfj/fjksfjskd/fjskfjdsk/should never exist")); // points into which is not what's expected.

        Channel channel = hfclient.newChannel("testProposalBuilderWithMetaInfExistsNOT");
        TransactionContext transactionContext = new TransactionContext(channel, getMockUser("rick", "rickORG"), CryptoSuite.Factory.getCryptoSuite());

        installProposalBuilder.context(transactionContext);

        installProposalBuilder.build(); // Build it get the proposal. Then unpack it to see if it's what we epect.
    }

    @Test
    public void testProposalBuilderWithMetaInfEmpty() throws Exception {

        thrown.expect(java.lang.IllegalArgumentException.class);
        thrown.expectMessage(matchesRegex("The META-INF directory.*/src/test/fixture/meta-infs/emptyMetaInf/META-INF is empty."));

        File emptyINF = new File("src/test/fixture/meta-infs/emptyMetaInf/META-INF"); // make it cause git won't check in empty directory
        if (!emptyINF.exists()) {
            emptyINF.mkdirs();
            emptyINF.deleteOnExit();
        }

        InstallProposalBuilder installProposalBuilder = InstallProposalBuilder.newBuilder();

        installProposalBuilder.setChaincodeLanguage(TransactionRequest.Type.GO_LANG);
        installProposalBuilder.chaincodePath("github.com/example_cc");
        installProposalBuilder.setChaincodeSource(new File(SAMPLE_GO_CC));
        installProposalBuilder.chaincodeName("example_cc.go");
        installProposalBuilder.chaincodeVersion("1");
        installProposalBuilder.setChaincodeMetaInfLocation(new File("src/test/fixture/meta-infs/emptyMetaInf")); // points into which is not what's expected.

        Channel channel = hfclient.newChannel("testProposalBuilderWithMetaInfEmpty");
        TransactionContext transactionContext = new TransactionContext(channel, getMockUser("rick", "rickORG"), CryptoSuite.Factory.getCryptoSuite());

        installProposalBuilder.context(transactionContext);

        FabricProposal.Proposal proposal = installProposalBuilder.build(); // Build it get the proposal. Then unpack it to see if it's what we epect.
    }

    class MockEndorserClient extends EndorserClient {
        final Throwable throwThis;
        private final ListenableFuture<FabricProposalResponse.ProposalResponse> returnedFuture;

        MockEndorserClient(Throwable throwThis) {
            super(new Endpoint("grpc://loclhost:99", null).getChannelBuilder());
            if (throwThis == null) {
                throw new IllegalArgumentException("Can't throw a null!");
            }
            this.throwThis = throwThis;
            this.returnedFuture = null;
        }

        MockEndorserClient(ListenableFuture<FabricProposalResponse.ProposalResponse> returnedFuture) {
            super(new Endpoint("grpc://loclhost:99", null).getChannelBuilder());
            this.throwThis = null;
            this.returnedFuture = returnedFuture;
        }

        @Override
        public ListenableFuture<FabricProposalResponse.ProposalResponse> sendProposalAsync(FabricProposal.SignedProposal proposal) throws PeerException {
            if (throwThis != null) {
                getUnsafe().throwException(throwThis);
            }
            return returnedFuture;

        }

        @Override
        public boolean isChannelActive() {

            return true;

        }

        private Unsafe getUnsafe() {  //lets us throw undeclared exceptions.
            try {
                Field field = Unsafe.class.getDeclaredField("theUnsafe");
                field.setAccessible(true);
                return (Unsafe) field.get(null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

}
