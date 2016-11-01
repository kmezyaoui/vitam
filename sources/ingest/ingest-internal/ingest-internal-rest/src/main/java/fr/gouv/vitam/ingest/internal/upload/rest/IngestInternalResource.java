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
package fr.gouv.vitam.ingest.internal.upload.rest;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Queue;

import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.stream.XMLStreamException;

import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;

import fr.gouv.vitam.common.CommonMediaType;
import fr.gouv.vitam.common.FileUtil;
import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.exception.InvalidGuidOperationException;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDReader;
import fr.gouv.vitam.common.i18n.VitamLogbookMessages;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.server2.application.AsyncInputStreamHelper;
import fr.gouv.vitam.common.server2.application.resources.ApplicationStatusResource;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.ingest.internal.api.upload.UploadService;
import fr.gouv.vitam.ingest.internal.common.util.LogbookOperationParametersList;
import fr.gouv.vitam.logbook.common.exception.LogbookClientAlreadyExistsException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientBadRequestException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientNotFoundException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientServerException;
import fr.gouv.vitam.logbook.common.parameters.LogbookOperationParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookParameterName;
import fr.gouv.vitam.logbook.common.parameters.LogbookParametersFactory;
import fr.gouv.vitam.logbook.common.parameters.LogbookTypeProcess;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClient;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import fr.gouv.vitam.processing.common.exception.ProcessingBadRequestException;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.exception.ProcessingInternalServerException;
import fr.gouv.vitam.processing.common.exception.ProcessingUnauthorizeException;
import fr.gouv.vitam.processing.common.exception.WorkflowNotFoundException;
import fr.gouv.vitam.processing.common.model.OutcomeMessage;
import fr.gouv.vitam.processing.management.client.ProcessingManagementClient;
import fr.gouv.vitam.processing.management.client.ProcessingManagementClientFactory;
import fr.gouv.vitam.storage.engine.client.StorageClient;
import fr.gouv.vitam.storage.engine.client.StorageClientFactory;
import fr.gouv.vitam.storage.engine.client.StorageCollectionType;
import fr.gouv.vitam.storage.engine.client.exception.StorageClientException;
import fr.gouv.vitam.storage.engine.client.exception.StorageServerClientException;
import fr.gouv.vitam.storage.engine.common.exception.StorageNotFoundException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageAlreadyExistException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageCompressedFileException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageNotFoundException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;

/**
 * IngestInternalResource implements UploadService
 *
 */
@Path("/ingest/v1")
@javax.ws.rs.ApplicationPath("webresources")
public class IngestInternalResource extends ApplicationStatusResource implements UploadService {

    private static VitamLogger LOGGER = VitamLoggerFactory.getInstance(IngestInternalResource.class);

    private static final String FOLDER_SIP = "SIP";
    private static final String INGEST_ATR_FORWARD = "STP_INGEST_ATR_FORWARD";
    private static final String INGEST_INT_UPLOAD = "STP_UPLOAD_SIP";
    private static final String INGEST_WORKFLOW = "PROCESS_SIP_UNITARY";
    private static final String DEFAULT_TENANT = "0";
    private static final String DEFAULT_STRATEGY = "default";
    private static final String XML = ".xml";

    private final IngestInternalConfiguration configuration;
    private LogbookOperationParameters parameters;
    private final ProcessingManagementClient processingClient;
    private final WorkspaceClient workspaceClientMock;

    /**
     * IngestInternalResource constructor
     *
     * @param configuration ingest configuration
     *
     */
    public IngestInternalResource(IngestInternalConfiguration configuration) {
        this.configuration = configuration;
        WorkspaceClientFactory.changeMode(configuration.getWorkspaceUrl());
        workspaceClientMock = null;
        processingClient = ProcessingManagementClientFactory.create(configuration.getProcessingUrl());
    }

    /**
     * IngestInternalResource constructor for tests
     *
     * @param configuration ingest configuration, internal values are not used.
     * @param workspaceClient workspace client instance
     * @param processingClient processing client instance
     *
     */
    IngestInternalResource(IngestInternalConfiguration configuration, WorkspaceClient workspaceClient,
        ProcessingManagementClient processingClient) {
        this.configuration = configuration;
        this.workspaceClientMock = workspaceClient;
        this.processingClient = processingClient;
    }

    /**
     * Allow to create a logbook by delegation
     * 
     * @param xRequestId X-Request-Id must have the very same value than eventProcessusId
     * @param queue list of LogbookOperationParameters, first being the created master
     * @return the status of the request (CREATED meaning OK)
     */
    @POST
    @Path("/logbooks")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response delegateCreateLogbookOperation(@HeaderParam(GlobalDataRest.X_REQUEST_ID) String xRequestId,
        Queue<LogbookOperationParameters> queue) {
        try (LogbookOperationsClient client =
            LogbookOperationsClientFactory.getInstance().getClient()) {
            ParametersChecker.checkParameter("X-Request-Id is a Mandatory parameter", xRequestId);
            ParametersChecker.checkParameter("list is a Mandatory parameter", queue);
            client.bulkCreate(xRequestId, queue);
            return Response.status(Status.CREATED).build();
        } catch (IllegalArgumentException | LogbookClientBadRequestException e) {
            LOGGER.error(e);
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (LogbookClientAlreadyExistsException e) {
            LOGGER.error(e);
            return Response.status(Status.CONFLICT).entity(e.getMessage()).build();
        } catch (LogbookClientServerException e) {
            LOGGER.error(e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    /**
     * Allow to update a logbook by delegation
     * 
     * @param xRequestId X-Request-Id must have the very same value than eventProcessusId
     * @param queue list of LogbookOperationParameters in append mode (created already done before)
     * @return the status of the request (OK)
     */
    @PUT
    @Path("/logbooks")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response delegateUpdateLogbookOperation(@HeaderParam(GlobalDataRest.X_REQUEST_ID) String xRequestId,
        Queue<LogbookOperationParameters> queue) {
        try (LogbookOperationsClient client =
            LogbookOperationsClientFactory.getInstance().getClient()) {
            ParametersChecker.checkParameter("list is a Mandatory parameter", queue);
            client.bulkUpdate(xRequestId, queue);
            return Response.status(Status.OK).build();
        } catch (IllegalArgumentException | LogbookClientBadRequestException e) {
            LOGGER.error(e);
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (LogbookClientNotFoundException e) {
            LOGGER.error(e);
            return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (LogbookClientServerException e) {
            LOGGER.error(e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    /**
     * Upload compressed SIP as Stream, will be uncompressed in workspace.</br>
     * </br>
     * Will return {@link Response} containing an InputStream for the ArchiveTransferReply (OK or KO) except in
     * INTERNAL_ERROR (no body)
     * 
     * @param xRequestId X-Request-Id must have the very same value than eventProcessusId
     * @param contentType the header Content-Type (zip, tar, ...)
     * @param uploadedInputStream the stream to upload
     * @param asyncResponse
     * 
     */
    @POST
    @Path("/ingests")
    @Consumes({MediaType.APPLICATION_OCTET_STREAM, CommonMediaType.ZIP, CommonMediaType.GZIP, CommonMediaType.TAR})
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public void uploadSipAsStream(@HeaderParam(GlobalDataRest.X_REQUEST_ID) String xRequestId,
        @HeaderParam(HttpHeaders.CONTENT_TYPE) String contentType, InputStream uploadedInputStream,
        @Suspended final AsyncResponse asyncResponse) {
        VitamThreadPoolExecutor.getInstance().execute(new Runnable() {

            @Override
            public void run() {
                ingestAsync(asyncResponse, xRequestId, contentType, uploadedInputStream);
            }
        });
    }

    private void ingestAsync(final AsyncResponse asyncResponse, String xRequestId, String contentType,
        InputStream uploadedInputStream) {
        String fileName = "";
        try (LogbookOperationsClient logbookOperationsClient =
            LogbookOperationsClientFactory.getInstance().getClient()) {

            try {
                ParametersChecker.checkParameter("xRequestId is a Mandatory parameter", xRequestId);
                ParametersChecker.checkParameter("HTTP Request must contains stream", uploadedInputStream);

                final GUID containerGUID = GUIDReader.getGUID(xRequestId);

                logbookInitialisation(logbookOperationsClient, containerGUID, containerGUID);
                parameters.putParameterValue(LogbookParameterName.eventType, INGEST_WORKFLOW);
                if (parameters.getParameterValue(LogbookParameterName.objectIdentifierIncome) != null) {
                    fileName = parameters.getParameterValue(LogbookParameterName.objectIdentifierIncome);
                }

                MediaType mediaType = CommonMediaType.valueOf(contentType);
                String archiveMimeType = CommonMediaType.mimeTypeOf(mediaType);

                // Save sip file
                LOGGER.debug("Starting up the save file sip");
                // workspace
                parameters.putParameterValue(LogbookParameterName.eventType, INGEST_INT_UPLOAD);
                callLogbookUpdate(logbookOperationsClient, parameters, StatusCode.STARTED,
                    VitamLogbookMessages.getCodeOp(INGEST_INT_UPLOAD, StatusCode.STARTED));
                // push uploaded sip as stream
                pushSipStreamToWorkspace(configuration.getWorkspaceUrl(), containerGUID.getId(), archiveMimeType,
                    uploadedInputStream,
                    parameters);
                final String uploadSIPMsg = VitamLogbookMessages.getCodeOp(INGEST_INT_UPLOAD, StatusCode.OK);

                callLogbookUpdate(logbookOperationsClient, parameters, StatusCode.OK, uploadSIPMsg);
                // processing
                parameters.putParameterValue(LogbookParameterName.eventType, INGEST_WORKFLOW);
                final boolean processingOk =
                    callProcessingEngine(parameters, logbookOperationsClient, containerGUID.getId());
                try (final StorageClient storageClient = StorageClientFactory.getInstance().getClient()) {
                    final InputStream stream =
                        storageClient.getContainer(DEFAULT_TENANT, DEFAULT_STRATEGY, containerGUID.getId() + XML,
                            StorageCollectionType.REPORTS);
                    // FIXME P0 when storage in Async mode use response instead
                    AsyncInputStreamHelper helper = new AsyncInputStreamHelper(asyncResponse, stream, null);
                    if (processingOk) {
                        helper.writeResponse(Response.status(Status.OK)
                            .entity(getAtrFromStorage(containerGUID.getId()))
                            .header(GlobalDataRest.X_REQUEST_ID, xRequestId));
                    } else {
                        // FIXME P0 Should set the status according to the rignt one: NOT INTERNAL_SERVER_ERROR!
                        // INTERNAL_SERVER_ERROR should be limited to the case where there is such an internal server
                        // error
                        helper.writeResponse(Response.status(Status.INTERNAL_SERVER_ERROR)
                            .entity(getAtrFromStorage(containerGUID.getId()))
                            .header(GlobalDataRest.X_REQUEST_ID, xRequestId));
                    }
                }
            } catch (final ContentAddressableStorageCompressedFileException e) {
                if (parameters != null) {
                    try {
                        final String errorMsg = VitamLogbookMessages.getCodeOp(INGEST_INT_UPLOAD, StatusCode.KO);
                        callLogbookUpdate(logbookOperationsClient, parameters, StatusCode.KO, errorMsg);
                        parameters.putParameterValue(LogbookParameterName.eventType, INGEST_INT_UPLOAD);
                        callLogbookUpdate(logbookOperationsClient, parameters, StatusCode.KO,
                            OutcomeMessage.WORKFLOW_INGEST_KO.value());
                    } catch (final LogbookClientException e1) {
                        LOGGER.error(e1);
                    }
                }
                LOGGER.error("Unexpected error was thrown : " + e.getMessage(), e);
                AsyncInputStreamHelper.writeErrorAsyncResponse(asyncResponse,
                    Response.status(Status.INTERNAL_SERVER_ERROR).build());
            } catch (final ContentAddressableStorageException e) {
                if (parameters != null) {
                    try {
                        parameters.putParameterValue(LogbookParameterName.eventType, INGEST_INT_UPLOAD);
                        callLogbookUpdate(logbookOperationsClient, parameters, StatusCode.KO, "error workspace");
                    } catch (final LogbookClientException e1) {
                        LOGGER.error(e1);
                    }
                }
                LOGGER.error("Unexpected error was thrown : " + e.getMessage(), e);
                AsyncInputStreamHelper.writeErrorAsyncResponse(asyncResponse,
                    Response.status(Status.INTERNAL_SERVER_ERROR).build());
                // FIXME P0 in particular Processing Exception could it be a "normal error" ?
            } catch (final InvalidParseOperationException | ProcessingException |
                LogbookClientException | StorageClientException | StorageNotFoundException | IOException |
                InvalidGuidOperationException e) {
                if (parameters != null) {
                    try {
                        parameters.putParameterValue(LogbookParameterName.eventType, INGEST_WORKFLOW);
                        callLogbookUpdate(logbookOperationsClient, parameters, StatusCode.KO, "error ingest");
                    } catch (final LogbookClientException e1) {
                        LOGGER.error(e1);
                    }
                }
                LOGGER.error("Unexpected error was thrown : " + e.getMessage(), e);
                AsyncInputStreamHelper.writeErrorAsyncResponse(asyncResponse,
                    Response.status(Status.INTERNAL_SERVER_ERROR).build());
            } catch (XMLStreamException e) {
                if (parameters != null) {
                    try {
                        parameters.putParameterValue(LogbookParameterName.eventType, INGEST_ATR_FORWARD);
                        callLogbookUpdate(logbookOperationsClient, parameters, StatusCode.KO, "error ATR forward");
                    } catch (final LogbookClientException e1) {
                        LOGGER.error(e1);
                    }
                }
                LOGGER.error("Unexpected error was thrown : " + e.getMessage(), e);
                AsyncInputStreamHelper.writeErrorAsyncResponse(asyncResponse,
                    Response.status(Status.INTERNAL_SERVER_ERROR).build());
            } finally {
                if (logbookOperationsClient != null) {
                    logbookOperationsClient.close();
                }
            }
        }
    }

    // FIXME P0 to be removed
    /**
     * Upload compressed SIP as Stream, will be uncompressed in workspace
     * 
     * @param xRequestId
     * @param partList
     * @return {@link Response}
     */
    @Override
    @POST
    @Path("/upload")
    @Consumes({MediaType.MULTIPART_FORM_DATA, CommonMediaType.ZIP, CommonMediaType.GZIP, CommonMediaType.TAR})
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadSipAsStream(@HeaderParam(GlobalDataRest.X_REQUEST_ID) String xRequestId,
        @FormDataParam("part") List<FormDataBodyPart> partList) {

        Response response;
        String fileName = "";
        try (LogbookOperationsClient logbookOperationsClient =
            LogbookOperationsClientFactory.getInstance().getClient()) {

            try {
                ParametersChecker.checkParameter("partList is a Mandatory parameter", partList);

                final LogbookOperationParametersList logbookOperationParametersList =
                    partList.get(0).getValueAs(LogbookOperationParametersList.class);

                ParametersChecker.checkParameter("logbookOperationParametersList is a Mandatory parameter",
                    logbookOperationParametersList);

                ParametersChecker.checkParameter("xRequestId is a Mandatory parameter", xRequestId);

                final GUID containerGUID = GUIDReader.getGUID(xRequestId);

                logbookInitialisation(logbookOperationsClient, containerGUID, containerGUID);
                // Log Ingest External operations
                LOGGER.debug("Log Ingest External operations");

                for (final LogbookOperationParameters logbookParameters : logbookOperationParametersList
                    .getLogbookOperationList()) {
                    callLogbookUpdate(logbookOperationsClient, logbookParameters);
                }

                parameters.putParameterValue(LogbookParameterName.eventType, INGEST_WORKFLOW);
                if (parameters.getParameterValue(LogbookParameterName.objectIdentifierIncome) != null) {
                    fileName = parameters.getParameterValue(LogbookParameterName.objectIdentifierIncome);
                }

                InputStream uploadedInputStream;

                if (partList.size() == 2) {
                    uploadedInputStream = partList.get(1).getValueAs(InputStream.class);

                    MediaType mediaType = partList.get(1).getMediaType();

                    String archiveMimeType = CommonMediaType.mimeTypeOf(mediaType);

                    ParametersChecker.checkParameter("HTTP Request must contains 2 multiparts part",
                        uploadedInputStream);

                    // Save sip file
                    LOGGER.debug("Starting up the save file sip");
                    // workspace
                    parameters.putParameterValue(LogbookParameterName.eventType, INGEST_INT_UPLOAD);
                    callLogbookUpdate(logbookOperationsClient, parameters, StatusCode.STARTED,
                        VitamLogbookMessages.getCodeOp(INGEST_INT_UPLOAD, StatusCode.STARTED));
                    // push uploaded sip as stream
                    pushSipStreamToWorkspace(configuration.getWorkspaceUrl(), containerGUID.getId(), archiveMimeType,
                        uploadedInputStream,
                        parameters);
                    final String uploadSIPMsg = VitamLogbookMessages.getCodeOp(INGEST_INT_UPLOAD, StatusCode.OK);

                    callLogbookUpdate(logbookOperationsClient, parameters, StatusCode.OK, uploadSIPMsg);
                    // processing
                    parameters.putParameterValue(LogbookParameterName.eventType, INGEST_WORKFLOW);
                    final boolean processingOk =
                        callProcessingEngine(parameters, logbookOperationsClient, containerGUID.getId());
                    if (processingOk) {
                        response = Response.status(Status.OK)
                            .entity(getAtrFromStorage(containerGUID.getId()))
                            .header(GlobalDataRest.X_REQUEST_ID, xRequestId)
                            .build();
                    } else {
                        response = Response.status(Status.INTERNAL_SERVER_ERROR)
                            .entity(getAtrFromStorage(containerGUID.getId()))
                            .header(GlobalDataRest.X_REQUEST_ID, xRequestId)
                            .build();
                    }
                } else {
                    callLogbookUpdate(logbookOperationsClient, parameters, StatusCode.KO,
                        OutcomeMessage.WORKFLOW_INGEST_KO.value());
                    response = Response.status(Status.INTERNAL_SERVER_ERROR)
                        .header(GlobalDataRest.X_REQUEST_ID, xRequestId)
                        .build();
                }

            } catch (final ContentAddressableStorageCompressedFileException e) {

                if (parameters != null) {
                    try {
                        final String errorMsg = VitamLogbookMessages.getCodeOp(INGEST_INT_UPLOAD, StatusCode.KO);
                        callLogbookUpdate(logbookOperationsClient, parameters, StatusCode.KO, errorMsg);
                        parameters.putParameterValue(LogbookParameterName.eventType, INGEST_INT_UPLOAD);
                        callLogbookUpdate(logbookOperationsClient, parameters, StatusCode.KO,
                            OutcomeMessage.WORKFLOW_INGEST_KO.value());
                    } catch (final LogbookClientException e1) {
                        LOGGER.error(e1);
                    }
                }

                LOGGER.error("Unexpected error was thrown : " + e.getMessage(), e);
                response = Response.status(Status.INTERNAL_SERVER_ERROR).build();

            } catch (final ContentAddressableStorageException e) {

                if (parameters != null) {
                    try {
                        parameters.putParameterValue(LogbookParameterName.eventType, INGEST_INT_UPLOAD);
                        callLogbookUpdate(logbookOperationsClient, parameters, StatusCode.KO, "error workspace");
                    } catch (final LogbookClientException e1) {
                        LOGGER.error(e1);
                    }
                }

                LOGGER.error("Unexpected error was thrown : " + e.getMessage(), e);
                response = Response.status(Status.INTERNAL_SERVER_ERROR).build();

            } catch (final InvalidParseOperationException | ProcessingException |
                LogbookClientException | StorageClientException | StorageNotFoundException | IOException |
                InvalidGuidOperationException e) {

                if (parameters != null) {
                    try {
                        parameters.putParameterValue(LogbookParameterName.eventType, INGEST_WORKFLOW);
                        callLogbookUpdate(logbookOperationsClient, parameters, StatusCode.KO, "error ingest");
                    } catch (final LogbookClientException e1) {
                        LOGGER.error(e1);
                    }
                }

                LOGGER.error("Unexpected error was thrown : " + e.getMessage(), e);
                response = Response.status(Status.INTERNAL_SERVER_ERROR).build();

            } catch (XMLStreamException e) {
                if (parameters != null) {
                    try {
                        parameters.putParameterValue(LogbookParameterName.eventType, INGEST_ATR_FORWARD);
                        callLogbookUpdate(logbookOperationsClient, parameters, StatusCode.KO, "error ATR forward");
                    } catch (final LogbookClientException e1) {
                        LOGGER.error(e1);
                    }
                }
                LOGGER.error("Unexpected error was thrown : " + e.getMessage(), e);
                response = Response.status(Status.INTERNAL_SERVER_ERROR).build();
            } finally {
                if (logbookOperationsClient != null) {
                    logbookOperationsClient.close();
                }
            }

            return response;
        }
    }


    private LogbookOperationsClient logbookInitialisation(LogbookOperationsClient client, final GUID ingestGuid,
        final GUID containerGUID)
        throws LogbookClientNotFoundException,
        LogbookClientServerException, LogbookClientAlreadyExistsException, LogbookClientBadRequestException {

        parameters = LogbookParametersFactory.newLogbookOperationParameters(
            ingestGuid, INGEST_WORKFLOW, containerGUID,
            LogbookTypeProcess.INGEST, StatusCode.STARTED,
            ingestGuid != null ? ingestGuid.toString() : "outcomeDetailMessage",
            ingestGuid);

        LOGGER.debug("call journal...");
        client.create(parameters);

        return client;

    }

    private void callLogbookUpdate(LogbookOperationsClient client, LogbookOperationParameters parameters,
        StatusCode logbookOutcome, String outcomeDetailMessage)
        throws LogbookClientNotFoundException, LogbookClientBadRequestException, LogbookClientServerException {

        parameters.setStatus(logbookOutcome);
        parameters.putParameterValue(LogbookParameterName.outcomeDetailMessage, outcomeDetailMessage);
        client.update(parameters);
    }

    private void callLogbookUpdate(LogbookOperationsClient client, LogbookOperationParameters logbookParameters)
        throws LogbookClientNotFoundException, LogbookClientBadRequestException, LogbookClientServerException {
        client.update(logbookParameters);
    }

    /**
     *
     * @param urlWorkspace
     * @param containerName
     * @param uploadedInputStream
     * @param archiveMimeType
     * @throws ContentAddressableStorageException
     */
    private void pushSipStreamToWorkspace(final String urlWorkspace, final String containerName,
        final String archiveMimeType,
        final InputStream uploadedInputStream, final LogbookOperationParameters parameters)
        throws ContentAddressableStorageException, ContentAddressableStorageNotFoundException,
        ContentAddressableStorageAlreadyExistException,
        ContentAddressableStorageCompressedFileException, ContentAddressableStorageServerException {


        parameters.putParameterValue(LogbookParameterName.outcomeDetailMessage, "Try to push stream to workspace...");
        LOGGER.debug("Try to push stream to workspace...");

        // call workspace
        if (workspaceClientMock != null) {
            if (!workspaceClientMock.isExistingContainer(containerName)) {
                workspaceClientMock.createContainer(containerName);
                workspaceClientMock.uncompressObject(containerName, FOLDER_SIP, archiveMimeType, uploadedInputStream);
            } else {
                throw new ContentAddressableStorageAlreadyExistException(containerName + "already exist");
            }
        } else {
            try (WorkspaceClient workspaceClient = WorkspaceClientFactory.getInstance().getClient()) {
                if (!workspaceClient.isExistingContainer(containerName)) {
                    workspaceClient.createContainer(containerName);
                    workspaceClient.uncompressObject(containerName, FOLDER_SIP, archiveMimeType, uploadedInputStream);
                } else {
                    throw new ContentAddressableStorageAlreadyExistException(containerName + "already exist");
                }
            }
        }

        LOGGER.debug(" -> push stream to workspace finished");
        parameters.putParameterValue(LogbookParameterName.outcomeDetailMessage, "-> push stream to workspace finished");
    }

    private boolean callProcessingEngine(final LogbookOperationParameters parameters,
        final LogbookOperationsClient client,
        final String containerName) throws InvalidParseOperationException,
        ProcessingException, LogbookClientNotFoundException, LogbookClientBadRequestException,
        LogbookClientServerException {
        parameters.putParameterValue(LogbookParameterName.outcomeDetailMessage, "Try to call processing...");

        final String workflowId = "DefaultIngestWorkflow";

        try {
            // FIXME P0 should return ItemStatus instead of true or false in order to be able to check the "real" status
            ItemStatus itemStatus = processingClient.executeVitamProcess(containerName, workflowId);
            callLogbookUpdate(client, parameters, itemStatus.getGlobalStatus(),
                OutcomeMessage.WORKFLOW_INGEST_OK.value());
        } catch (WorkflowNotFoundException | ProcessingInternalServerException exc) {
            LOGGER.error(exc);
            callLogbookUpdate(client, parameters, StatusCode.FATAL, OutcomeMessage.WORKFLOW_INGEST_KO.value());
            return false;
        } catch (IllegalArgumentException | ProcessingBadRequestException | ProcessingUnauthorizeException exc) {
            LOGGER.error(exc);
            // FIXME P0 etes vous sûr que c'est un KO ici et pas un FATAL comme au-dessus!
            callLogbookUpdate(client, parameters, StatusCode.KO, OutcomeMessage.WORKFLOW_INGEST_KO.value());
            return false;
        }

        return true;
    }

    private String getAtrFromStorage(String guid)
        throws StorageServerClientException, StorageNotFoundException, XMLStreamException, IOException {
        try (final StorageClient storageClient = StorageClientFactory.getInstance().getClient()) {
            final InputStream stream =
                storageClient.getContainer(DEFAULT_TENANT, DEFAULT_STRATEGY, guid + XML, StorageCollectionType.REPORTS);
            return FileUtil.readInputStream(stream);
        }
    }
}
