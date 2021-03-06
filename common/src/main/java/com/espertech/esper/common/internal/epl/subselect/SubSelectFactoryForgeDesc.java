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
package com.espertech.esper.common.internal.epl.subselect;

import com.espertech.esper.common.internal.compile.stage3.StmtClassForgableFactory;

import java.util.List;

public class SubSelectFactoryForgeDesc {
    private final SubSelectFactoryForge subSelectFactoryForge;
    private final List<StmtClassForgableFactory> additionalForgeables;

    public SubSelectFactoryForgeDesc(SubSelectFactoryForge subSelectFactoryForge, List<StmtClassForgableFactory> additionalForgeables) {
        this.subSelectFactoryForge = subSelectFactoryForge;
        this.additionalForgeables = additionalForgeables;
    }

    public SubSelectFactoryForge getSubSelectFactoryForge() {
        return subSelectFactoryForge;
    }

    public List<StmtClassForgableFactory> getAdditionalForgeables() {
        return additionalForgeables;
    }
}
