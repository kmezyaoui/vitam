package fr.gouv.vitam.worker.core.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.gouv.vitam.common.LocalDateUtil;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.database.builder.query.QueryHelper;
import fr.gouv.vitam.common.database.builder.request.multiple.SelectMultiQuery;
import fr.gouv.vitam.common.database.parser.request.single.SelectParserSingle;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.LifeCycleStatusCode;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.logbook.common.exception.LogbookClientException;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCyclesClient;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCyclesClientFactory;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import fr.gouv.vitam.metadata.api.exception.MetaDataExecutionException;
import fr.gouv.vitam.metadata.client.MetaDataClient;
import fr.gouv.vitam.metadata.client.MetaDataClientFactory;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.processing.common.parameter.WorkerParametersFactory;
import fr.gouv.vitam.storage.engine.client.StorageClient;
import fr.gouv.vitam.storage.engine.client.StorageClientFactory;
import fr.gouv.vitam.storage.engine.client.exception.StorageAlreadyExistsClientException;
import fr.gouv.vitam.storage.engine.client.exception.StorageNotFoundClientException;
import fr.gouv.vitam.storage.engine.common.model.DataCategory;
import fr.gouv.vitam.storage.engine.common.model.response.StoredInfoResult;
import fr.gouv.vitam.worker.core.impl.HandlerIOImpl;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;
import org.assertj.core.util.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.net.ssl.*")
@PrepareForTest({WorkspaceClientFactory.class, MetaDataClientFactory.class, StorageClientFactory.class, LogbookLifeCyclesClientFactory.class})
public class StoreMetaDataUnitActionPluginTest {

    private static final String METDATA_UNIT_RESPONSE_JSON = "storeMetadataUnitPlugin/metdataUnitResponse.json";
    private static final String LFC_UNIT_RESPONSE_JSON = "storeMetadataUnitPlugin/LFCUnitResponse.json";
    private static final String CONTAINER_NAME = "aebaaaaaaaag3r7cabf4aak2izdlnwiaaaaq";
    private static final String UNIT_GUID = "aeaqaaaaaaag3r7cabf4aak2izdloiiaaaaq";
    private static final String UNIT = "storeMetadataUnitPlugin/aeaqaaaaaaag3r7cabf4aak2izdloiiaaaaq.json";
    private static final String OB_ID = "obId";

    private WorkspaceClient workspaceClient;
    private WorkspaceClientFactory workspaceClientFactory;
    private MetaDataClient metadataClient;
    private LogbookLifeCyclesClient logbookClient;
    private StorageClient storageClient;
    private HandlerIOImpl action;
    private StorageClientFactory storageClientFactory;
    
    private final InputStream unit;
    private final JsonNode unitResponse;
    private final JsonNode lfcResponse;

    private StoreMetaDataUnitActionPlugin plugin;

    public StoreMetaDataUnitActionPluginTest() throws FileNotFoundException, InvalidParseOperationException {
        unit = PropertiesUtils.getResourceAsStream(UNIT);
        File mdFile = PropertiesUtils.getResourceFile(METDATA_UNIT_RESPONSE_JSON);
        unitResponse = JsonHandler.getFromFile(mdFile);
        File lfcFile = PropertiesUtils.getResourceFile(LFC_UNIT_RESPONSE_JSON);
        lfcResponse = JsonHandler.getFromFile(lfcFile);
    }


    @Before
    public void setUp() throws Exception {
        LogbookOperationsClientFactory.changeMode(null);
        LogbookLifeCyclesClientFactory.changeMode(null);
        // clients
        workspaceClient = mock(WorkspaceClient.class);
        metadataClient = mock(MetaDataClient.class);
        storageClient = mock(StorageClient.class);
        logbookClient = mock(LogbookLifeCyclesClient.class);
        // static factories
        PowerMockito.mockStatic(WorkspaceClientFactory.class);
        PowerMockito.mockStatic(MetaDataClientFactory.class);
        PowerMockito.mockStatic(StorageClientFactory.class);
        PowerMockito.mockStatic(LogbookLifeCyclesClientFactory.class);
        // workspace client
        workspaceClientFactory = mock(WorkspaceClientFactory.class);
        PowerMockito.when(WorkspaceClientFactory.getInstance()).thenReturn(workspaceClientFactory);
        PowerMockito.when(WorkspaceClientFactory.getInstance().getClient()).thenReturn(workspaceClient);
        // metadata client
        final MetaDataClientFactory mockedMetadataFactory = mock(MetaDataClientFactory.class);
        PowerMockito.when(MetaDataClientFactory.getInstance()).thenReturn(mockedMetadataFactory);
        PowerMockito.when(mockedMetadataFactory.getClient()).thenReturn(metadataClient);
        // logbookClient
        final LogbookLifeCyclesClientFactory logbookLifeCyclesClientFactory = mock(LogbookLifeCyclesClientFactory.class);
        PowerMockito.when(LogbookLifeCyclesClientFactory.getInstance()).thenReturn(logbookLifeCyclesClientFactory);
        PowerMockito.when(logbookLifeCyclesClientFactory.getClient()).thenReturn(logbookClient);
        // storage client
        storageClientFactory = PowerMockito.mock(StorageClientFactory.class);
        when(storageClientFactory.getClient()).thenReturn(storageClient);
        when(StorageClientFactory.getInstance()).thenReturn(storageClientFactory);

        action = new HandlerIOImpl(CONTAINER_NAME, "workerId");
    }

    @After
    public void clean() {
        action.partialClose();
    }



    @Test
    public void givenMetadataClientWhensearchUnirThenReturnNull() throws Exception {
        final WorkerParameters params =
            WorkerParametersFactory.newWorkerParameters().setWorkerGUID(GUIDFactory
                .newGUID()).setContainerName(CONTAINER_NAME).setUrlMetadata("http://localhost:8083")
                .setUrlWorkspace("http://localhost:8083")
                .setObjectNameList(Lists.newArrayList(UNIT_GUID + ".json"))
                .setObjectName(UNIT_GUID + ".json").setCurrentStep("Store unit");

        plugin = new StoreMetaDataUnitActionPlugin();

        final ItemStatus response = plugin.execute(params, action);
        assertEquals(StatusCode.KO, response.getGlobalStatus());
    }

    @Test
    public void givenMetadataClientAndLogbookLifeCycleClientAndWorkspaceResponsesWhenSearchUnitThenReturnOK() throws Exception {
        final WorkerParameters params =
            WorkerParametersFactory.newWorkerParameters().setWorkerGUID(GUIDFactory
                .newGUID()).setContainerName(CONTAINER_NAME).setUrlMetadata("http://localhost:8083")
                .setUrlWorkspace("http://localhost:8083")
                .setObjectNameList(Lists.newArrayList(UNIT_GUID + ".json"))
                .setObjectName(UNIT_GUID + ".json").setCurrentStep("Store unit");

        SelectMultiQuery query = new SelectMultiQuery();
        ObjectNode constructQuery = query.getFinalSelect();
        when(metadataClient.selectUnitbyId(constructQuery, UNIT_GUID))
            .thenReturn(unitResponse);

        SelectParserSingle parser = new SelectParserSingle();
        parser.parse(constructQuery);
        parser.addCondition(QueryHelper.eq(OB_ID, UNIT_GUID));
        constructQuery = parser.getRequest().getFinalSelect();
        when(logbookClient.selectUnitLifeCycleById(UNIT_GUID, constructQuery, LifeCycleStatusCode.LIFE_CYCLE_IN_PROCESS))
                .thenReturn(lfcResponse);

        when(workspaceClient.getObject(CONTAINER_NAME,
            DataCategory.UNIT.name() + "/" + params.getObjectName()))
            .thenReturn(Response.status(Status.OK).entity(unit).build());

        when(storageClient.storeFileFromWorkspace(anyObject(), anyObject(), anyObject(), anyObject()))
                .thenReturn(getStoredInfoResult());

        plugin = new StoreMetaDataUnitActionPlugin();

        final ItemStatus response = plugin.execute(params, action);
        assertEquals(StatusCode.OK, response.getGlobalStatus());
    }

    private StoredInfoResult getStoredInfoResult() {
        StoredInfoResult result = new StoredInfoResult();
        result.setNbCopy(1).setCreationTime(LocalDateUtil.now().toString()).setId("id")
                .setLastAccessTime(LocalDateUtil.now().toString()).setLastModifiedTime(LocalDateUtil.now().toString())
                .setObjectGroupId("id").setOfferIds(Arrays.asList("id1")).setStrategy("default");
        return result;
    }
    
    @Test 
    public void givenMetadataClientAndLogbookLifeCycleClientWhenSearchUnitWithLFCThenReturnOK() throws Exception {
        SelectMultiQuery query = new SelectMultiQuery();
        ObjectNode constructQuery = query.getFinalSelect();
        when(metadataClient.selectUnitbyId(constructQuery, UNIT_GUID))
                .thenReturn(unitResponse);

        SelectParserSingle parser = new SelectParserSingle();
        parser.parse(constructQuery);
        parser.addCondition(QueryHelper.eq(OB_ID, UNIT_GUID));
        constructQuery = parser.getRequest().getFinalSelect();
        when(logbookClient.selectUnitLifeCycleById(UNIT_GUID, constructQuery, LifeCycleStatusCode.LIFE_CYCLE_IN_PROCESS))
                .thenReturn(lfcResponse);

        plugin = new StoreMetaDataUnitActionPlugin();

        // select unit
        JsonNode unit = plugin.selectMetadataDocumentById(UNIT_GUID, DataCategory.UNIT, metadataClient);
        assertNotNull(unit);
        assertEquals(unit.get("#id").asText(), UNIT_GUID);
        
        // select lfc
        JsonNode lfc = plugin.retrieveLogbookLifeCycleById(UNIT_GUID, DataCategory.UNIT, logbookClient);
        assertNotNull(lfc);
        assertEquals(lfc.get("_id").asText(), UNIT_GUID);
        
        // aggregate unit with lfc
        JsonNode docWithLfc = plugin.getDocumentWithLFC(unit, lfc, DataCategory.UNIT);
        assertNotNull(docWithLfc);
        assertNotNull(docWithLfc.get("unit"));
        assertNotNull(docWithLfc.get("lfc"));
        
        // check aggregation
        JsonNode aggregatedUnit = docWithLfc.get("unit");
        JsonNode aggregatedLfc = docWithLfc.get("lfc");
        assertEquals(unit, aggregatedUnit);
        assertEquals(lfc, aggregatedLfc);
    } 

    @Test
    public void givenMetadataClientAndLogbookLifeCycleClientWhensearchUnitThenThrowsException() throws Exception {
        final WorkerParameters params =
            WorkerParametersFactory.newWorkerParameters().setWorkerGUID(GUIDFactory
                .newGUID()).setContainerName(CONTAINER_NAME).setUrlMetadata("http://localhost:8083")
                .setUrlWorkspace("http://localhost:8083")
                .setObjectNameList(Lists.newArrayList(UNIT_GUID + ".json"))
                .setObjectName(UNIT_GUID + ".json").setCurrentStep("Store unit");

        Mockito.doThrow(new MetaDataExecutionException("Error Metadata")).when(metadataClient)
            .selectUnitbyId(anyObject(), anyObject());

        when(logbookClient.selectUnitLifeCycleById(anyObject(), anyObject(), eq(LifeCycleStatusCode.LIFE_CYCLE_IN_PROCESS)))
                .thenReturn(lfcResponse);
        
        plugin = new StoreMetaDataUnitActionPlugin();

        final ItemStatus response = plugin.execute(params, action);
        assertEquals(StatusCode.FATAL, response.getGlobalStatus());
    }

    @Test
    public void givenMetadataClientWhensearchUnitThenThrowsException() throws Exception {
        final WorkerParameters params =
            WorkerParametersFactory.newWorkerParameters().setWorkerGUID(GUIDFactory
                .newGUID()).setContainerName(CONTAINER_NAME).setUrlMetadata("http://localhost:8083")
                .setUrlWorkspace("http://localhost:8083")
                .setObjectNameList(Lists.newArrayList(UNIT_GUID + ".json"))
                .setObjectName(UNIT_GUID + ".json").setCurrentStep("Store unit");

        Mockito.doThrow(new MetaDataExecutionException("Error Metadata")).when(metadataClient)
            .selectUnitbyId(anyObject(), anyObject());

        when(logbookClient.selectUnitLifeCycleById(anyObject(), anyObject(), eq(LifeCycleStatusCode.LIFE_CYCLE_IN_PROCESS)))
                .thenReturn(lfcResponse);
        
        plugin = new StoreMetaDataUnitActionPlugin();

        final ItemStatus response = plugin.execute(params, action);
        assertEquals(StatusCode.FATAL, response.getGlobalStatus());
    }

    @Test
    public void givenLogbookLifeCycleClientWhenSearchLfcThenThrowsException() throws Exception {
        final WorkerParameters params =
            WorkerParametersFactory.newWorkerParameters().setWorkerGUID(GUIDFactory
                .newGUID()).setContainerName(CONTAINER_NAME).setUrlMetadata("http://localhost:8083")
                .setUrlWorkspace("http://localhost:8083")
                .setObjectNameList(Lists.newArrayList(UNIT_GUID + ".json"))
                .setObjectName(UNIT_GUID + ".json").setCurrentStep("Store unit");

        when(metadataClient.selectUnitbyId(anyObject(), anyObject())).thenReturn(unitResponse);
        
        Mockito.doThrow(new LogbookClientException("Error Logbook")).when(logbookClient)
                .selectUnitLifeCycleById(anyObject(), anyObject(), eq(LifeCycleStatusCode.LIFE_CYCLE_IN_PROCESS));

        plugin = new StoreMetaDataUnitActionPlugin();

        final ItemStatus response = plugin.execute(params, action);
        assertEquals(StatusCode.FATAL, response.getGlobalStatus());
    }
    
    @Test
    public void givenStorageClientWhenStoreFromWorkspaceThenThrowStorageNotFoundClientExceptionThenFATAL()
        throws Exception {
        final WorkerParameters params =
            WorkerParametersFactory.newWorkerParameters().setWorkerGUID(GUIDFactory
                .newGUID()).setContainerName(CONTAINER_NAME).setUrlMetadata("http://localhost:8083")
                .setUrlWorkspace("http://localhost:8083")
                .setObjectNameList(Lists.newArrayList(UNIT_GUID + ".json"))
                .setObjectName(UNIT_GUID + ".json").setCurrentStep("Store unit");

        SelectMultiQuery query = new SelectMultiQuery();
        ObjectNode constructQuery = query.getFinalSelect();
        when(metadataClient.selectUnitbyId(constructQuery, UNIT_GUID))
            .thenReturn(unitResponse);

        SelectParserSingle parser = new SelectParserSingle();
        parser.parse(constructQuery);
        parser.addCondition(QueryHelper.eq(OB_ID, UNIT_GUID));
        constructQuery = parser.getRequest().getFinalSelect();
        when(logbookClient.selectUnitLifeCycleById(UNIT_GUID, constructQuery, LifeCycleStatusCode.LIFE_CYCLE_IN_PROCESS))
                .thenReturn(lfcResponse);
        
        when(workspaceClient.getObject(CONTAINER_NAME,
            DataCategory.UNIT.name() + "/" + params.getObjectName()))
            .thenReturn(Response.status(Status.OK).entity(unit).build());

        Mockito.doThrow(new StorageNotFoundClientException("Error Metadata")).when(storageClient)
            .storeFileFromWorkspace(anyObject(), anyObject(), anyObject(), anyObject());

        plugin = new StoreMetaDataUnitActionPlugin();

        final ItemStatus response = plugin.execute(params, action);
        assertEquals(StatusCode.FATAL, response.getGlobalStatus());
    }
    
    @Test
    public void givenStorageClientWhenStoreFromWorkspaceThenThrowStorageAlreadyExistsClientExceptionThenKO()
        throws Exception {
        final WorkerParameters params =
            WorkerParametersFactory.newWorkerParameters().setWorkerGUID(GUIDFactory
                .newGUID()).setContainerName(CONTAINER_NAME).setUrlMetadata("http://localhost:8083")
                .setUrlWorkspace("http://localhost:8083")
                .setObjectNameList(Lists.newArrayList(UNIT_GUID + ".json"))
                .setObjectName(UNIT_GUID + ".json").setCurrentStep("Store unit");

        SelectMultiQuery query = new SelectMultiQuery();
        ObjectNode constructQuery = query.getFinalSelect();
        when(metadataClient.selectUnitbyId(constructQuery, UNIT_GUID))
            .thenReturn(unitResponse);

        SelectParserSingle parser = new SelectParserSingle();
        parser.parse(constructQuery);
        parser.addCondition(QueryHelper.eq(OB_ID, UNIT_GUID));
        constructQuery = parser.getRequest().getFinalSelect();
        when(logbookClient.selectUnitLifeCycleById(UNIT_GUID, constructQuery, LifeCycleStatusCode.LIFE_CYCLE_IN_PROCESS))
                .thenReturn(lfcResponse);
        
        when(workspaceClient.getObject(CONTAINER_NAME,
            DataCategory.UNIT.name() + "/" + params.getObjectName()))
            .thenReturn(Response.status(Status.OK).entity(unit).build());

        Mockito.doThrow(new StorageAlreadyExistsClientException("Error Metadata ")).when(storageClient)
            .storeFileFromWorkspace(anyObject(), anyObject(), anyObject(), anyObject());

        plugin = new StoreMetaDataUnitActionPlugin();

        final ItemStatus response = plugin.execute(params, action);
        assertEquals(StatusCode.KO, response.getGlobalStatus());
    }

}
