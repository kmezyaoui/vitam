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

'use strict';

angular.module('contexts')
    .controller('contextsController', function ($http, $scope, $mdDialog, $filter, $window, ihmDemoCLient, ihmDemoFactory, ITEM_PER_PAGE, loadStaticValues, $translate, processSearchService, resultStartService) {

        $scope.startFormat = resultStartService.startFormat;

        $scope.search = {
          form: {
            ContextID: '',
            ContextName: ''
          }, pagination: {
            currentPage: 0,
            resultPages: 0,
            itemsPerPage: ITEM_PER_PAGE
          }, error: {
            message: '',
            displayMessage: false
          }, response: {
            data: [],
            hints: {},
            totalResult: 0
          }, dynamicTable : {
            customFields: [{ id : '_id', label : 'GUID'}, { id : 'hasAccesContract', label : 'Contrat d\'accès'}, { id : 'hasIngestContract', label : 'Contrat d\'entrée'} ],
            selectedObjects: []
          }
        };

        $scope.goToDetails = function (id) {
            $window.open('#!/admin/contexts/' + id)
        };

        $scope.shouldShowItem = function(name) {
          var showItem = false;
          $scope.search.dynamicTable.selectedObjects.forEach(function(value) {
            if (value.id == name) {
              showItem = true;
            }
          });
          return showItem;
        };
        function initFields(fields) {
            var result = [];
            for (var i = 0, len = fields.length; i < len; i++) {
                var fieldId = fields[i];
                result.push({
                    id: fieldId, label: 'operation.logbook.displayField.' + fieldId
                });
            }
            return result;
        }

        var preSearch = function () {
            var requestOptions = angular.copy($scope.search.form);

            if (requestOptions.ContextName == "" || requestOptions.ContextName == undefined) {
                requestOptions.ContextName = "all";
            }

            if (requestOptions.ContextID == "" || requestOptions.ContextID == undefined) {
                requestOptions.ContextID = "all";
            }
            requestOptions.orderby = {
                field: 'Name', sortType: 'ASC'
            };
            return requestOptions;
        };

        var successCallback = function () {
          console.log ($scope.search.response.data);
          $scope.search.response.data.forEach(function(value) {
            value.hasAccesContract = false;
            value.hasIngestContract = false;
            if (value.Permissions != null) {
              value.Permissions.forEach(function(permission) {
                if (permission.IngestContracts != null && permission.IngestContracts.length > 0) {
                  value.hasIngestContract = true;
                }
                if (permission.AccessContracts != null && permission.AccessContracts.length > 0) {
                  value.hasAccesContract = true;
                }
              })
            }
          });

          return true;
        };

        var computeErrorMessage = function () {
            if ($scope.search.form.ContextName && $scope.search.form.ContextID) {
                return 'Veuillez ne remplir qu\'un seul champ';
            } else {
                return 'Il n\'y a aucun résultat pour votre recherche';
            }
        };

        var customPost = function (criteria, headers) {
            return ihmDemoCLient.getClient('contexts').all('').post(criteria);
        };

        var searchService = processSearchService.initAndServe(customPost, preSearch, successCallback, computeErrorMessage, $scope.search, true);
        $scope.getList = searchService.processSearch;
        $scope.reinitForm = searchService.processReinit;
        $scope.onInputChange = searchService.onInputChange;
        console.log("searchService => ", searchService);
    });


