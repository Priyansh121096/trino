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
package io.trino.type;

import io.airlift.slice.Slice;
import io.trino.spi.block.Block;
import io.trino.spi.block.BlockBuilder;
import io.trino.spi.block.VariableWidthBlock;
import io.trino.spi.block.VariableWidthBlockBuilder;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.type.AbstractVariableWidthType;
import io.trino.spi.type.TypeSignature;

import java.util.Optional;

import static io.airlift.slice.Slices.utf8Slice;
import static java.nio.charset.StandardCharsets.UTF_8;

public class LikePatternType
        extends AbstractVariableWidthType
{
    public static final LikePatternType LIKE_PATTERN = new LikePatternType();
    public static final String NAME = "LikePattern";

    private LikePatternType()
    {
        super(new TypeSignature(NAME), LikePattern.class);
    }

    @Override
    public Object getObjectValue(ConnectorSession session, Block block, int position)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getObject(Block block, int position)
    {
        if (block.isNull(position)) {
            return null;
        }

        VariableWidthBlock valueBlock = (VariableWidthBlock) block.getUnderlyingValueBlock();
        int valuePosition = block.getUnderlyingValuePosition(position);
        Slice slice = valueBlock.getSlice(valuePosition);

        // layout is: <pattern length> <pattern> <hasEscape> <escape>?
        int length = slice.getInt(0);
        String pattern = slice.toString(4, length, UTF_8);

        boolean hasEscape = slice.getByte(4 + length) != 0;

        Optional<Character> escape = Optional.empty();
        if (hasEscape) {
            escape = Optional.of((char) slice.getInt(4 + length + 1));
        }

        return LikePattern.compile(pattern, escape);
    }

    @Override
    public void writeObject(BlockBuilder blockBuilder, Object value)
    {
        LikePattern likePattern = (LikePattern) value;
        ((VariableWidthBlockBuilder) blockBuilder).buildEntry(valueWriter -> {
            Slice pattern = utf8Slice(likePattern.getPattern());
            int length = pattern.length();
            valueWriter.writeInt(length);
            valueWriter.writeBytes(pattern, 0, length);
            if (likePattern.getEscape().isEmpty()) {
                valueWriter.writeByte(0);
            }
            else {
                valueWriter.writeByte(1);
                valueWriter.writeInt(likePattern.getEscape().get());
            }
        });
    }
}
