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

package fr.gouv.vitam.storage.logbook;

import static fr.gouv.vitam.common.LocalDateUtil.getString;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.digest.Digest;
import fr.gouv.vitam.common.digest.DigestType;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDFactory;
import static fr.gouv.vitam.common.json.JsonHandler.unprettyPrint;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.StatusCode;
import static fr.gouv.vitam.common.model.StatusCode.FATAL;
import static fr.gouv.vitam.common.model.StatusCode.STARTED;
import fr.gouv.vitam.common.parameter.ParameterHelper;
import fr.gouv.vitam.logbook.common.exception.LogbookClientAlreadyExistsException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientBadRequestException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientNotFoundException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientServerException;
import fr.gouv.vitam.logbook.common.exception.TraceabilityException;
import fr.gouv.vitam.logbook.common.parameters.LogbookOperationParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookOperationsClientHelper;
import fr.gouv.vitam.logbook.common.parameters.LogbookParameterName;
import static fr.gouv.vitam.logbook.common.parameters.LogbookParametersFactory.newLogbookOperationParameters;
import static fr.gouv.vitam.logbook.common.parameters.LogbookTypeProcess.STORAGE_LOGBOOK;
import fr.gouv.vitam.logbook.common.server.exception.LogbookDatabaseException;
import fr.gouv.vitam.logbook.common.server.exception.LogbookNotFoundException;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import fr.gouv.vitam.storage.LogInformation;
import fr.gouv.vitam.storage.engine.client.StorageClient;
import fr.gouv.vitam.storage.engine.client.StorageClientFactory;
import fr.gouv.vitam.storage.engine.client.exception.StorageAlreadyExistsClientException;
import fr.gouv.vitam.storage.engine.client.exception.StorageNotFoundClientException;
import fr.gouv.vitam.storage.engine.client.exception.StorageServerClientException;
import fr.gouv.vitam.storage.engine.common.model.StorageCollectionType;
import fr.gouv.vitam.storage.engine.common.model.request.ObjectDescription;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageAlreadyExistException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageNotFoundException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;
import org.apache.commons.compress.archivers.ArchiveException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;

/**
 * Business class for Logbook Administration (traceability)
 */
public class StorageLogbookAdministration {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(StorageLogbookAdministration.class);

    private static final String STP_SECURISATION = "STORAGE_LOG_OP_SECURISATION";
    private static final String OP_SECURISATION_STORAGE_LOGBOOK = "OP_SECURISATION_STORAGE_LOGBOOK";
    private static final String STP_OP_SECURISATION = "STP_OP_SECURISATION";
    private static final String STP_BEGIN_MESSAGE = "Sécurisation des journaux";
    private static final String STP_BEGIN_MESSAGE_PROCESS = "Début de la sécurisation des journaux";
    private static final String STP_FAIL_MESSAGE_PROCESS = "Echec de la sécurisation des journaux";


    private static final String STRATEGY_ID = "default";
    public static final String STORAGE_LOGBOOK_OPERATION_ZIP = "Storage_LogbookOperation";
    final StorageLogbookService storageLogbookService;
    private final File tmpFolder;



    public StorageLogbookAdministration(StorageLogbookService storageLogbookService,
        String tmpFolder) {
        this.storageLogbookService = storageLogbookService;
        this.tmpFolder = new File(tmpFolder);
        this.tmpFolder.mkdir();
    }



    /**
     * secure the logbook operation since last securisation.
     *
     * @return the GUID of the operation
     * @throws TraceabilityException           if error on generating secure logbook
     * @throws LogbookNotFoundException        if not found on selecting logbook operation
     * @throws InvalidParseOperationException  if json data is not well-formed
     * @throws LogbookDatabaseException        if error on query logbook collection
     * @throws InvalidCreateOperationException if error on creating query
     */
    // TODO: use a distributed lock to launch this function only on one server (cf consul)
    public synchronized GUID generateSecureStorageLogbook()
        throws TraceabilityException, IOException, ArchiveException, StorageLogException,
        LogbookClientBadRequestException, LogbookClientAlreadyExistsException, LogbookClientServerException {
        final LogbookOperationsClientHelper helper = new LogbookOperationsClientHelper();
        Integer tenantId = ParameterHelper.getTenantParameter();
        final GUID eip = GUIDFactory.newOperationLogbookGUID(tenantId);
        try {

            final String fileName = String.format("%d_"+STORAGE_LOGBOOK_OPERATION_ZIP+"_%s.zip", tenantId, eip.toString());
            createLogbookOperationStructure(helper, eip, tenantId);

            final File zipFile = new File(tmpFolder, fileName);
            final String uri = String.format("%s/%s", "storgae_logbook", fileName);
            LogInformationEvent event = null;
            LogInformation info = storageLogbookService.generateSecureStorage(tenantId);
            try (LogZipFile logZipFile = new LogZipFile(zipFile)) {
                logZipFile.initStoreLog();
                final DigestType digestType = VitamConfiguration.getDefaultTimestampDigestType();
                final Digest digest = new Digest(digestType);
                FileInputStream stream = new FileInputStream(info.getPath().toFile());
                logZipFile.storeLogFile(digest.getDigestInputStream(stream));
                logZipFile.storeAdditionalInformation(getString(info.getBeginTime()), getString(info.getEndTime()),
                    digest.toString(),
                    getString(LocalDateTime.now()), tenantId);
                logZipFile.close();
                try {
                    info.getPath().toFile().delete();
                }catch (Exception e ){
                    LOGGER.error("unable to delete log fiel ", e);
                }
            } catch (IOException |
                ArchiveException e) {
                createLogbookOperationEvent(helper, eip, tenantId, STP_OP_SECURISATION, FATAL, null,STP_FAIL_MESSAGE_PROCESS);
                zipFile.delete();
                throw new StorageLogException(e);
            }

            try (InputStream inputStream = new BufferedInputStream(new FileInputStream(zipFile));

                WorkspaceClient workspaceClient = WorkspaceClientFactory.getInstance().getClient()) {



                workspaceClient.createContainer(fileName);
                workspaceClient.putObject(fileName, uri, inputStream);

                final StorageClientFactory storageClientFactory = StorageClientFactory.getInstance();

                final ObjectDescription description = new ObjectDescription();
                description.setWorkspaceContainerGUID(fileName);
                description.setWorkspaceObjectURI(uri);

                try (final StorageClient storageClient = storageClientFactory.getClient()) {
                    storageClient.storeFileFromWorkspace(
                        STRATEGY_ID, StorageCollectionType.STORAGELOG, fileName, description);
                    workspaceClient.deleteObject(fileName, uri);

                } catch (StorageAlreadyExistsClientException | StorageNotFoundClientException |
                    StorageServerClientException | ContentAddressableStorageNotFoundException e) {
                    createLogbookOperationEvent(helper, eip, tenantId, OP_SECURISATION_STORAGE_LOGBOOK, FATAL, null,STP_FAIL_MESSAGE_PROCESS);
                    LOGGER.error("unable to store zip file", e);
                    throw new StorageLogException(e);
                }
            } catch (ContentAddressableStorageAlreadyExistException | ContentAddressableStorageServerException |
                IOException e) {
                LOGGER.error("unable to create container", e);
                createLogbookOperationEvent(helper, eip, tenantId, OP_SECURISATION_STORAGE_LOGBOOK, FATAL, null,STP_FAIL_MESSAGE_PROCESS);
                throw new StorageLogException(e);



            } finally {
                zipFile.delete();
            }
            createLogbookOperationEvent(helper, eip, tenantId, STP_OP_SECURISATION, StatusCode.OK, event, STP_BEGIN_MESSAGE);
        } catch (LogbookClientNotFoundException | LogbookClientAlreadyExistsException e) {
            throw new StorageLogException(e);
        } finally {
            LogbookOperationsClientFactory.getInstance().getClient()
                .bulkCreate(eip.getId(), helper.removeCreateDelegate(eip.getId()));
        }
        return eip;
    }

    private void createLogbookOperationStructure(LogbookOperationsClientHelper helper, GUID eip, Integer tenantId)
        throws LogbookClientNotFoundException, LogbookClientAlreadyExistsException {
        final LogbookOperationParameters logbookParameters =
            newLogbookOperationParameters(eip, STP_SECURISATION, eip, STORAGE_LOGBOOK, STARTED, STP_BEGIN_MESSAGE, eip);
        LogbookOperationsClientHelper.checkLogbookParameters(logbookParameters);
        helper.createDelegate(logbookParameters);
        createLogbookOperationEvent(helper, eip, tenantId, STP_OP_SECURISATION, STARTED, null,STP_BEGIN_MESSAGE_PROCESS);
    }

    private void createLogbookOperationEvent(LogbookOperationsClientHelper helper, GUID parentEventId, Integer tenantId,
        String eventType,
        StatusCode statusCode, LogInformationEvent data,String message) throws LogbookClientNotFoundException {
        final GUID eventId = GUIDFactory.newEventGUID(tenantId);
        final LogbookOperationParameters logbookOperationParameters =
            newLogbookOperationParameters(eventId, eventType, parentEventId, STORAGE_LOGBOOK, statusCode, message,
                parentEventId);
        LogbookOperationsClientHelper.checkLogbookParameters(logbookOperationParameters);
        if (data != null) {
            logbookOperationParameters
                .putParameterValue(LogbookParameterName.eventDetailData, unprettyPrint(data));
        }
        helper.updateDelegate(logbookOperationParameters);
    }

}