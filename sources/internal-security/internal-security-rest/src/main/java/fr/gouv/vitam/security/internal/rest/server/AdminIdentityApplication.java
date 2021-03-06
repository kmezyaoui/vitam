package fr.gouv.vitam.security.internal.rest.server;

import static fr.gouv.vitam.common.serverv2.application.ApplicationParameter.CONFIGURATION_FILE_APPLICATION;

import com.fasterxml.jackson.jaxrs.base.JsonParseExceptionMapper;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.database.collections.VitamCollection;
import fr.gouv.vitam.common.database.server.mongodb.MongoDbAccess;
import fr.gouv.vitam.common.server.HeaderIdContainerFilter;
import fr.gouv.vitam.common.serverv2.application.AdminApplication;
import fr.gouv.vitam.security.internal.rest.SimpleMongoDBAccess;
import fr.gouv.vitam.security.internal.rest.mapper.CertificateExceptionMapper;
import fr.gouv.vitam.security.internal.rest.mapper.IllegalArgumentExceptionMapper;
import fr.gouv.vitam.security.internal.rest.repository.IdentityRepository;
import fr.gouv.vitam.security.internal.rest.resource.AdminIdentityResource;
import fr.gouv.vitam.security.internal.rest.service.IdentityService;

import javax.servlet.ServletConfig;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * rest module declaring admin resource
 */
public class AdminIdentityApplication extends Application {

    private final AdminApplication adminApplication;
    private Set<Object> singletons;
    private String configurationFile;

    public AdminIdentityApplication(@Context ServletConfig servletConfig) {
        this.configurationFile = servletConfig.getInitParameter(CONFIGURATION_FILE_APPLICATION);
        this.adminApplication = new AdminApplication();
    }

    @Override
    public Set<Object> getSingletons() {
        if (singletons == null) {

            singletons = adminApplication.getSingletons();

            try (final InputStream yamlIS = PropertiesUtils.getConfigAsStream(configurationFile)) {
                final InternalSecurityConfiguration configuration =
                    PropertiesUtils.readYaml(yamlIS, InternalSecurityConfiguration.class);

                MongoClientOptions mongoClientOptions = VitamCollection.getMongoClientOptions();

                MongoClient mongoClient = MongoDbAccess.createMongoClient(configuration, mongoClientOptions);

                SimpleMongoDBAccess mongoDbAccess = new SimpleMongoDBAccess(mongoClient, configuration.getDbName());

                IdentityRepository identityRepository = new IdentityRepository(mongoDbAccess);
                IdentityService identityService = new IdentityService(identityRepository);
                singletons.add(new AdminIdentityResource(identityService));

                singletons.add(new CertificateExceptionMapper());
                singletons.add(new IllegalArgumentExceptionMapper());
                singletons.add(new HeaderIdContainerFilter());
                singletons.add(new JsonParseExceptionMapper());


            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return singletons;
    }

}
