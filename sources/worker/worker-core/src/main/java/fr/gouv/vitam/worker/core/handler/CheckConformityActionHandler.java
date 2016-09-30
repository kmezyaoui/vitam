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
package fr.gouv.vitam.worker.core.handler;

import java.net.URISyntaxException;
import java.util.List;

import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.logbook.common.parameters.LogbookOperationParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookParametersFactory;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.model.EngineResponse;
import fr.gouv.vitam.processing.common.model.OutcomeMessage;
import fr.gouv.vitam.processing.common.model.ProcessResponse;
import fr.gouv.vitam.processing.common.model.StatusCode;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.worker.common.utils.SedaUtils;
import fr.gouv.vitam.worker.common.utils.SedaUtilsFactory;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageException;

/**
 * Check conformity handler
 */
public class CheckConformityActionHandler extends ActionHandler {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(CheckConformityActionHandler.class);
    private static final String HANDLER_ID = "CheckConformity";
    private final SedaUtilsFactory sedaUtilsFactory;
    LogbookOperationParameters parameters = LogbookParametersFactory.newLogbookOperationParameters();

    /**
     * Constructor CheckConformityActionHandler with parameter SedaUtilsFactory
     * 
     * @param factory SedaUtils factory
     */
    public CheckConformityActionHandler(SedaUtilsFactory factory) {
        sedaUtilsFactory = factory;
    }

    /**
     * @return HANDLER_ID
     */
    public static final String getId() {
        return HANDLER_ID;
    }

    @Override
    public EngineResponse execute(WorkerParameters params) throws ProcessingException {
        checkMandatoryParameters(params);
        LOGGER.debug("CheckConformityActionHandler running ...");

        final EngineResponse response = new ProcessResponse().setStatus(StatusCode.OK).setOutcomeMessages(HANDLER_ID,
            OutcomeMessage.CHECK_CONFORMITY_OK);

        final SedaUtils sedaUtils = sedaUtilsFactory.create();

        try {
            List<String> digestMessageInvalidList = sedaUtils.checkConformityBinaryObject(params);
            if (!digestMessageInvalidList.isEmpty()) {
                response.setErrorNumber(digestMessageInvalidList.size());
                response.setStatus(StatusCode.KO);
                response.setOutcomeMessages(HANDLER_ID, OutcomeMessage.CHECK_CONFORMITY_KO);
            }
        } catch (ProcessingException | ContentAddressableStorageException e) {
            LOGGER.error(e);
            response.setStatus(StatusCode.KO);
            response.setOutcomeMessages(HANDLER_ID, OutcomeMessage.CHECK_CONFORMITY_KO);
        } catch (URISyntaxException e) {
            LOGGER.error(e);
            response.setStatus(StatusCode.FATAL);
            response.setOutcomeMessages(HANDLER_ID, OutcomeMessage.CHECK_CONFORMITY_KO);
        }

        LOGGER.debug("CheckConformityActionHandler response: ", response.getStatus().name());
        return response;
    }

}
