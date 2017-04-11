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
package fr.gouv.vitam.functional.administration.contract.core;

import fr.gouv.vitam.functional.administration.client.model.AbstractContractModel;

import java.util.Optional;

/**
 * Used to validate contracts (any class that extends AbstractContractModel) and to apply acceptance rules.
 *
 * Bellow the example of usage :
 *<pre>
 * {@code
 * private static GenericContractValidator checkDuplicateInDatabaseValidator() {
 *    return (contract, contractName) -> {
 *        GenericRejectionCause rejection = null;
 *        boolean exist = ... exists in database?;
 *        if (exist) {
 *           rejection = GenericRejectionCause.rejectDuplicatedInDatabase(contractName);
 *        }
 *        return (rejection == null) ? Optional.empty() : Optional.of(rejection);
 *    };
 * }
 * }
 * </pre>
 *
 * The call the method like this to validate the contract c:
 *
 * GenericRejectionCause rejection = checkDuplicateInDatabaseValidator().validate(c, c.getName());
 *
 * Check if rejection is present then do the resolution
 *
 * @param <T>
 */
@FunctionalInterface
public interface GenericContractValidator<T extends AbstractContractModel> {



    /**
     * Validate a contract object
     * 
     * @param contract to validate
     * @param contractName
     * @return empty optional if OK, Else return the rejection cause
     */
    Optional<GenericRejectionCause> validate(T contract, String contractName);


    public class GenericRejectionCause {

        private static final String ERR_INVALID_CONTRACT = "The input contract json is is invalid";
        public static String ERR_DUPLICATE_CONTRACT_ENTRY = "One or many contracts in the imported list have the same name : %s";
        public static String ERR_ID_NOT_ALLOWED_IN_CREATE = "Id must be null when creating contracts (%s)";
        public static String ERR_DUPLICATE_CONTRACT = "The contract %s already exists in database";
        public static String ERR_INVALID_FIELD = "The field %s has an invalid format";
        public static String ERR_MANDATORY_FIELD = "The field %s is mandatory";

        private String reason;
        
        public GenericRejectionCause(String error) {
            setReason(error);
        }

        /**
         * Reject if id exisit and the action is creation.
         * If id exists, it should be an update instead of create
         *
         * @param contractName
         * @return
         */
        public static GenericRejectionCause rejectIdNotAllowedInCreate(String contractName){
            return new GenericRejectionCause(String.format(ERR_ID_NOT_ALLOWED_IN_CREATE , contractName));
        }

        /**
         * Reject if multiple contract have the same name in the same request before persist into database.
         * The contract name must be unique
         *
         * @param contractName
         * @return
         */
        public static GenericRejectionCause rejectDuplicatedEntry(String contractName){
            return new GenericRejectionCause(String.format(ERR_DUPLICATE_CONTRACT_ENTRY , contractName));
        }

        /**
         * Verify for each contract if already exists one in database that have the same name.
         * The database my manage this kind of constraint (by creating an unique index on the field or column)
         * @param contractName
         * @return
         */
        public static GenericRejectionCause rejectDuplicatedInDatabase(String contractName){
            return new GenericRejectionCause(String.format(ERR_DUPLICATE_CONTRACT , contractName));
        }

        /**
         * Reject if one of multiple mandatory parameter are null
         * @param fieldName
         * @return
         */
        public static GenericRejectionCause rejectMandatoryMissing(String fieldName){
            return new GenericRejectionCause(String.format(ERR_MANDATORY_FIELD , fieldName));
        }

        public String getReason() {
            return reason;
        }

        private void setReason(String reason) {
            this.reason = reason;
        }
    }
}