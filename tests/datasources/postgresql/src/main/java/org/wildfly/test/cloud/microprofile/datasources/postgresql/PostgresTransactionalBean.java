/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.test.cloud.microprofile.datasources.postgresql;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;


@ApplicationScoped
public class PostgresTransactionalBean {

    @PersistenceContext(unitName = "from-galleon-pack")
    EntityManager galleonPackEm;

    @PersistenceContext(unitName = "generated-by-launch-scripts")
    EntityManager launchScriptsEm;

    @Transactional
    public void storeValueInGalleonPackDs(String value) {
        PostgresGalleonPackEntity entity = new PostgresGalleonPackEntity();
        entity.setValue(value);
        galleonPackEm.persist(entity);
    }

    @Transactional
    public List<String> getAllGalleonPackDsValues() {
        TypedQuery<PostgresGalleonPackEntity> query = galleonPackEm.createQuery("SELECT p from PostgresGalleonPackEntity p", PostgresGalleonPackEntity.class);
        List<String> values = query.getResultList().stream().map(v -> v.getValue()).collect(Collectors.toList());
        return values;
    }


    @Transactional
    public void storeValueInLaunchScriptDs(String value) {
        PostgresLaunchScriptEntity entity = new PostgresLaunchScriptEntity();
        entity.setValue(value);
        galleonPackEm.persist(entity);
    }

    @Transactional
    public List<String> getAllLaunchScriptDsValues() {
        TypedQuery<PostgresLaunchScriptEntity> query = galleonPackEm.createQuery("SELECT p from PostgresLaunchScriptEntity p", PostgresLaunchScriptEntity.class);
        List<String> values = query.getResultList().stream().map(v -> v.getValue()).collect(Collectors.toList());
        return values;
    }

}
