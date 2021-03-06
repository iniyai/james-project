/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.linshare;

import java.time.Duration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

public class Linshare {
    private static final String WAIT_FOR_BACKEND_INIT_LOG = ".*Server startup.*";
    private static final String WAIT_FOR_DB_INIT_LOG = ".*/linshare/webservice/rest/admin/authentication/change_password.*";
    private static final int LINSHARE_BACKEND_PORT = 8080;

    private final GenericContainer<?> linshareBackend;
    private final GenericContainer<?> linshareDatabase;
    private final GenericContainer<?> linshareSmtp;
    private final GenericContainer<?> linshareLdap;
    private final GenericContainer<?> linshareMongodb;

    private Network network;

    @SuppressWarnings("resource")
    public Linshare() {
        network = Network.newNetwork();
        linshareDatabase = createDockerDatabase();
        linshareMongodb = createDockerMongodb();
        linshareLdap = createDockerLdap();
        linshareSmtp = createDockerSmtp();
        linshareBackend = createDockerBackend();
    }

    public void start() {
        linshareDatabase.start();
        linshareMongodb.start();
        linshareLdap.start();
        linshareSmtp.start();
        linshareBackend.start();

        LDAPConfigurationPerformer.configureLdap(this);
    }

    public void stop() {
        linshareDatabase.stop();
        linshareMongodb.stop();
        linshareLdap.stop();
        linshareSmtp.stop();
        linshareBackend.stop();
    }

    public int getPort() {
        return linshareBackend.getMappedPort(LINSHARE_BACKEND_PORT);
    }

    public String getIp() {
        return linshareBackend.getContainerIpAddress();
    }

    public String getUrl() {
        return "http://" + getIp() + ":" + getPort();
    }

    private GenericContainer createDockerDatabase() {
        return new GenericContainer<>("linagora/linshare-database:2.2")
            .withNetworkAliases("database", "linshare_database")
            .withEnv("PGDATA", "/var/lib/postgresql/data/pgdata")
            .withEnv("POSTGRES_USER", "linshare")
            .withEnv("POSTGRES_PASSWORD", "linshare")
            .withNetwork(network);
    }

    private GenericContainer createDockerMongodb() {
        return new GenericContainer<>("mongo:3.2")
            .withNetworkAliases("mongodb", "linshare_mongodb")
            .withCommand("mongod --smallfiles")
            .withNetwork(network);
    }

    private GenericContainer createDockerLdap() {
        return new GenericContainer<>("linagora/linshare-ldap-for-tests:1.0")
            .withNetworkAliases("ldap")
            .withNetwork(network);
    }

    private GenericContainer createDockerSmtp() {
        return new GenericContainer<>(
            new ImageFromDockerfile()
                .withFileFromClasspath("conf/smtpd.conf", "smtp/conf/smtpd.conf")
                .withFileFromClasspath("Dockerfile", "smtp/Dockerfile"))
            .withNetworkAliases("smtp", "linshare_smtp")
            .withNetwork(network);
    }

    private GenericContainer createDockerBackend() {
        return new GenericContainer<>(
            new ImageFromDockerfile()
                .withFileFromClasspath("conf/log4j.properties", "backend/conf/log4j.properties")
                .withFileFromClasspath("conf/catalina.properties", "backend/conf/catalina.properties")
                .withFileFromClasspath("conf/id_rsa", "backend/conf/id_rsa")
                .withFileFromClasspath("conf/id_rsa.pub", "backend/conf/id_rsa.pub")
                .withFileFromClasspath("Dockerfile", "backend/Dockerfile"))
            .withNetworkAliases("backend")
            .withEnv("SMTP_HOST", "linshare_smtp")
            .withEnv("SMTP_PORT", "25")
            .withEnv("POSTGRES_HOST", "linshare_database")
            .withEnv("POSTGRES_PORT", "5432")
            .withEnv("POSTGRES_USER", "linshare")
            .withEnv("POSTGRES_PASSWORD", "linshare")
            .withEnv("MONGODB_HOST", "linshare_mongodb")
            .withEnv("MONGODB_PORT", "27017")
            .withEnv("THUMBNAIL_ENABLE", "false")
            .withExposedPorts(LINSHARE_BACKEND_PORT)
            .waitingFor(Wait.forLogMessage(WAIT_FOR_BACKEND_INIT_LOG, 1)
                .withStartupTimeout(Duration.ofMinutes(10)))
            .withNetwork(network);
    }
}
