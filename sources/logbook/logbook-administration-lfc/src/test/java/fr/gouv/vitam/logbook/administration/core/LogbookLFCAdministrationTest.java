package fr.gouv.vitam.logbook.administration.core;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.ws.rs.core.Response.Status;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import fr.gouv.vitam.common.database.server.elasticsearch.ElasticsearchNode;
import fr.gouv.vitam.common.exception.DatabaseException;
import fr.gouv.vitam.common.exception.VitamApplicationServerException;
import fr.gouv.vitam.common.exception.VitamClientException;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.junit.JunitHelper;
import fr.gouv.vitam.common.junit.JunitHelper.ElasticsearchTestConfiguration;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.server.application.configuration.MongoDbNode;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.logbook.common.server.LogbookConfiguration;
import fr.gouv.vitam.logbook.common.server.LogbookDbAccess;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookCollections;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookMongoDbAccessFactory;
import fr.gouv.vitam.logbook.operations.core.LogbookOperationsImpl;
import fr.gouv.vitam.processing.management.client.ProcessingManagementClient;
import fr.gouv.vitam.processing.management.client.ProcessingManagementClientFactory;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageAlreadyExistException;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;

public class LogbookLFCAdministrationTest {

    private static final String DATABASE_HOST = "localhost";
    private static final String DATABASE_NAME = "vitam-test";
    static LogbookDbAccess mongoDbAccess;
    static MongodExecutable mongodExecutable;
    static MongodProcess mongod;
    private static fr.gouv.vitam.common.junit.JunitHelper junitHelper;
    private static int port;

    private static WorkspaceClientFactory workspaceClientFactory;
    private static WorkspaceClient workspaceClient;

    private static ProcessingManagementClientFactory processingManagementClientFactory;
    private static ProcessingManagementClient processingManagementClient;

    // ES
    @ClassRule
    public static TemporaryFolder esTempFolder = new TemporaryFolder();
    private final static String ES_CLUSTER_NAME = "vitam-cluster";
    private final static String ES_HOST_NAME = "localhost";
    private static ElasticsearchTestConfiguration config = null;

    private static final Integer tenantId = 0;
    static final List<Integer> tenantList = Arrays.asList(0);

    @Rule
    public RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());

    @BeforeClass
    public static void init() throws IOException {
        workspaceClientFactory = mock(WorkspaceClientFactory.class);
        workspaceClient = mock(WorkspaceClient.class);

        processingManagementClientFactory =
            mock(ProcessingManagementClientFactory.class);
        processingManagementClient = mock(ProcessingManagementClient.class);

        given(workspaceClientFactory.getClient()).willReturn(workspaceClient);
        given(processingManagementClientFactory.getClient()).willReturn(processingManagementClient);

        final MongodStarter starter = MongodStarter.getDefaultInstance();
        junitHelper = JunitHelper.getInstance();
        port = junitHelper.findAvailablePort();
        mongodExecutable = starter.prepare(new MongodConfigBuilder()
            .withLaunchArgument("--enableMajorityReadConcern")
            .version(Version.Main.PRODUCTION)
            .net(new Net(port, Network.localhostIsIPv6()))
            .build());
        mongod = mongodExecutable.start();
        // ES
        try {
            config = JunitHelper.startElasticsearchForTest(esTempFolder, ES_CLUSTER_NAME);
        } catch (final VitamApplicationServerException e1) {
            assumeTrue(false);
        }

        List<MongoDbNode> nodes = new ArrayList<MongoDbNode>();
        nodes.add(new MongoDbNode(DATABASE_HOST, port));
        final List<ElasticsearchNode> esNodes = new ArrayList<>();
        esNodes.add(new ElasticsearchNode(ES_HOST_NAME, config.getTcpPort()));
        LogbookConfiguration logbookConfiguration =
            new LogbookConfiguration(nodes, DATABASE_NAME, ES_CLUSTER_NAME, esNodes);
        logbookConfiguration.setTenants(tenantList);
        mongoDbAccess = LogbookMongoDbAccessFactory.create(logbookConfiguration);
    }


    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        mongoDbAccess.close();
        if (config != null) {
            JunitHelper.stopElasticsearchForTest(config);
        }
        mongod.stop();
        mongodExecutable.stop();
        junitHelper.releasePort(port);
    }

    @After
    public void tearDown() throws DatabaseException {
        mongoDbAccess.deleteCollection(LogbookCollections.OPERATION);
    }


    @Test
    @RunWithCustomExecutor
    public void shouldGenerateSecure() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);

        reset(workspaceClient);
        reset(processingManagementClient);
        doNothing().when(workspaceClient).createContainer(anyString());

        doNothing().when(processingManagementClient).initVitamProcess(anyString(), anyString(), anyString());
        RequestResponseOK<ItemStatus> req = new RequestResponseOK<ItemStatus>().addResult(new ItemStatus());
        req.setHttpCode(Status.ACCEPTED.getStatusCode());
        when(processingManagementClient.updateOperationActionProcess(anyString(), anyString())).thenReturn(req);
        LogbookOperationsImpl logbookOperations = new LogbookOperationsImpl(mongoDbAccess);

        LogbookLFCAdministration logbookAdministration =
            new LogbookLFCAdministration(logbookOperations, processingManagementClientFactory,
                workspaceClientFactory);

        // When
        GUID operationGuid = logbookAdministration.generateSecureLogbookLFC();
        assertNotNull(operationGuid);

    }

    @Test
    @RunWithCustomExecutor
    public void shouldGetExceptionWhenWorkspaceIsDown() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);

        doThrow(new ContentAddressableStorageAlreadyExistException("ContentAddressableStorageAlreadyExistException"))
            .when(workspaceClient).createContainer(anyString());
        LogbookOperationsImpl logbookOperations = new LogbookOperationsImpl(mongoDbAccess);

        LogbookLFCAdministration logbookAdministration =
            new LogbookLFCAdministration(logbookOperations, processingManagementClientFactory,
                workspaceClientFactory);

        try {
            logbookAdministration.generateSecureLogbookLFC();
            fail("should throw an exception");
        } catch (VitamClientException e) {

        }

    }
}
