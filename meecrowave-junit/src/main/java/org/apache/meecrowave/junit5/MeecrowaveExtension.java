/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.meecrowave.junit5;

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.testing.Injector;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ContainerExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExtensionContext;

import javax.enterprise.context.spi.CreationalContext;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

public class MeecrowaveExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(MeecrowaveExtension.class.getName());

    @Override
    public void beforeAll(final ContainerExtensionContext context) throws Exception {
        final Meecrowave.Builder builder = new Meecrowave.Builder();
        final Optional<MeecrowaveConfig> meecrowaveConfig = context.getElement().map(e -> e.getAnnotation(MeecrowaveConfig.class));
        final String ctx;
        if (meecrowaveConfig.isPresent()) {
            final MeecrowaveConfig config = meecrowaveConfig.get();
            ctx = config.context();

            for (final Method method : MeecrowaveConfig.class.getMethods()) {
                if (MeecrowaveConfig.class != method.getDeclaringClass()) {
                    continue;
                }

                try {
                    final Object value = method.invoke(config);

                    final Field configField = Meecrowave.Builder.class.getDeclaredField(method.getName());
                    if (!configField.isAccessible()) {
                        configField.setAccessible(true);
                    }

                    if (value != null && (!String.class.isInstance(value) || !value.toString().isEmpty())) {
                        if (!configField.isAccessible()) {
                            configField.setAccessible(true);
                        }
                        configField.set(builder, File.class == configField.getType() ? /*we use string instead */new File(value.toString()) : value);
                    }
                } catch (final NoSuchFieldException nsfe) {
                    // ignored
                } catch (final Exception e) {
                    throw new IllegalStateException(e);
                }
            }

            if (builder.getHttpPort() < 0) {
                builder.randomHttpPort();
            }
        } else {
            ctx = "";
        }
        final Meecrowave meecrowave = new Meecrowave(builder);
        context.getStore(NAMESPACE).put(Meecrowave.class.getName(), meecrowave);
        context.getStore(NAMESPACE).put(Meecrowave.Builder.class.getName(), builder);
        meecrowave.bake(ctx);
    }

    @Override
    public void afterAll(final ContainerExtensionContext context) throws Exception {
        Meecrowave.class.cast(context.getStore(NAMESPACE).get(Meecrowave.class.getName())).close();
    }

    @Override
    public void beforeEach(final TestExtensionContext context) throws Exception {
        context.getStore(NAMESPACE).put(CreationalContext.class.getName(), Injector.inject(context.getTestInstance()));
        Injector.injectConfig(Meecrowave.Builder.class.cast(context.getStore(NAMESPACE).get(Meecrowave.Builder.class.getName())), context.getTestInstance());
    }

    @Override
    public void afterEach(final TestExtensionContext context) throws Exception {
        CreationalContext.class.cast(context.getStore(NAMESPACE).get(CreationalContext.class.getName())).release();
    }
}
