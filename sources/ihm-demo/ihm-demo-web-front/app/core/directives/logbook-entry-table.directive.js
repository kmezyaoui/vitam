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

/*
 Used to select the displayed column of a dynamic table

 It takes some mandatory parameters:
 custom-fields: Array of items for the editable fields that can be added as column of the table
  The objects in custom-fields must have at least id (Technical and unique value) and label (User friendly value) column.
  These objects can have a 'order' number attribute used to display columns in the exactly same order each time.
 selectedObjects: Object selected by the dynamic array (items come from custom fields
  selectedObjects will return a list of item selected
 */

'use strict';

angular.module('ihm.demo')
  .controller('logbookEntryTableController', function($scope, ihmDemoCLient, logbookEntryFullService) {
    ihmDemoCLient.getClient('logbook/operations').all($scope.operationId).post({}).then(function(response) {
      $scope.detail = response.data.$results[0];
    })
    $scope.selectStyleByStatus = function(status){
      return logbookEntryFullService.selectClassByStatus(status);
    };
    $scope.getClassByevType = function(eventType) {
      if (eventType.startsWith('STP') ) {
        return 'logbookStep';
      }
      if (eventType.indexOf('.') > -1 && eventType.indexOf('CHECK') > -1 ) {
        return 'logbookSubTask';
      }
      return 'logbookTask' ;
    }
  })
  .directive('logbookEntryTable', function()  {
    return {
      require : "ngModel",
      restrict: 'E',
      scope: {
        operationId: '=id'
      },
      controller: 'logbookEntryTableController',
      templateUrl: 'core/directives/logbook-entry-table.directive.html'
    };
  });
