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

describe('accessionRegisterService', function() {
  beforeEach(module('ihm.demo'));

  var AccessionRegisterService, AccessionRegisterResource, /*$httpBackend, */$cookies, $q;

  var identifier = '001';
  var $scope = {};
  var headers = {};

  beforeEach(inject(function ($injector) {
    AccessionRegisterService = $injector.get('accessionRegisterService');
    AccessionRegisterResource = $injector.get('accessionRegisterResource');
    $q = $injector.get('$q');

    $cookies = $injector.get('$cookies');
    $cookies.put('tenantId', 0);
    headers['X-Tenant-Id'] = $cookies.get('tenantId');
  }));

  it('should transfer the response from resource to service callback for getDetails', function() {
    var detailsCallback = function(response) {
      $scope.response = response;
      expect($scope.response.length).toEqual(1);
      expect($scope.response[0].id).toEqual(identifier);
      done();
    };

    spyOn(AccessionRegisterResource, 'getDetails').and.callFake(function (id, criteria) {
      var deferred = $q.defer();
      var result = {data: {$hints: [], $results: [{id: id}]}};
      deferred.resolve(result);
      return deferred.promise;
    });

    AccessionRegisterService.getDetails(identifier, detailsCallback);
  });

  it('should transfer the response from resource to service callback for getSummary', function() {
    var summaryCallback = function(response) {
      $scope.response = response;
      expect($scope.response.item).toEqual('value');
    };

    spyOn(AccessionRegisterResource, 'getSummary').and.callFake(function (criteria) {
      var deferred = $q.defer();
      var result = {data: {$hints: [], $results: [{item: 'value'}]}};
      deferred.resolve(result);
      return deferred.promise;
    });

    AccessionRegisterService.getSummary(identifier, summaryCallback);
  });

});
