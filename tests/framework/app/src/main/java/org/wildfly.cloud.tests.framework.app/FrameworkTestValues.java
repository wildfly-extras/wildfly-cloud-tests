/*
 * JBoss, Home of Professional Open Source.
 *  Copyright 2022 Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.wildfly.cloud.tests.framework.app;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class FrameworkTestValues {

    private volatile String propertyA;
    private volatile String propertyB;
    private volatile String propertyC;
    private volatile String propertyD;

    public FrameworkTestValues(String propertyA, String propertyB, String propertyC, String propertyD) {
        this.propertyA = propertyA;
        this.propertyB = propertyB;
        this.propertyC = propertyC;
        this.propertyD = propertyD;
    }

    public FrameworkTestValues() {

    }


    public void setPropertyA(String propertyA) {
        this.propertyA = propertyA;
    }

    public String getPropertyA() {
        return propertyA;
    }

    public void setPropertyB(String propertyB) {
        this.propertyB = propertyB;
    }

    public String getPropertyB() {
        return propertyB;
    }

    public String getPropertyC() {
        return propertyC;
    }

    public void setPropertyC(String propertyC) {
        this.propertyC = propertyC;
    }

    public String getPropertyD() {
        return propertyD;
    }

    public void setPropertyD(String propertyD) {
        this.propertyD = propertyD;
    }
}
