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
package fr.gouv.vitam.logbook.common.model.response;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;

import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;

public class RequestResponseOKTest {

    @Test
    public void testRequestResponseOK() throws InvalidParseOperationException {
        RequestResponseOK requestOK = new RequestResponseOK();
        DatabaseCursor cursor = new DatabaseCursor(0, 0, 0);
        cursor.setTotal(1).setOffset(0).setLimit(10);
        requestOK.setHits(cursor);
        assertEquals(10, requestOK.getHits().getLimit());
        assertEquals(0, requestOK.getHits().getOffset());
        assertEquals(1, requestOK.getHits().getTotal());
        
        requestOK.setHits(2, 0, 100);
        assertEquals(100, requestOK.getHits().getLimit());
        assertEquals(0, requestOK.getHits().getOffset());
        assertEquals(2, requestOK.getHits().getTotal());
        
        JsonNode test = JsonHandler.getFromString("{}");
        requestOK.setQuery(test).setResult(test);
        assertEquals(test, requestOK.getQuery());
        assertEquals(test, requestOK.getResult());
        
        
    }


}
