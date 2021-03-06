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
package com.espertech.esper.common.internal.avro.core;

import com.espertech.esper.common.client.EPException;
import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.common.client.EventBeanFactory;
import com.espertech.esper.common.client.EventType;
import com.espertech.esper.common.client.configuration.common.ConfigurationCommonEventTypeAvro;
import com.espertech.esper.common.client.configuration.common.ConfigurationCommonEventTypeMeta;
import com.espertech.esper.common.client.hook.type.ObjectValueTypeWidenerFactory;
import com.espertech.esper.common.client.hook.type.TypeRepresentationMapper;
import com.espertech.esper.common.client.meta.EventTypeMetadata;
import com.espertech.esper.common.internal.avro.selectexprrep.SelectExprProcessorRepresentationFactoryAvro;
import com.espertech.esper.common.internal.epl.expression.core.ExprValidationException;
import com.espertech.esper.common.internal.epl.resultset.select.core.SelectExprProcessorRepresentationFactory;
import com.espertech.esper.common.internal.event.avro.AvroSchemaEventType;
import com.espertech.esper.common.internal.event.avro.EventTypeAvroHandler;
import com.espertech.esper.common.internal.event.core.*;
import com.espertech.esper.common.internal.settings.ClasspathImportService;
import com.espertech.esper.common.internal.util.JavaClassHelper;
import com.espertech.esper.common.internal.util.TypeWidenerCustomizer;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;

import java.lang.annotation.Annotation;
import java.util.*;

import static org.apache.avro.SchemaBuilder.record;

public abstract class EventTypeAvroHandlerBase implements EventTypeAvroHandler {
    private final static SelectExprProcessorRepresentationFactoryAvro FACTORY_SELECT = new SelectExprProcessorRepresentationFactoryAvro();

    private ConfigurationCommonEventTypeMeta.AvroSettings avroSettings;
    private TypeRepresentationMapper optionalTypeMapper;
    private ObjectValueTypeWidenerFactory optionalWidenerFactory;

    protected abstract AvroSchemaEventType makeType(EventTypeMetadata metadata, EventBeanTypedEventFactory eventAdapterService, Schema schema, ConfigurationCommonEventTypeAvro optionalConfig, EventType[] supertypes, Set<EventType> deepSupertypes);

    public void init(ConfigurationCommonEventTypeMeta.AvroSettings avroSettings, ClasspathImportService classpathImportService) {
        this.avroSettings = avroSettings;

        if (avroSettings.getTypeRepresentationMapperClass() != null) {
            optionalTypeMapper = (TypeRepresentationMapper) JavaClassHelper.instantiate(TypeRepresentationMapper.class, avroSettings.getTypeRepresentationMapperClass(), classpathImportService.getClassForNameProvider());
        }

        if (avroSettings.getObjectValueTypeWidenerFactoryClass() != null) {
            optionalWidenerFactory = (ObjectValueTypeWidenerFactory) JavaClassHelper.instantiate(ObjectValueTypeWidenerFactory.class, avroSettings.getObjectValueTypeWidenerFactoryClass(), classpathImportService.getClassForNameProvider());
        }
    }

    public AvroSchemaEventType newEventTypeFromSchema(EventTypeMetadata metadata, EventBeanTypedEventFactory eventAdapterService, ConfigurationCommonEventTypeAvro requiredConfig, EventType[] superTypes, Set<EventType> deepSuperTypes) {

        Object avroSchemaObj = requiredConfig.getAvroSchema();
        String avroSchemaText = requiredConfig.getAvroSchemaText();

        if (avroSchemaObj == null && avroSchemaText == null) {
            throw new IllegalArgumentException("Null value for schema and schema text");
        }
        if (avroSchemaObj != null && avroSchemaText != null) {
            throw new IllegalArgumentException("Both avro schema and avro schema text are supplied and one can be provided");
        }
        if (avroSchemaObj != null && !(avroSchemaObj instanceof Schema)) {
            throw new IllegalArgumentException("Schema expected of type " + Schema.class.getName() + " but received " + avroSchemaObj.getClass().getName());
        }

        Schema schema;
        if (avroSchemaObj != null) {
            schema = (Schema) avroSchemaObj;
        } else {
            try {
                schema = new Schema.Parser().parse(avroSchemaText);
            } catch (Throwable t) {
                throw new EPException("Failed for parse avro schema: " + t.getMessage(), t);
            }
        }

        return makeType(metadata, eventAdapterService, schema, requiredConfig, superTypes, deepSuperTypes);
    }

    public AvroSchemaEventType newEventTypeFromNormalized(EventTypeMetadata metadata, EventTypeNameResolver eventTypeNameResolver, EventBeanTypedEventFactory eventAdapterService, Map<String, Object> properties, Annotation[] annotations, ConfigurationCommonEventTypeAvro optionalConfig, EventType[] superTypes, Set<EventType> deepSuperTypes, String statementName) {
        SchemaBuilder.FieldAssembler<Schema> assembler = record(metadata.getName()).fields();

        // add supertypes first so the positions are comparable
        Set<String> added = new HashSet<>();
        if (superTypes != null) {
            for (int i = 0; i < superTypes.length; i++) {
                AvroEventType superType = (AvroEventType) superTypes[i];
                for (Schema.Field field : superType.getSchemaAvro().getFields()) {
                    if (properties.containsKey(field.name()) || added.contains(field.name())) {
                        continue;
                    }
                    added.add(field.name());
                    assembler.name(field.name()).type(field.schema()).noDefault();
                }
            }
        }

        for (Map.Entry<String, Object> prop : properties.entrySet()) {
            if (!added.contains(prop.getKey())) {
                AvroSchemaUtil.assembleField(prop.getKey(), prop.getValue(), assembler, annotations, avroSettings, eventTypeNameResolver, statementName, optionalTypeMapper);
                added.add(prop.getKey());
            }
        }

        Schema schema = assembler.endRecord();
        return makeType(metadata, eventAdapterService, schema, optionalConfig, superTypes, deepSuperTypes);
    }

    public AvroSchemaEventType newEventTypeFromJson(EventTypeMetadata metadata, EventBeanTypedEventFactory eventBeanTypedEventFactory, String schemaJson) {
        Schema schema = new Schema.Parser().parse(schemaJson);
        return makeType(metadata, eventBeanTypedEventFactory, schema, null, null, null);
    }

    public SelectExprProcessorRepresentationFactory getOutputFactory() {
        return FACTORY_SELECT;
    }

    public EventBeanManufacturerForge getEventBeanManufacturer(AvroSchemaEventType avroSchemaEventType, WriteablePropertyDescriptor[] properties) {
        return new EventBeanManufacturerAvroForge(avroSchemaEventType, properties);
    }

    public EventBeanFactory getEventBeanFactory(EventType type, EventBeanTypedEventFactory eventAdapterService) {
        return new EventBeanFactoryAvro(type, eventAdapterService);
    }

    public void validateExistingType(EventType existingType, AvroSchemaEventType proposedType) {
        if (!(existingType instanceof AvroSchemaEventType)) {
            throw new EventAdapterException("Type by name '" + proposedType.getName() + "' is not a compatible type " +
                    "(target type underlying is '" + existingType.getUnderlyingType().getName() + "', " +
                    "source type underlying is '" + proposedType.getUnderlyingType().getName() + "')");
        }

        Schema proposed = (Schema) proposedType.getSchema();
        Schema existing = (Schema) ((AvroSchemaEventType) existingType).getSchema();
        if (!proposed.equals(existing)) {
            throw new EventAdapterException("Event type named '" + existingType.getName() +
                    "' has already been declared with differing column name or type information\n"
                    + "schemaExisting: " + AvroSchemaUtil.toSchemaStringSafe(existing) + "\n"
                    + "schemaProposed: " + AvroSchemaUtil.toSchemaStringSafe(proposed));
        }
    }

    public void avroCompat(EventType existingType, Map<String, Object> selPropertyTypes) throws ExprValidationException {
        Schema schema = ((AvroEventType) existingType).getSchemaAvro();

        for (Map.Entry<String, Object> selected : selPropertyTypes.entrySet()) {
            String propertyName = selected.getKey();
            Schema.Field targetField = schema.getField(selected.getKey());

            if (targetField == null) {
                throw new ExprValidationException("Property '" + propertyName + "' is not found among the fields for event type '" + existingType.getName() + "'");
            }

            if (selected.getValue() instanceof EventType) {
                EventType targetEventType = (EventType) selected.getValue();
                AvroEventType targetAvro = checkAvroEventTpe(selected.getKey(), targetEventType);
                if (targetField.schema().getType() != Schema.Type.RECORD || !targetField.schema().equals(targetAvro.getSchemaAvro())) {
                    throw new ExprValidationException("Property '" + propertyName + "' is incompatible, expecting a compatible schema '" + targetField.schema().getName() + "' but received schema '" + targetAvro.getSchemaAvro().getName() + "'");
                }
            } else if (selected.getValue() instanceof EventType[]) {
                EventType targetEventType = ((EventType[]) selected.getValue())[0];
                AvroEventType targetAvro = checkAvroEventTpe(selected.getKey(), targetEventType);
                if (targetField.schema().getType() != Schema.Type.ARRAY ||
                        targetField.schema().getElementType().getType() != Schema.Type.RECORD ||
                        !targetField.schema().getElementType().equals(targetAvro.getSchemaAvro())) {
                    throw new ExprValidationException("Property '" + propertyName + "' is incompatible, expecting an array of compatible schema '" + targetField.schema().getName() + "' but received schema '" + targetAvro.getSchemaAvro().getName() + "'");
                }
            }
        }
    }

    public Object convertEvent(EventBean theEvent, AvroSchemaEventType targetType) {
        GenericData.Record original = ((AvroGenericDataBackedEventBean) theEvent).getProperties();
        Schema targetSchema = (Schema) targetType.getSchema();
        GenericData.Record target = new GenericData.Record(targetSchema);

        List<Schema.Field> fields = original.getSchema().getFields();
        for (Schema.Field field : fields) {

            Schema.Field targetField = targetSchema.getField(field.name());
            if (targetField == null) {
                continue;
            }

            if (field.schema().getType() == Schema.Type.ARRAY) {
                Collection originalColl = (Collection) original.get(field.pos());
                if (originalColl != null) {
                    target.put(targetField.pos(), new ArrayList<>(originalColl));
                }
            } else if (field.schema().getType() == Schema.Type.MAP) {
                Map originalMap = (Map) original.get(field.pos());
                if (originalMap != null) {
                    target.put(targetField.pos(), new HashMap<>(originalMap));
                }
            } else {
                target.put(targetField.pos(), original.get(field.pos()));
            }
        }
        return target;
    }

    public TypeWidenerCustomizer getTypeWidenerCustomizer(EventType eventType) {
        return optionalWidenerFactory == null ? AvroTypeWidenerCustomizerDefault.INSTANCE : new AvroTypeWidenerCustomizerWHook(optionalWidenerFactory, eventType);
    }

    private AvroEventType checkAvroEventTpe(String propertyName, EventType eventType) throws ExprValidationException {
        if (!(eventType instanceof AvroEventType)) {
            throw new ExprValidationException("Property '" + propertyName + "' is incompatible with event type '" + eventType.getName() + "' underlying type " + eventType.getUnderlyingType().getSimpleName());
        }
        return (AvroEventType) eventType;
    }
}
