/**
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
 */
package fr.gouv.vitam.functional.administration.security.profile.core;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.database.builder.query.QueryHelper;
import fr.gouv.vitam.common.database.builder.query.VitamFieldsHelper;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.database.builder.request.single.Select;
import fr.gouv.vitam.common.database.parser.request.adapter.SingleVarNameAdapter;
import fr.gouv.vitam.common.database.parser.request.single.SelectParserSingle;
import fr.gouv.vitam.common.database.parser.request.single.UpdateParserSingle;
import fr.gouv.vitam.common.database.server.DbRequestResult;
import fr.gouv.vitam.common.error.VitamCode;
import fr.gouv.vitam.common.error.VitamError;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamException;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.i18n.VitamLogbookMessages;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.VitamAutoCloseable;
import fr.gouv.vitam.common.model.administration.SecurityProfileModel;
import fr.gouv.vitam.common.parameter.ParameterHelper;
import fr.gouv.vitam.common.security.SanityChecker;
import fr.gouv.vitam.functional.administration.common.SecurityProfile;
import fr.gouv.vitam.functional.administration.common.exception.ReferentialException;
import fr.gouv.vitam.functional.administration.common.server.FunctionalAdminCollections;
import fr.gouv.vitam.functional.administration.common.server.MongoDbAccessAdminImpl;
import fr.gouv.vitam.functional.administration.counter.SequenceType;
import fr.gouv.vitam.functional.administration.counter.VitamCounterService;
import fr.gouv.vitam.logbook.common.parameters.LogbookOperationParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookOperationsClientHelper;
import fr.gouv.vitam.logbook.common.parameters.LogbookParameterName;
import fr.gouv.vitam.logbook.common.parameters.LogbookParametersFactory;
import fr.gouv.vitam.logbook.common.parameters.LogbookTypeProcess;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClient;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;

public class SecurityProfileService implements VitamAutoCloseable {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(SecurityProfileService.class);
    private static final String SECURITY_PROFILE_IS_MANDATORY_PARAMETER =
        "The collection of security profiles is mandatory";
    private static final String SECURITY_PROFILE_NOT_FOUND = "Security profile not found";
    public static String ERR_ID_NOT_ALLOWED_IN_CREATE = "Id must be null when creating security profiles (%s)";

    public static String ERR_MISSING_SECURITY_PROFILE_IDENTIFIER =
        "Identifier must not be null when creating security profiles (%s)";
    public static String ERR_DUPLICATE_IDENTIFIER_IN_CREATE =
        "One or many security profiles in the imported list have the same identifier : %s";

    public static String ERR_MISSING_SECURITY_PROFILE_NAME = "Security profile name is mandatory (%s)";
    public static final String ERR_DUPLICATE_NAME_IN_CREATE =
        "One or many security profiles in the imported list have the same name : %s";

    public static String ERR_UNEXPECTED_PERMISSION_SET_WITH_FULL_ACCESS =
        "Permission set cannot be set with full access mode : %s";

    private static final String SECURITY_PROFILE_IMPORT_EVENT = "STP_IMPORT_SECURITY_PROFILE";
    private static final String SECURITY_PROFILE_UPDATE_EVENT = "STP_UPDATE_SECURITY_PROFILE";

    private final MongoDbAccessAdminImpl mongoAccess;
    private final LogbookOperationsClient logbookClient;
    private final VitamCounterService vitamCounterService;
    private final SecurityProfileLogbookManager manager;
    private static final String _ID = "_id";

    /**
     * Constructor
     *
     * @param dbConfiguration
     * @param vitamCounterService
     */
    public SecurityProfileService(MongoDbAccessAdminImpl dbConfiguration, VitamCounterService vitamCounterService) {
        mongoAccess = dbConfiguration;
        this.vitamCounterService = vitamCounterService;
        logbookClient = LogbookOperationsClientFactory.getInstance().getClient();
        manager = new SecurityProfileLogbookManager(logbookClient);

    }

    public RequestResponse<SecurityProfileModel> createSecurityProfiles(List<SecurityProfileModel> securityProfileList)
        throws VitamException {
        ParametersChecker.checkParameter(SECURITY_PROFILE_IS_MANDATORY_PARAMETER, securityProfileList);

        if (securityProfileList.isEmpty()) {
            return new RequestResponseOK<>();
        }
        manager.logStarted();

        final VitamError error =
            new VitamError(VitamCode.SECURITY_PROFILE_VALIDATION_ERROR.getItem())
                .setHttpCode(Response.Status.BAD_REQUEST
                    .getStatusCode());

        try {

            validateSecurityProfilesToInsert(securityProfileList, error);

            if (null != error.getErrors() && !error.getErrors().isEmpty()) {
                // log book + application log
                // stop
                String errorsDetails =
                    error.getErrors().stream().map(c -> c.getMessage()).collect(Collectors.joining(","));
                manager.logValidationError(errorsDetails, SECURITY_PROFILE_IMPORT_EVENT);
                return error;
            }

            boolean slaveMode = isSlaveMode();
            for (SecurityProfileModel securityProfile : securityProfileList) {
                securityProfile.setId(GUIDFactory.newContextGUID().getId());

                if (!slaveMode) {
                    String code = vitamCounterService
                        .getNextSequenceAsString(ParameterHelper.getTenantParameter(),
                            SequenceType.SECURITY_PROFILE_SEQUENCE.getName());
                    securityProfile.setIdentifier(code);
                }
            }

            ArrayNode securityProfilesToPersist = JsonHandler.createArrayNode();
            for (SecurityProfileModel securityProfile : securityProfileList) {

                ObjectNode securityProfileNode = (ObjectNode) JsonHandler.toJsonNode(securityProfile);

                JsonNode jsonNode = securityProfileNode.remove(VitamFieldsHelper.id());
                if (jsonNode != null) {
                    securityProfileNode.set(_ID, jsonNode);
                }
                securityProfilesToPersist.add(securityProfileNode);
            }
            mongoAccess.insertDocuments(securityProfilesToPersist, FunctionalAdminCollections.SECURITY_PROFILE).close();

            manager.logImportSuccess();

            return new RequestResponseOK<SecurityProfileModel>().addAllResults(securityProfileList)
                .setHttpCode(Response.Status.CREATED.getStatusCode());

        } catch (final Exception exp) {
            final String err =
                new StringBuilder("Security profile import failed > ").append(exp.getMessage()).toString();
            manager.logFatalError(err);
            return error.setCode(VitamCode.GLOBAL_INTERNAL_SERVER_ERROR.getItem()).setDescription(err).setHttpCode(
                Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
        }
    }

    private void validateSecurityProfilesToInsert(List<SecurityProfileModel> securityProfileList, VitamError error) {

        boolean slaveMode = isSlaveMode();

        final Set<String> securityProfileNames = new HashSet<>();
        final Set<String> securityProfileIdentifiers = new HashSet<>();

        for (SecurityProfileModel securityProfile : securityProfileList) {

            // Security profile id should be null
            if (null != securityProfile.getId()) {
                error.addToErrors(new VitamError(VitamCode.SECURITY_PROFILE_VALIDATION_ERROR.getItem()).setMessage(
                    String.format(ERR_ID_NOT_ALLOWED_IN_CREATE, securityProfile.getName())));
                continue;
            }

            // In slave mode (app provided identifier), security profile identifier should be unique and not null
            if (slaveMode) {

                // if a security profile have an identifier
                if (StringUtils.isEmpty(securityProfile.getIdentifier())) {
                    error.addToErrors(new VitamError(VitamCode.SECURITY_PROFILE_VALIDATION_ERROR.getItem()).setMessage(
                        String.format(ERR_MISSING_SECURITY_PROFILE_IDENTIFIER, securityProfile.getName())));
                    continue;
                }

                if (securityProfileIdentifiers.contains(securityProfile.getIdentifier())) {
                    error.addToErrors(new VitamError(VitamCode.SECURITY_PROFILE_VALIDATION_ERROR.getItem()).setMessage(
                        String.format(ERR_DUPLICATE_IDENTIFIER_IN_CREATE, securityProfile.getIdentifier())));
                    continue;
                }

                // mark the current security profile identifier as treated
                securityProfileIdentifiers.add(securityProfile.getIdentifier());
            }

            // Missing security profile name
            if (StringUtils.isEmpty(securityProfile.getName())) {
                error.addToErrors(new VitamError(VitamCode.SECURITY_PROFILE_VALIDATION_ERROR.getItem()).setMessage(
                    String.format(ERR_MISSING_SECURITY_PROFILE_NAME, securityProfile.getName())));
                continue;
            }

            // Check duplicate security profile names
            if (securityProfileNames.contains(securityProfile.getName())) {
                error.addToErrors(new VitamError(VitamCode.SECURITY_PROFILE_VALIDATION_ERROR.getItem()).setMessage(
                    String.format(ERR_DUPLICATE_NAME_IN_CREATE, securityProfile.getName())));
                continue;
            }

            // mark the current security profile name as treated
            securityProfileNames.add(securityProfile.getName());

            if (securityProfile.getFullAccess()) {

                // Permission set incompatible with full access mode
                if (!CollectionUtils.isEmpty(securityProfile.getPermissions())) {
                    error.addToErrors(new VitamError(VitamCode.SECURITY_PROFILE_VALIDATION_ERROR.getItem()).setMessage(
                        String.format(ERR_UNEXPECTED_PERMISSION_SET_WITH_FULL_ACCESS, securityProfile.getName())));
                    continue;
                }
            }
        }
    }

    public SecurityProfileModel findOneByIdentifier(String identifier)
        throws ReferentialException, InvalidParseOperationException {
        final SelectParserSingle parser = new SelectParserSingle(new SingleVarNameAdapter());
        parser.parse(new Select().getFinalSelect());
        try {
            parser.addCondition(QueryHelper.eq("Identifier", identifier));
        } catch (InvalidCreateOperationException e) {
            throw new ReferentialException(e);
        }

        try (DbRequestResult result =
            mongoAccess.findDocuments(parser.getRequest().getFinalSelect(),
                FunctionalAdminCollections.SECURITY_PROFILE)) {
            final List<SecurityProfileModel> list =
                result.getDocuments(SecurityProfile.class, SecurityProfileModel.class);
            if (list.isEmpty()) {
                return null;
            }
            return list.get(0);
        }
    }

    public RequestResponseOK<SecurityProfileModel> findSecurityProfiles(JsonNode queryDsl)
        throws ReferentialException, InvalidParseOperationException {
        try (DbRequestResult result =
            mongoAccess.findDocuments(queryDsl, FunctionalAdminCollections.SECURITY_PROFILE)) {
            return result.getRequestResponseOK(queryDsl, SecurityProfile.class, SecurityProfileModel.class);
        }
    }

    public RequestResponse<SecurityProfileModel> updateSecurityProfile(String identifier, JsonNode queryDsl)
        throws VitamException {
        VitamError error =
            new VitamError(VitamCode.SECURITY_PROFILE_VALIDATION_ERROR.getItem())
                .setHttpCode(Response.Status.BAD_REQUEST
                    .getStatusCode());

        if (queryDsl == null || !queryDsl.isObject()) {
            return error;
        }

        final SecurityProfileModel securityProfileModel = findOneByIdentifier(identifier);
        if (securityProfileModel == null) {
            error.setHttpCode(Response.Status.NOT_FOUND.getStatusCode());
            return error.addToErrors(new VitamError(VitamCode.SECURITY_PROFILE_VALIDATION_ERROR.getItem()).setMessage(
                SECURITY_PROFILE_NOT_FOUND + identifier));
        }
        manager.logUpdateStarted(securityProfileModel.getId());

        try {

            JsonNode finalUpdate = enforceIdentifierInUpdateQuery(identifier, queryDsl);

            DbRequestResult result = mongoAccess.updateData(finalUpdate, FunctionalAdminCollections.SECURITY_PROFILE);
            Map<String, List<String>> updateDiffs = result.getDiffs();
            result.close();

            manager.logUpdateSuccess(securityProfileModel.getId(), updateDiffs.get(securityProfileModel.getId()));
            return new RequestResponseOK<>();

        } catch (ReferentialException | InvalidCreateOperationException e) {
            final String err = new StringBuilder("Security profile update failed > ").append(e.getMessage()).toString();
            manager.logFatalError(err);
            error.setCode(VitamCode.GLOBAL_INTERNAL_SERVER_ERROR.getItem())
                .setDescription(err)
                .setHttpCode(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());

            return error;
        }
    }

    private JsonNode enforceIdentifierInUpdateQuery(String identifier, JsonNode queryDsl)
        throws InvalidParseOperationException, InvalidCreateOperationException {
        final UpdateParserSingle parser =
            new UpdateParserSingle(FunctionalAdminCollections.SECURITY_PROFILE.getVarNameAdapater());
        parser.parse(queryDsl);
        parser.addCondition(QueryHelper.eq(SecurityProfile.IDENTIFIER, identifier));
        return parser.getRequest().getFinalUpdate();
    }

    /**
     * Logbook manager for security profile operations
     */
    private final static class SecurityProfileLogbookManager {

        private static final String UPDATED_DIFFS = "updatedDiffs";
        private static final String SECURITY_PROFILE = "SecurityProfile";

        final LogbookOperationsClientHelper helper = new LogbookOperationsClientHelper();
        private GUID eip = null;

        private final LogbookOperationsClient logbookClient;

        public SecurityProfileLogbookManager(LogbookOperationsClient logbookClient) {
            this.logbookClient = logbookClient;
        }

        /**
         * Log validation error (business error)
         *
         * @param errorsDetails
         */
        private void logValidationError(final String errorsDetails, final String eventType) throws VitamException {
            LOGGER.error("There validation errors on the input file {}", errorsDetails);
            final LogbookOperationParameters logbookParameters = LogbookParametersFactory
                .newLogbookOperationParameters(eip, eventType, eip, LogbookTypeProcess.MASTERDATA,
                    StatusCode.KO,
                    VitamLogbookMessages.getCodeOp(eventType, StatusCode.KO), eip);
            logbookMessageError(errorsDetails, logbookParameters);
            helper.updateDelegate(logbookParameters);
            logbookClient.bulkCreate(eip.getId(), helper.removeCreateDelegate(eip.getId()));
        }

        /**
         * log fatal error (system or technical error)
         *
         * @param errorsDetails
         * @throws VitamException
         */
        private void logFatalError(String errorsDetails) throws VitamException {
            LOGGER.error("There validation errors on the input file {}", errorsDetails);
            final LogbookOperationParameters logbookParameters = LogbookParametersFactory
                .newLogbookOperationParameters(eip, SECURITY_PROFILE_IMPORT_EVENT, eip, LogbookTypeProcess.MASTERDATA,
                    StatusCode.FATAL,
                    VitamLogbookMessages.getCodeOp(SECURITY_PROFILE_IMPORT_EVENT, StatusCode.FATAL), eip);
            logbookParameters
                .putParameterValue(LogbookParameterName.outcomeDetail, SECURITY_PROFILE_IMPORT_EVENT + "." +
                    StatusCode.FATAL);
            logbookMessageError(errorsDetails, logbookParameters);
            helper.updateDelegate(logbookParameters);
            logbookClient.bulkCreate(eip.getId(), helper.removeCreateDelegate(eip.getId()));
        }

        private void logbookMessageError(String errorsDetails, LogbookOperationParameters logbookParameters) {
            if (null != errorsDetails && !errorsDetails.isEmpty()) {
                try {
                    final ObjectNode object = JsonHandler.createObjectNode();
                    object.put("errors", errorsDetails);

                    final String wellFormedJson = SanityChecker.sanitizeJson(object);
                    logbookParameters.putParameterValue(LogbookParameterName.eventDetailData, wellFormedJson);
                } catch (final InvalidParseOperationException e) {
                    // Do nothing
                }
            }
        }

        /**
         * log start process
         *
         * @throws VitamException
         */
        private void logStarted() throws VitamException {
            eip = GUIDFactory.newOperationLogbookGUID(ParameterHelper.getTenantParameter());
            final LogbookOperationParameters logbookParameters = LogbookParametersFactory
                .newLogbookOperationParameters(eip, SECURITY_PROFILE_IMPORT_EVENT, eip, LogbookTypeProcess.MASTERDATA,
                    StatusCode.STARTED,
                    VitamLogbookMessages.getCodeOp(SECURITY_PROFILE_IMPORT_EVENT, StatusCode.STARTED), eip);
            logbookParameters
                .putParameterValue(LogbookParameterName.outcomeDetail, SECURITY_PROFILE_IMPORT_EVENT + "." +
                    StatusCode.STARTED);
            helper.createDelegate(logbookParameters);
        }

        /**
         * log end success process
         *
         * @throws VitamException
         */
        private void logImportSuccess() throws VitamException {
            final LogbookOperationParameters logbookParameters = LogbookParametersFactory
                .newLogbookOperationParameters(eip, SECURITY_PROFILE_IMPORT_EVENT, eip, LogbookTypeProcess.MASTERDATA,
                    StatusCode.OK,
                    VitamLogbookMessages.getCodeOp(SECURITY_PROFILE_IMPORT_EVENT, StatusCode.OK), eip);
            logbookParameters
                .putParameterValue(LogbookParameterName.outcomeDetail, SECURITY_PROFILE_IMPORT_EVENT + "." +
                    StatusCode.OK);
            helper.updateDelegate(logbookParameters);
            logbookClient.bulkCreate(eip.getId(), helper.removeCreateDelegate(eip.getId()));
        }

        /**
         * log update start process
         *
         * @throws VitamException
         */
        private void logUpdateStarted(String id) throws VitamException {
            eip = GUIDFactory.newOperationLogbookGUID(ParameterHelper.getTenantParameter());
            final LogbookOperationParameters logbookParameters = LogbookParametersFactory
                .newLogbookOperationParameters(eip, SECURITY_PROFILE_UPDATE_EVENT, eip, LogbookTypeProcess.MASTERDATA,
                    StatusCode.STARTED,
                    VitamLogbookMessages.getCodeOp(SECURITY_PROFILE_UPDATE_EVENT, StatusCode.STARTED), eip);
            logbookParameters.putParameterValue(LogbookParameterName.outcomeDetail, SECURITY_PROFILE_UPDATE_EVENT +
                "." + StatusCode.STARTED);
            if (null != id && !id.isEmpty()) {
                logbookParameters.putParameterValue(LogbookParameterName.objectIdentifier, id);
            }
            helper.createDelegate(logbookParameters);
        }

        private void logUpdateSuccess(String id, List<String> listDiffs) throws VitamException {
            final ObjectNode evDetData = JsonHandler.createObjectNode();
            final String diffs = listDiffs.stream().reduce("", String::concat);

            final ObjectNode msg = JsonHandler.createObjectNode();
            msg.put(UPDATED_DIFFS, diffs);

            evDetData.set(SECURITY_PROFILE, msg);
            final String wellFormedJson = SanityChecker.sanitizeJson(evDetData);
            final LogbookOperationParameters logbookParameters =
                LogbookParametersFactory
                    .newLogbookOperationParameters(
                        eip,
                        SECURITY_PROFILE_UPDATE_EVENT,
                        eip,
                        LogbookTypeProcess.MASTERDATA,
                        StatusCode.OK,
                        VitamLogbookMessages.getCodeOp(SECURITY_PROFILE_UPDATE_EVENT, StatusCode.OK),
                        eip);
            if (null != id && !id.isEmpty()) {
                logbookParameters.putParameterValue(LogbookParameterName.objectIdentifier, id);
            }
            logbookParameters.putParameterValue(LogbookParameterName.eventDetailData,
                wellFormedJson);
            logbookParameters.putParameterValue(LogbookParameterName.outcomeDetail, SECURITY_PROFILE_UPDATE_EVENT +
                "." + StatusCode.OK);
            helper.updateDelegate(logbookParameters);
            logbookClient.bulkCreate(eip.getId(), helper.removeCreateDelegate(eip.getId()));
        }
    }

    @Override
    public void close() {
        logbookClient.close();
    }

    private boolean isSlaveMode() {
        return vitamCounterService
            .isSlaveFunctionnalCollectionOnTenant(SequenceType.SECURITY_PROFILE_SEQUENCE.getCollection(),
                ParameterHelper.getTenantParameter());
    }
}
