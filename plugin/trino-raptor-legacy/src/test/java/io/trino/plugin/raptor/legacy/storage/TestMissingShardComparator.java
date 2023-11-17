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
package io.trino.plugin.raptor.legacy.storage;

import org.junit.jupiter.api.Test;

import static io.trino.plugin.raptor.legacy.storage.ShardRecoveryManager.MissingShardComparator;
import static io.trino.plugin.raptor.legacy.storage.ShardRecoveryManager.MissingShardRunnable;
import static org.assertj.core.api.Assertions.assertThat;

public class TestMissingShardComparator
{
    @Test
    public void testOrdering()
    {
        MissingShardComparator comparator = new MissingShardComparator();
        assertThat(comparator.compare(new DummyMissingShardRunnable(false), new DummyMissingShardRunnable(false))).isEqualTo(0);
        assertThat(comparator.compare(new DummyMissingShardRunnable(false), new DummyMissingShardRunnable(true))).isEqualTo(1);
        assertThat(comparator.compare(new DummyMissingShardRunnable(true), new DummyMissingShardRunnable(false))).isEqualTo(-1);
        assertThat(comparator.compare(new DummyMissingShardRunnable(true), new DummyMissingShardRunnable(true))).isEqualTo(0);
    }

    private static class DummyMissingShardRunnable
            implements MissingShardRunnable
    {
        private final boolean active;

        DummyMissingShardRunnable(boolean active)
        {
            this.active = active;
        }

        @Override
        public boolean isActive()
        {
            return active;
        }

        @Override
        public void run()
        {
            throw new UnsupportedOperationException();
        }
    }
}
