/*
 * Copyright (C) 2018 The Sylph Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ideal.sylph.runner.spark;

import com.github.harbby.gadtry.classloader.DirClassLoader;
import com.github.harbby.gadtry.ioc.IocFactory;
import ideal.sylph.spi.Runner;
import ideal.sylph.spi.RunnerContext;
import ideal.sylph.spi.job.ContainerFactory;
import ideal.sylph.spi.job.JobActuatorHandle;
import ideal.sylph.spi.model.PipelinePluginInfo;
import ideal.sylph.spi.model.PipelinePluginManager;

import java.io.File;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.harbby.gadtry.base.Throwables.throwsException;
import static com.google.common.base.Preconditions.checkArgument;
import static ideal.sylph.spi.model.PipelinePluginManager.filterRunnerPlugins;
import static java.util.Objects.requireNonNull;

public class SparkRunner
        implements Runner
{
    @Override
    public Set<JobActuatorHandle> create(RunnerContext context)
    {
        requireNonNull(context, "context is null");
        String sparkHome = requireNonNull(System.getenv("SPARK_HOME"), "SPARK_HOME not setting");
        checkArgument(new File(sparkHome).exists(), "SPARK_HOME " + sparkHome + " not exists");

        ClassLoader classLoader = this.getClass().getClassLoader();
        try {
            if (classLoader instanceof DirClassLoader) {
                ((DirClassLoader) classLoader).addDir(new File(sparkHome, "jars"));
            }

            IocFactory injector = IocFactory.create(
                    binder -> {
                        binder.bind(StreamEtlActuator.class).withSingle();
                        binder.bind(Stream2EtlActuator.class).withSingle();
                        binder.bind(SparkSubmitActuator.class).withSingle();
                        binder.bind(SparkStreamingSqlActuator.class).withSingle();
                        binder.bind(StructuredStreamingSqlActuator.class).withSingle();
                        //------------------------
                        binder.bind(RunnerContext.class).byInstance(context);
                    });

            return Stream.of(
                    StreamEtlActuator.class,
                    Stream2EtlActuator.class,
                    SparkSubmitActuator.class,
                    SparkStreamingSqlActuator.class,
                    StructuredStreamingSqlActuator.class
            ).map(injector::getInstance).collect(Collectors.toSet());
        }
        catch (Exception e) {
            throw throwsException(e);
        }
    }

    @Override
    public Class<? extends ContainerFactory> getContainerFactory()
    {
        return SparkContainerFactory.class;
    }

    public static PipelinePluginManager createPipelinePluginManager(RunnerContext context, Collection<Class<?>> filterClass)
    {
        final Set<String> keyword = filterClass.stream().map(Class::getName).collect(Collectors.toSet());
        Set<PipelinePluginInfo> pluginInfos = filterRunnerPlugins(context.getFindPlugins(), keyword, SparkRunner.class);
        return new PipelinePluginManager()
        {
            @Override
            public Set<PipelinePluginInfo> getAllPlugins()
            {
                return pluginInfos;
            }
        };
    }
}
