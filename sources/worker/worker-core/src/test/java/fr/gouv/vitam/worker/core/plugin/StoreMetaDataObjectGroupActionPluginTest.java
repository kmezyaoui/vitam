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
public class StoreMetaDataObjectGroupActionPluginTest {

    private static final String METDATA_OG_RESPONSE_JSON = "storeMetadataObjectGroupPlugin/MetadataObjectGroupResponse.json";
    private static final String LFC_OG_RESPONSE_JSON = "storeMetadataObjectGroupPlugin/LFCObjectGroupResponse.json";
    private static final String CONTAINER_NAME = "aebaaaaaaaag3r3cabgjaak2izdlnwiaaaaq";
    private static final String OG_GUID = "aebaaaaaaaag3r7caarvuak2ij3chpyaaaaq";
    private static final String OG_GUID_2 = "aebaaaaaaaakwtamaaxakak32oqku2qaaaaq";
    private static final String OBJECT_GROUP = "storeMetadataObjectGroupPlugin/aebaaaaaaaag3r7caarvuak2ij3chpyaaaaq.json";
    private static final String OBJECT_GROUP_2 = "storeMetadataObjectGroupPlugin/aebaaaaaaaakwtamaaxakak32oqku2qaaaaq.json";
    private static final String OB_ID = "obId";

    private WorkspaceClient workspaceClient;
    private WorkspaceClientFactory workspaceClientFactory;
    private MetaDataClient metadataClient;
    private LogbookLifeCyclesClient logbookClient;
    private StorageClient storageClient;
    private HandlerIOImpl action;
    private StorageClientFactory storageClientFactory;
    
    private final InputStream objectGroup;
    private final InputStream objectGroup2;
    private final JsonNode oGResponse;
    private final JsonNode lfcResponse;

    private StoreMetaDataObjectGroupActionPlugin plugin;

    public StoreMetaDataObjectGroupActionPluginTest() throws FileNotFoundException, InvalidParseOperationException {
        objectGroup = PropertiesUtils.getResourceAsStream(OBJECT_GROUP);
        objectGroup2 = PropertiesUtils.getResourceAsStream(OBJECT_GROUP_2);
        File lfcFile = PropertiesUtils.getResourceFile(LFC_OG_RESPONSE_JSON);
        lfcResponse = JsonHandler.getFromFile(lfcFile);
        File mdfile = PropertiesUtils.getResourceFile(METDATA_OG_RESPONSE_JSON);
        oGResponse = JsonHandler.getFromFile(mdfile);
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
    public void givenMetadataClientWhensearchOGrThenReturnNull() throws Exception {
        final WorkerParameters params =
            WorkerParametersFactory.newWorkerParameters().setWorkerGUID(GUIDFactory
                .newGUID()).setContainerName(CONTAINER_NAME).setUrlMetadata("http://localhost:8083")
                .setUrlWorkspace("http://localhost:8083")
                .setObjectNameList(Lists.newArrayList(OG_GUID + ".json"))
                .setObjectName(OG_GUID + ".json").setCurrentStep("Store objectGroup");

        plugin = new StoreMetaDataObjectGroupActionPlugin();

        final ItemStatus response = plugin.execute(params, action);
        assertEquals(StatusCode.KO, response.getGlobalStatus());
    }

    @Test
    public void givenMetadataClientAndLogbookLifeCycleClientAndWorkspaceResponsesWhenSearchOGThenReturnOK() throws Exception {
        final WorkerParameters params =
            WorkerParametersFactory.newWorkerParameters().setWorkerGUID(GUIDFactory
                .newGUID()).setContainerName(CONTAINER_NAME).setUrlMetadata("http://localhost:8083")
                .setUrlWorkspace("http://localhost:8083")
                .setObjectNameList(Lists.newArrayList(OG_GUID + ".json"))
                .setObjectName(OG_GUID + ".json").setCurrentStep("Store objectGroup");

        SelectMultiQuery query = new SelectMultiQuery();
        ObjectNode constructQuery = query.getFinalSelect();
        when(metadataClient.selectObjectGrouptbyId(constructQuery, OG_GUID))
            .thenReturn(oGResponse);

        SelectParserSingle parser = new SelectParserSingle();
        parser.parse(constructQuery);
        parser.addCondition(QueryHelper.eq(OB_ID, OG_GUID));
        constructQuery = parser.getRequest().getFinalSelect();
        when(logbookClient.selectObjectGroupLifeCycleById(OG_GUID, constructQuery, LifeCycleStatusCode.LIFE_CYCLE_IN_PROCESS))
            .thenReturn(lfcResponse);

        when(workspaceClient.getObject(CONTAINER_NAME,
            DataCategory.OBJECT_GROUP.name() + "/" + params.getObjectName()))
            .thenReturn(Response.status(Status.OK).entity(objectGroup).build());

        when(storageClient.storeFileFromWorkspace(anyObject(), anyObject(), anyObject(), anyObject()))
            .thenReturn(getStoredInfoResult());
        
        plugin = new StoreMetaDataObjectGroupActionPlugin();

        final ItemStatus response = plugin.execute(params, action);
        assertEquals(StatusCode.OK, response.getGlobalStatus());
    }

    @Test
    public void givenMetadataClientAndLogbookLifeCycleClientAndWorkspaceResponsesAdnPdosWhenSearchOGThenReturnOK() throws Exception {
        final WorkerParameters params =
            WorkerParametersFactory.newWorkerParameters().setWorkerGUID(GUIDFactory
                .newGUID()).setContainerName(CONTAINER_NAME).setUrlMetadata("http://localhost:8083")
                .setUrlWorkspace("http://localhost:8083").setObjectNameList(Lists.newArrayList(OG_GUID_2 + ".json"))
                .setObjectName(OG_GUID_2 + ".json")
                .setCurrentStep("Store objectGroup");

        SelectMultiQuery query = new SelectMultiQuery();
        ObjectNode constructQuery = query.getFinalSelect();
        when(metadataClient.selectObjectGrouptbyId(constructQuery, OG_GUID_2)).thenReturn(oGResponse);

        SelectParserSingle parser = new SelectParserSingle();
        parser.parse(constructQuery);
        parser.addCondition(QueryHelper.eq(OB_ID, OG_GUID_2));
        constructQuery = parser.getRequest().getFinalSelect();
        when(logbookClient.selectObjectGroupLifeCycleById(OG_GUID_2, constructQuery, LifeCycleStatusCode.LIFE_CYCLE_IN_PROCESS))
                .thenReturn(lfcResponse);
        
        when(workspaceClient.getObject(CONTAINER_NAME, DataCategory.OBJECT_GROUP.name() + "/" + params.getObjectName()))
            .thenReturn(Response.status(Status.OK).entity(objectGroup2).build());

        when(storageClientFactory.getClient()).thenReturn(storageClient);
        when(StorageClientFactory.getInstance()).thenReturn(storageClientFactory);
        
        when(storageClient.storeFileFromWorkspace(anyObject(), anyObject(), anyObject(), anyObject()))
            .thenReturn(getStoredInfoResult());

        plugin = new StoreMetaDataObjectGroupActionPlugin();

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
        when(metadataClient.selectObjectGrouptbyId(constructQuery, OG_GUID))
                .thenReturn(oGResponse);

        SelectParserSingle parser = new SelectParserSingle();
        parser.parse(constructQuery);
        parser.addCondition(QueryHelper.eq(OB_ID, OG_GUID));
        constructQuery = parser.getRequest().getFinalSelect();
        when(logbookClient.selectObjectGroupLifeCycleById(OG_GUID, constructQuery, LifeCycleStatusCode.LIFE_CYCLE_IN_PROCESS))
                .thenReturn(lfcResponse);

        plugin = new StoreMetaDataObjectGroupActionPlugin();

        // select unit
        JsonNode og = plugin.selectMetadataDocumentById(OG_GUID, DataCategory.OBJECT_GROUP, metadataClient);
        assertNotNull(og);
        assertEquals(og.get("#id").asText(), OG_GUID);

        // select lfc
        JsonNode lfc = plugin.retrieveLogbookLifeCycleById(OG_GUID, DataCategory.OBJECT_GROUP, logbookClient);
        assertNotNull(lfc);
        assertEquals(lfc.get("_id").asText(), OG_GUID);

        // aggregate unit with lfc
        JsonNode docWithLfc = plugin.getDocumentWithLFC(og, lfc, DataCategory.OBJECT_GROUP);
        assertNotNull(docWithLfc);
        assertNotNull(docWithLfc.get("got"));
        assertNotNull(docWithLfc.get("lfc"));

        // check aggregation
        JsonNode aggregatedOg = docWithLfc.get("got");
        JsonNode aggregatedLfc = docWithLfc.get("lfc");
        assertEquals(og, aggregatedOg);
        assertEquals(lfc, aggregatedLfc);
    }

    @Test
    public void givenMetadataClientWhensearchOGThenThrowsException() throws Exception {
        final WorkerParameters params =
            WorkerParametersFactory.newWorkerParameters().setWorkerGUID(GUIDFactory
                .newGUID()).setContainerName(CONTAINER_NAME).setUrlMetadata("http://localhost:8083")
                .setUrlWorkspace("http://localhost:8083").setObjectNameList(Lists.newArrayList(OG_GUID + ".json"))
                .setObjectName(OG_GUID + ".json").setCurrentStep("Store ObjectGroup");

        Mockito.doThrow(new MetaDataExecutionException("Error Metadata")).when(metadataClient)
            .selectObjectGrouptbyId(anyObject(), anyObject());

        when(logbookClient.selectObjectGroupLifeCycleById(anyObject(), anyObject(), eq(LifeCycleStatusCode.LIFE_CYCLE_IN_PROCESS)))
                .thenReturn(lfcResponse);
        
        plugin = new StoreMetaDataObjectGroupActionPlugin();

        final ItemStatus response = plugin.execute(params, action);
        assertEquals(StatusCode.FATAL, response.getGlobalStatus());
    }

    @Test
    public void givenLogbookLifeCycleClientWhenSearchLfcThenThrowsException() throws Exception {
        final WorkerParameters params =
                WorkerParametersFactory.newWorkerParameters().setWorkerGUID(GUIDFactory
                        .newGUID()).setContainerName(CONTAINER_NAME).setUrlMetadata("http://localhost:8083")
                        .setUrlWorkspace("http://localhost:8083").setObjectNameList(Lists.newArrayList(OG_GUID + ".json"))
                        .setObjectName(OG_GUID + ".json").setCurrentStep("Store ObjectGroup");

        when(metadataClient.selectObjectGrouptbyId(anyObject(), anyObject())).thenReturn(oGResponse);
        
        Mockito.doThrow(new LogbookClientException("Error Logbook")).when(logbookClient)
                .selectObjectGroupLifeCycleById(anyObject(), anyObject(), eq(LifeCycleStatusCode.LIFE_CYCLE_IN_PROCESS));

        plugin = new StoreMetaDataObjectGroupActionPlugin();

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
                .setObjectNameList(Lists.newArrayList(OG_GUID + ".json"))
                .setObjectName(OG_GUID + ".json").setCurrentStep("Store unit");

        SelectMultiQuery query = new SelectMultiQuery();
        ObjectNode constructQuery = query.getFinalSelect();
        when(metadataClient.selectObjectGrouptbyId(constructQuery, OG_GUID))
            .thenReturn(oGResponse);

        SelectParserSingle parser = new SelectParserSingle();
        parser.parse(constructQuery);
        parser.addCondition(QueryHelper.eq(OB_ID, OG_GUID));
        constructQuery = parser.getRequest().getFinalSelect();
        when(logbookClient.selectObjectGroupLifeCycleById(OG_GUID, constructQuery, LifeCycleStatusCode.LIFE_CYCLE_IN_PROCESS))
                .thenReturn(lfcResponse);
        
        when(workspaceClient.getObject(CONTAINER_NAME,
            DataCategory.OBJECT_GROUP.name() + "/" + params.getObjectName()))
            .thenReturn(Response.status(Status.OK).entity(objectGroup).build());

        Mockito.doThrow(new StorageNotFoundClientException("Error Metadata")).when(storageClient)
            .storeFileFromWorkspace(anyObject(), anyObject(), anyObject(), anyObject());

        plugin = new StoreMetaDataObjectGroupActionPlugin();

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
                .setObjectNameList(Lists.newArrayList(OG_GUID + ".json"))
                .setObjectName(OG_GUID + ".json").setCurrentStep("Store unit");

        SelectMultiQuery query = new SelectMultiQuery();
        ObjectNode constructQuery = query.getFinalSelect();
        when(metadataClient.selectObjectGrouptbyId(constructQuery, OG_GUID))
            .thenReturn(oGResponse);

        SelectParserSingle parser = new SelectParserSingle();
        parser.parse(constructQuery);
        parser.addCondition(QueryHelper.eq(OB_ID, OG_GUID));
        constructQuery = parser.getRequest().getFinalSelect();
        when(logbookClient.selectObjectGroupLifeCycleById(OG_GUID, constructQuery, LifeCycleStatusCode.LIFE_CYCLE_IN_PROCESS))
                .thenReturn(lfcResponse);

        when(workspaceClient.getObject(CONTAINER_NAME,
            DataCategory.OBJECT_GROUP.name() + "/" + params.getObjectName()))
            .thenReturn(Response.status(Status.OK).entity(objectGroup).build());

        Mockito.doThrow(new StorageAlreadyExistsClientException("Error Metadata ")).when(storageClient)
            .storeFileFromWorkspace(anyObject(), anyObject(), anyObject(), anyObject());

        plugin = new StoreMetaDataObjectGroupActionPlugin();

        final ItemStatus response = plugin.execute(params, action);
        assertEquals(StatusCode.KO, response.getGlobalStatus());
    }

}
