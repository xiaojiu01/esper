/*
 ***************************************************************************************
 *  Copyright (C) 2006 EsperTech, Inc. All rights reserved.                            *
 *  http://www.espertech.com/esper                                                     *
 *  http://www.espertech.com                                                           *
 *  ---------------------------------------------------------------------------------- *
 *  The software in this package is published under the terms of the GPL license       *
 *  a copy of which has been included with this distribution in the license.txt file.  *
 ***************************************************************************************
 */
package com.espertech.esper.common.internal.epl.agg.core;

import com.espertech.esper.common.client.serde.MultiKeyGeneratedSerde;
import com.espertech.esper.common.client.util.MultiKeyGenerated;

import java.util.Arrays;

public abstract class AggregationGroupByRollupLevel {
    private final int levelNumber;
    private final int levelOffset;
    private final int[] rollupKeys;
    private final MultiKeyGeneratedSerde optionalSubkeyMultikeySerde;

    public abstract Object computeSubkey(Object groupKey);

    public AggregationGroupByRollupLevel(int levelNumber, int levelOffset, int[] rollupKeys, MultiKeyGeneratedSerde optionalSubkeyMultikeySerde) {
        this.levelNumber = levelNumber;
        this.levelOffset = levelOffset;
        this.rollupKeys = rollupKeys;
        this.optionalSubkeyMultikeySerde = optionalSubkeyMultikeySerde;
    }

    public int getLevelNumber() {
        return levelNumber;
    }

    public boolean isAggregationTop() {
        return levelOffset == -1;
    }

    public int getAggregationOffset() {
        if (isAggregationTop()) {
            throw new IllegalArgumentException();
        }
        return levelOffset;
    }

    public int getLevelOffset() {
        return levelOffset;
    }

    public int[] getRollupKeys() {
        return rollupKeys;
    }

    public MultiKeyGeneratedSerde getOptionalSubkeyMultikeySerde() {
        return optionalSubkeyMultikeySerde;
    }

    public String toString() {
        return "GroupByRollupLevel{" +
            "levelOffset=" + levelOffset +
            ", rollupKeys=" + Arrays.toString(rollupKeys) +
            '}';
    }

    public Object[] computeMultiKey(Object subkey, int numExpected) {
        if (subkey instanceof MultiKeyGenerated) {
            MultiKeyGenerated mk = (MultiKeyGenerated) subkey;
            if (mk.getNumKeys() == numExpected) {
                return MultiKeyGenerated.toObjectArray(mk);
            }
            Object[] keys = new Object[]{numExpected};
            for (int i = 0; i < rollupKeys.length; i++) {
                keys[rollupKeys[i]] = mk.getKey(i);
            }
            return keys;
        }
        Object[] keys = new Object[numExpected];
        if (subkey == null) {
            return keys;
        }
        keys[rollupKeys[0]] = subkey;
        return keys;
    }
}
