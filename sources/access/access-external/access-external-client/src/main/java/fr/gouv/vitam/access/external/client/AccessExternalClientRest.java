package fr.gouv.vitam.access.external.client;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;

import fr.gouv.vitam.access.external.api.AccessExtAPI;
import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.client.VitamContext;
import fr.gouv.vitam.common.exception.VitamClientException;
import fr.gouv.vitam.common.exception.VitamClientInternalException;
import fr.gouv.vitam.common.external.client.DefaultClient;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.logbook.LogbookLifecycle;
import fr.gouv.vitam.common.model.logbook.LogbookOperation;

/**
 * Rest client implementation for Access External
 */
class AccessExternalClientRest extends DefaultClient implements AccessExternalClient {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(AccessExternalClientRest.class);

    private static final String INVALID_PARSE_OPERATION = "Invalid Parse Operation";
    private static final String UNITS = "/units/";
    private static final String OBJECTS = "/objects/";
    private static final String BLANK_DSL = "select DSL is blank";
    private static final String BLANK_UNIT_ID = "unit identifier should be filled";
    private static final String BLANK_OBJECT_ID = "object identifier should be filled";
    private static final String BLANK_OBJECT_GROUP_ID = "object identifier should be filled";
    private static final String BLANK_USAGE = "usage should be filled";
    private static final String BLANK_VERSION = "usage version should be filled";
    private static final String BLANK_DIP_ID = "DIP identifier should be filled";

    private static final String LOGBOOK_OPERATIONS_URL = "/logbookoperations";
    private static final String LOGBOOK_UNIT_LIFECYCLE_URL = "/logbookunitlifecycles";
    private static final String LOGBOOK_OBJECT_LIFECYCLE_URL = "/logbookobjectslifecycles";

    AccessExternalClientRest(AccessExternalClientFactory factory) {
        super(factory);
    }

    @Override
    public RequestResponse<JsonNode> selectUnits(VitamContext vitamContext, JsonNode selectQuery)
        throws VitamClientException {
        Response response = null;

        MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.putAll(vitamContext.getHeaders());

        try {
            response = performRequest(HttpMethod.GET, "/units", headers,
                selectQuery, MediaType.APPLICATION_JSON_TYPE, MediaType.APPLICATION_JSON_TYPE, false);
            return RequestResponse.parseFromResponse(response, JsonNode.class);

        } catch (IllegalStateException e) {
            LOGGER.error("Could not parse server response ", e);
            throw createExceptionFromResponse(response);
        } catch (VitamClientInternalException e) {
            LOGGER.error("VitamClientInternalException: ", e);
            throw new VitamClientException(e);
        } finally {
            consumeAnyEntityAndClose(response);
        }

    }

    @Override
    public RequestResponse<JsonNode> selectUnitbyId(VitamContext vitamContext, JsonNode selectQuery, String unitId)
        throws VitamClientException {
        Response response = null;

        final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.putAll(vitamContext.getHeaders());

        ParametersChecker.checkParameter(BLANK_UNIT_ID, unitId);

        try {
            response = performRequest(HttpMethod.GET, UNITS + unitId, headers,
                selectQuery, MediaType.APPLICATION_JSON_TYPE, MediaType.APPLICATION_JSON_TYPE, false);
            return RequestResponse.parseFromResponse(response, JsonNode.class);

        } catch (IllegalStateException e) {
            LOGGER.error("Could not parse server response ", e);
            throw createExceptionFromResponse(response);
        } catch (VitamClientInternalException e) {
            LOGGER.error("VitamClientInternalException: ", e);
            throw new VitamClientException(e);
        } finally {
            consumeAnyEntityAndClose(response);
        }
    }

    @Override
    public RequestResponse<JsonNode> updateUnitbyId(VitamContext vitamContext, JsonNode updateQuery, String unitId)
        throws VitamClientException {
        Response response = null;

        MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.putAll(vitamContext.getHeaders());

        ParametersChecker.checkParameter(BLANK_UNIT_ID, unitId);

        try {
            response = performRequest(HttpMethod.PUT, UNITS + unitId, headers, updateQuery,
                MediaType.APPLICATION_JSON_TYPE, MediaType.APPLICATION_JSON_TYPE, false);
            return RequestResponse.parseFromResponse(response, JsonNode.class);

        } catch (IllegalStateException e) {
            LOGGER.error("Could not parse server response ", e);
            throw createExceptionFromResponse(response);
        } catch (VitamClientInternalException e) {
            LOGGER.error("VitamClientInternalException: ", e);
            throw new VitamClientException(e);
        } finally {
            consumeAnyEntityAndClose(response);
        }
    }

    @Override
    public RequestResponse<JsonNode> selectObjectMetadatasByUnitId(VitamContext vitamContext,
        JsonNode selectObjectQuery,
        String unitId)
        throws VitamClientException {

        ParametersChecker.checkParameter(BLANK_OBJECT_ID, unitId);

        Response response = null;
        MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.putAll(vitamContext.getHeaders());
        try {
            response = performRequest(HttpMethod.GET, "/units/" + unitId + "/objects", headers,
                selectObjectQuery, MediaType.APPLICATION_JSON_TYPE, MediaType.APPLICATION_JSON_TYPE, false);
            return RequestResponse.parseFromResponse(response, JsonNode.class);

        } catch (IllegalStateException e) {
            LOGGER.error("Could not parse server response ", e);
            throw createExceptionFromResponse(response);
        } catch (final VitamClientInternalException e) {
            LOGGER.error("VitamClientInternalException: ", e);
            throw new VitamClientException(e);
        } finally {
            consumeAnyEntityAndClose(response);
        }
    }


    @Override
    public Response getObjectStreamByUnitId(VitamContext vitamContext,
        String unitId,
        String usage,
        int version)
        throws VitamClientException {


        ParametersChecker.checkParameter(BLANK_OBJECT_ID, unitId);
        ParametersChecker.checkParameter(BLANK_USAGE, usage);
        ParametersChecker.checkParameter(BLANK_VERSION, version);

        Response response = null;
        MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.putAll(vitamContext.getHeaders());
        headers.add(GlobalDataRest.X_QUALIFIER, usage);
        headers.add(GlobalDataRest.X_VERSION, version);

        try {
            response = performRequest(HttpMethod.GET, "/units/" + unitId + "/objects", headers,
                null, MediaType.APPLICATION_JSON_TYPE, MediaType.APPLICATION_OCTET_STREAM_TYPE, false);

        } catch (final VitamClientInternalException e) {
            LOGGER.error("VitamClientInternalException: ", e);
            throw new VitamClientException(e);
        }
        return response;
    }

    /* Logbook external */

    @Override
    public RequestResponse<LogbookOperation> selectOperations(VitamContext vitamContext,
        JsonNode select)
        throws VitamClientException {
        Response response = null;
        try {
            MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
            headers.putAll(vitamContext.getHeaders());
            response = performRequest(HttpMethod.GET, LOGBOOK_OPERATIONS_URL, headers, select,
                MediaType.APPLICATION_JSON_TYPE, MediaType.APPLICATION_JSON_TYPE, false);
            return RequestResponse.parseFromResponse(response, LogbookOperation.class);

        } catch (IllegalStateException e) {
            LOGGER.error("Could not parse server response ", e);
            throw createExceptionFromResponse(response);
        } catch (VitamClientInternalException e) {
            LOGGER.error("VitamClientInternalException: ", e);
            throw new VitamClientException(e);
        } finally {
            consumeAnyEntityAndClose(response);
        }
    }

    @Override
    public RequestResponse<LogbookOperation> selectOperationbyId(VitamContext vitamContext,
        String processId, JsonNode select)
        throws VitamClientException {

        Response response = null;
        MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.putAll(vitamContext.getHeaders());
        try {
            response = performRequest(HttpMethod.GET, LOGBOOK_OPERATIONS_URL + "/" + processId, headers,
                select, MediaType.APPLICATION_JSON_TYPE,
                MediaType.APPLICATION_JSON_TYPE, false);
            return RequestResponse.parseFromResponse(response, LogbookOperation.class);

        } catch (IllegalStateException e) {
            LOGGER.error("Could not parse server response ", e);
            throw createExceptionFromResponse(response);
        } catch (VitamClientInternalException e) {
            LOGGER.error("VitamClientInternalException: ", e);
            throw new VitamClientException(e);
        } finally {
            consumeAnyEntityAndClose(response);
        }
    }

    @Override
    public RequestResponse<LogbookLifecycle> selectUnitLifeCycleById(VitamContext vitamContext, String idUnit,
        JsonNode select)
        throws VitamClientException {
        Response response = null;
        ParametersChecker.checkParameter(BLANK_UNIT_ID, idUnit);
        MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.putAll(vitamContext.getHeaders());
        try {
            response =
                performRequest(HttpMethod.GET, LOGBOOK_UNIT_LIFECYCLE_URL + "/" + idUnit, headers,
                    select, MediaType.APPLICATION_JSON_TYPE,
                    MediaType.APPLICATION_JSON_TYPE, false);
            return RequestResponse.parseFromResponse(response, LogbookLifecycle.class);

        } catch (IllegalStateException e) {
            LOGGER.error("Could not parse server response ", e);
            throw createExceptionFromResponse(response);
        } catch (VitamClientInternalException e) {
            LOGGER.error("VitamClientInternalException: ", e);
            throw new VitamClientException(e);
        } finally {
            consumeAnyEntityAndClose(response);
        }
    }

    @Override
    public RequestResponse<LogbookLifecycle> selectObjectGroupLifeCycleById(
        VitamContext vitamContext, String idObject, JsonNode select)
        throws VitamClientException {
        Response response = null;
        ParametersChecker.checkParameter(BLANK_OBJECT_GROUP_ID, idObject);
        MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.putAll(vitamContext.getHeaders());
        try {
            response = performRequest(HttpMethod.GET, LOGBOOK_OBJECT_LIFECYCLE_URL + "/" + idObject,
                headers,
                select, MediaType.APPLICATION_JSON_TYPE,
                MediaType.APPLICATION_JSON_TYPE, false);

            return RequestResponse.parseFromResponse(response, LogbookLifecycle.class);

        } catch (IllegalStateException e) {
            LOGGER.error("Could not parse server response ", e);
            throw createExceptionFromResponse(response);
        } catch (VitamClientInternalException e) {
            LOGGER.error("VitamClientInternalException: ", e);
            throw new VitamClientException(e);
        }
    }

    @Override
    public RequestResponse<JsonNode> exportDIP(VitamContext vitamContext, JsonNode selectQuery)
        throws VitamClientException {
        Response response = null;

        MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.putAll(vitamContext.getHeaders());

        try {
            response = performRequest(HttpMethod.POST, AccessExtAPI.DIP_API, headers,
                selectQuery, MediaType.APPLICATION_JSON_TYPE, MediaType.APPLICATION_JSON_TYPE, false);
            return RequestResponse.parseFromResponse(response, JsonNode.class);
        } catch (IllegalStateException e) {
            LOGGER.error("Could not parse server response ", e);
            throw createExceptionFromResponse(response);
        } catch (VitamClientInternalException e) {
            LOGGER.error("VitamClientInternalException: ", e);
            throw new VitamClientException(e);
        } finally {
            consumeAnyEntityAndClose(response);
        }
    }

    @Override
    public Response getDIPById(VitamContext vitamContext, String dipId)
        throws VitamClientException {

        ParametersChecker.checkParameter(BLANK_DIP_ID, dipId);

        Response response = null;
        MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.putAll(vitamContext.getHeaders());

        try {
            response = performRequest(HttpMethod.GET, AccessExtAPI.DIP_API + "/" + dipId + "/dip", headers,
                null, MediaType.APPLICATION_JSON_TYPE, MediaType.APPLICATION_OCTET_STREAM_TYPE, false);

        } catch (final VitamClientInternalException e) {
            LOGGER.error("VitamClientInternalException: ", e);
            throw new VitamClientException(e);
        }
        return response;
    }
}
