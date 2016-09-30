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
package fr.gouv.vitam.logbook.operations.api;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.logbook.common.parameters.LogbookOperationParameters;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookOperation;
import fr.gouv.vitam.logbook.common.server.exception.LogbookAlreadyExistsException;
import fr.gouv.vitam.logbook.common.server.exception.LogbookDatabaseException;
import fr.gouv.vitam.logbook.common.server.exception.LogbookNotFoundException;

/**
 * Logbook operations interface for database operations
 */
public interface LogbookOperations {

    /**
     * Create and insert logbook operation entries
     *
     * @param parameters the entry parameters
     * @throws fr.gouv.vitam.logbook.common.server.exception.LogbookAlreadyExistsException if an operation with the same
     *         eventIdentifierProcess and outcome="Started" already exists
     * @throws fr.gouv.vitam.logbook.common.server.exception.LogbookDatabaseException if errors occur while connecting
     *         or writing to the database
     */
    void create(LogbookOperationParameters parameters) throws LogbookAlreadyExistsException, LogbookDatabaseException;

    /**
     * Update and insert logbook operation entries
     *
     * @param parameters the entry parameters
     * @throws fr.gouv.vitam.logbook.common.server.exception.LogbookNotFoundException if no operation with the same
     *         eventIdentifierProcess exists
     * @throws fr.gouv.vitam.logbook.common.server.exception.LogbookDatabaseException if errors occur while connecting
     *         or writing to the database
     */
    void update(LogbookOperationParameters parameters) throws LogbookNotFoundException, LogbookDatabaseException;

    /**
     * Select logbook operation entries
     *
     * @param select the select request in format of JsonNode
     * @return List of the logbook operation
     * @throws LogbookNotFoundException if no operation selected cannot be found
     * @throws LogbookDatabaseException if errors occur while connecting or writing to the database
     * @throws InvalidParseOperationException if invalid parse for selecting the operation
     */
    List<LogbookOperation> select(JsonNode select)
        throws LogbookDatabaseException, LogbookNotFoundException, InvalidParseOperationException;

    /**
     * Select logbook operation by the operation's ID
     *
     * @param IdProcess
     * @return the logbook operation found by the ID
     * @throws LogbookDatabaseException if errors occur while connecting or writing to the database
     * @throws LogbookNotFoundException if no operation selected cannot be found
     */
    LogbookOperation getById(String IdProcess) throws LogbookDatabaseException, LogbookNotFoundException;
}
