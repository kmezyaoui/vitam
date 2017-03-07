/*******************************************************************************
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2019)
 *
 * contact.vitam@culture.gouv.fr
 *
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 *
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 * circulated by CEA, CNRS and INRIA at the following URL "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and rights to copy, modify and redistribute granted by the license,
 * users are provided only with a limited warranty and the software's author, the holder of the economic rights, and the
 * successive licensors have only limited liability.
 *
 * In this respect, the user's attention is drawn to the risks associated with loading, using, modifying and/or
 * developing or reproducing the software by the user in light of its specific status of free software, that may mean
 * that it is complicated to manipulate, and that also therefore means that it is reserved for developers and
 * experienced professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the
 * software's suitability as regards their requirements in conditions enabling the security of their systems and/or data
 * to be ensured and, more generally, to use and operate it in the same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL 2.1 license and that you
 * accept its terms.
 *******************************************************************************/
package fr.gouv.vitam.logbook.common.server.database.collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.mongodb.DBObject;

import fr.gouv.vitam.common.LocalDateUtil;
import fr.gouv.vitam.common.database.server.elasticsearch.ElasticsearchNode;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamApplicationServerException;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.junit.JunitHelper;
import fr.gouv.vitam.common.junit.JunitHelper.ElasticsearchTestConfiguration;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.logbook.common.parameters.LogbookOperationParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookParameterName;
import fr.gouv.vitam.logbook.common.parameters.LogbookParametersFactory;
import fr.gouv.vitam.logbook.common.parameters.LogbookTypeProcess;
import fr.gouv.vitam.logbook.common.server.exception.LogbookException;

public class LogbookElasticsearchAccessTest {

    @ClassRule
    public static TemporaryFolder tempFolder = new TemporaryFolder();

    private final static String CLUSTER_NAME = "vitam-cluster";
    private final static String HOST_NAME = "127.0.0.1";
    private static LogbookElasticsearchAccess esClient;

    private static ElasticsearchTestConfiguration config = null;


    private static final int tenantId = 0;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        // ES
        try {
            config = JunitHelper.startElasticsearchForTest(tempFolder, CLUSTER_NAME);
        } catch (final VitamApplicationServerException e1) {
            assumeTrue(false);
        }

        final List<ElasticsearchNode> nodes = new ArrayList<>();
        nodes.add(new ElasticsearchNode(HOST_NAME, config.getTcpPort()));
        esClient = new LogbookElasticsearchAccess(CLUSTER_NAME, nodes);
    }

    @AfterClass
    public static void tearDownAfterClass() {
        esClient.close();
        if (config != null) {
            JunitHelper.stopElasticsearchForTest(config);
        }
    }


    @Test
    public void testElasticsearchAccessOperation() throws InvalidParseOperationException, LogbookException {
        // add index
        assertEquals(true, esClient.addIndex(LogbookCollections.OPERATION, tenantId));

        // data
        GUID eventIdentifier = GUIDFactory.newEventGUID(0);
        String eventType = "IMPORT_FORMAT";
        GUID eventIdentifierProcess = GUIDFactory.newEventGUID(0);
        LogbookTypeProcess eventTypeProcess = LogbookTypeProcess.MASTERDATA;
        StatusCode outcome = StatusCode.STARTED;
        String outcomeDetailMessage = "IMPORT_FORMAT." + StatusCode.STARTED.name();
        GUID eventIdentifierRequest = GUIDFactory.newEventGUID(0);

        // add indexEntry
        final LogbookOperationParameters parametersForCreation =
            LogbookParametersFactory.newLogbookOperationParameters(eventIdentifier,
                eventType, eventIdentifierProcess, eventTypeProcess,
                outcome, outcomeDetailMessage, eventIdentifierRequest);
        for (final LogbookParameterName name : LogbookParameterName.values()) {
            if (LogbookParameterName.eventDateTime.equals(name)) {
                parametersForCreation.putParameterValue(name, LocalDateUtil.now().toString());
            } else {
                parametersForCreation.putParameterValue(name,
                    GUIDFactory.newEventGUID(0).getId());
            }

        }

        LogbookOperation operationForCreation = new LogbookOperation(parametersForCreation, false);
        Map<String, String> mapIdJson = new HashMap<>();
        String id = operationForCreation.getId();
        operationForCreation.remove(LogbookCollections.ID);
        final String mongoJson =
            operationForCreation.toJson(new JsonWriterSettings(JsonMode.STRICT));
        operationForCreation.clear();
        final String esJson = ((DBObject) com.mongodb.util.JSON.parse(mongoJson))
            .toString();
        mapIdJson.put(id, esJson);
        esClient.addEntryIndexes(LogbookCollections.OPERATION, tenantId, mapIdJson);

        // check entry
        QueryBuilder query = QueryBuilders.matchAllQuery();
        SearchResponse elasticSearchResponse =
            esClient.search(LogbookCollections.OPERATION, tenantId, query, null, null, 0, 10);

        assertEquals(1, elasticSearchResponse.getHits().getTotalHits());
        assertNotNull(elasticSearchResponse.getHits().getAt(0));

        // update entry
        Map<String, Object> created = elasticSearchResponse.getHits().getAt(0).getSource();
        for (int i = 0; i < 3; i++) {
            outcome = StatusCode.OK;
            outcomeDetailMessage = "IMPORT_FORMAT." + StatusCode.OK.name();
            final LogbookOperationParameters parametersForUpdate =
                LogbookParametersFactory.newLogbookOperationParameters(eventIdentifier,
                    eventType, eventIdentifierProcess, eventTypeProcess,
                    outcome, outcomeDetailMessage, eventIdentifierRequest);
            // add update as event
            ArrayList<Document> events = (ArrayList<Document>) created.get(LogbookDocument.EVENTS);
            if (events == null) {
                events = new ArrayList<Document>();
            }
            events.add(new LogbookOperation(parametersForUpdate, true));
            created.put(LogbookDocument.EVENTS, events);
            String idUpdate = id;
            String mongoJsonUpdate = JsonHandler.unprettyPrint(created);
            final String esJsonUpdate = ((DBObject) com.mongodb.util.JSON.parse(mongoJsonUpdate)).toString();
            esClient.updateEntryIndex(LogbookCollections.OPERATION, tenantId, idUpdate, esJsonUpdate);

        }
        // check entry
        SearchResponse elasticSearchResponse2 =
            esClient.search(LogbookCollections.OPERATION, tenantId, query, null, null, 0, 10);
        assertEquals(1, elasticSearchResponse2.getHits().getTotalHits());
        assertNotNull(elasticSearchResponse2.getHits().getAt(0));
        SearchHit hit = elasticSearchResponse2.getHits().iterator().next();
        assertNotNull(hit);
        System.out.println(hit.getSourceAsString());

        // check search
        // QueryBuilder searchQuery = QueryBuilders.matchQuery("events."+LogbookMongoDbName.outcome.getDbname(), "OK");
        QueryBuilder filter = null;
        filter = QueryBuilders.boolQuery().filter(QueryBuilders.termQuery("_index", "logbookoperation_0"));
        SearchResponse elasticSearchResponse3 =
            esClient.search(LogbookCollections.OPERATION, tenantId, query, filter, null, 0, 01);
        assertEquals(1, elasticSearchResponse3.getHits().getTotalHits());

        // refresh index
        esClient.refreshIndex(LogbookCollections.OPERATION, tenantId);

        // delete index
        assertEquals(true, esClient.deleteIndex(LogbookCollections.OPERATION, tenantId));

        // check post delete
        try {
            esClient.search(LogbookCollections.OPERATION, tenantId, query, null, null, 0, 10);
            fail("Should have failed : IndexNotFoundException");
        } catch (LogbookException e) {}
    }
}