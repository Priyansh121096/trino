/*
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
package io.trino.plugin.deltalake;

import io.trino.metastore.HiveMetastore;

import java.nio.file.Path;

import static io.trino.plugin.hive.metastore.glue.TestingGlueHiveMetastore.createTestingGlueHiveMetastore;

/**
 * Requires AWS credentials, which can be provided any way supported by the DefaultProviderChain
 * See https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html#credentials-default
 */
public class TestDeltaLakeSharedGlueMetastoreViews
        extends BaseDeltaLakeSharedMetastoreViewsTest
{
    @Override
    protected HiveMetastore createTestMetastore(Path dataDirectory)
    {
        return createTestingGlueHiveMetastore(dataDirectory, this::closeAfterClass);
    }
}
