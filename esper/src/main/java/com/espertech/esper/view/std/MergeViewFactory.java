/**************************************************************************************
 * Copyright (C) 2006-2015 EsperTech Inc. All rights reserved.                        *
 * http://www.espertech.com/esper                                                          *
 * http://www.espertech.com                                                           *
 * ---------------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the GPL license       *
 * a copy of which has been included with this distribution in the license.txt file.  *
 **************************************************************************************/
package com.espertech.esper.view.std;

import com.espertech.esper.client.EventType;
import com.espertech.esper.client.PropertyAccessException;
import com.espertech.esper.core.context.util.AgentInstanceContext;
import com.espertech.esper.epl.expression.core.ExprNode;
import com.espertech.esper.core.context.util.AgentInstanceViewFactoryChainContext;
import com.espertech.esper.core.service.StatementContext;
import com.espertech.esper.epl.expression.core.ExprNodeUtility;
import com.espertech.esper.view.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory for {@link MergeView} instances.
 */
public class MergeViewFactory implements ViewFactory, MergeViewFactoryMarker
{
    private List<ExprNode> viewParameters;
    private int streamNumber;

    private ExprNode[] criteriaExpressions;
    private EventType eventType;
    private boolean removable = false;  // set to true when retain-age

    public void setViewParameters(ViewFactoryContext viewFactoryContext, List<ExprNode> expressionParameters) throws ViewParameterException
    {
        this.viewParameters = expressionParameters;
        this.streamNumber = viewFactoryContext.getStreamNum();
    }

    public void attach(EventType parentEventType, StatementContext statementContext, ViewFactory optionalParentFactory, List<ViewFactory> parentViewFactories) throws ViewParameterException
    {
        // Find the group by view matching the merge view
        GroupByViewFactoryMarker groupByViewFactory = null;
        ExprNode[] unvalidated = viewParameters.toArray(new ExprNode[viewParameters.size()]);
        for (ViewFactory parentView : parentViewFactories)
        {
            if (!(parentView instanceof GroupByViewFactoryMarker))
            {
                continue;
            }
            GroupByViewFactoryMarker candidateGroupByView = (GroupByViewFactoryMarker) parentView;
            if (ExprNodeUtility.deepEquals(candidateGroupByView.getCriteriaExpressions(), unvalidated))
            {
                groupByViewFactory = candidateGroupByView;
            }
        }

        if (groupByViewFactory == null)
        {
            throw new ViewParameterException("Groupwin view for this merge view could not be found among parent views");
        }
        criteriaExpressions = groupByViewFactory.getCriteriaExpressions();
        removable = groupByViewFactory.isReclaimAged();

        // determine types of fields
        Class[] fieldTypes = new Class[criteriaExpressions.length];
        for (int i = 0; i < fieldTypes.length; i++)
        {
            fieldTypes[i] = criteriaExpressions[i].getExprEvaluator().getType();
        }

        // Determine the final event type that the merge view generates
        // This event type is ultimatly generated by AddPropertyValueView which is added to each view branch for each
        // group key.

        // If the parent event type contains the merge fields, we use the same event type
        boolean parentContainsMergeKeys = true;
        String[] fieldNames = new String[criteriaExpressions.length];
        for (int i = 0; i < criteriaExpressions.length; i++)
        {
            String name = ExprNodeUtility.toExpressionStringMinPrecedenceSafe(criteriaExpressions[i]);
            fieldNames[i] = name;
            try {
                if (!(parentEventType.isProperty(name))) {
                    parentContainsMergeKeys = false;
                }
            }
            catch (PropertyAccessException ex) {
                // expected
                parentContainsMergeKeys = false;
            }
        }

        // If the parent view contains the fields to group by, the event type after merging stays the same
        if (parentContainsMergeKeys)
        {
            eventType = parentEventType;
        }
        else
        // If the parent event type does not contain the fields, such as when a statistics views is
        // grouped which simply provides a map of calculated values,
        // then we need to add in the merge field as an event property thus changing event types.
        {
            Map<String, Object> additionalProps = new HashMap<String, Object>();
            for (int i = 0; i < fieldNames.length; i++)
            {
                additionalProps.put(fieldNames[i], fieldTypes[i]);
            }
            String outputEventTypeName = statementContext.getStatementId() + "_mergeview_" + streamNumber;
            eventType = statementContext.getEventAdapterService().createAnonymousWrapperType(outputEventTypeName, parentEventType, additionalProps);
        }
    }

    public View makeView(AgentInstanceViewFactoryChainContext agentInstanceViewFactoryContext)
    {
        return new MergeView(agentInstanceViewFactoryContext, criteriaExpressions, eventType, removable);
    }

    public EventType getEventType()
    {
        return eventType;
    }

    public boolean canReuse(View view, AgentInstanceContext agentInstanceContext)
    {
        if (!(view instanceof MergeView))
        {
            return false;
        }

        MergeView myView = (MergeView) view;
        if (!ExprNodeUtility.deepEquals(myView.getGroupFieldNames(), criteriaExpressions))
        {
            return false;
        }
        return true;
    }

    public ExprNode[] getCriteriaExpressions() {
        return criteriaExpressions;
    }

    public String getViewName() {
        return "Group-By-Merge";
    }
}
