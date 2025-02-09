/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.kogito.index.graphql.query;

import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeReference;

import static graphql.schema.GraphQLInputObjectField.newInputObjectField;
import static org.kie.kogito.index.Constants.PROCESS_INSTANCES_DOMAIN_ATTRIBUTE;
import static org.kie.kogito.index.Constants.USER_TASK_INSTANCES_DOMAIN_ATTRIBUTE;

public class GraphQLOrderByTypeMapper extends AbstractInputObjectTypeMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphQLOrderByTypeMapper.class);
    private static final String ORDER_BY = "OrderBy";

    public GraphQLOrderByTypeMapper(GraphQLSchema schema, Map<String, GraphQLType> additionalTypes) {
        super(schema, additionalTypes);
    }

    @Override
    public String getTypeName(GraphQLObjectType type) {
        return type.getName() + ORDER_BY;
    }

    @Override
    protected Consumer<GraphQLInputObjectType.Builder> build(GraphQLObjectType domain) {
        return builder -> domain.getFieldDefinitions().forEach(field -> {
            if (!(field.getType() instanceof GraphQLList)) {
                LOGGER.debug("GraphQL mapping field: {}", field.getName());
                switch (field.getName()) {
                    //Skip id, multi instances not sortable
                    case PROCESS_INSTANCES_DOMAIN_ATTRIBUTE:
                    case USER_TASK_INSTANCES_DOMAIN_ATTRIBUTE:
                        //Skip id, not sortable
                    case "id":
                        break;
                    default:
                        String typeName;
                        String name = ((GraphQLNamedType) field.getType()).getName();
                        switch (name) {
                            case "Int":
                            case "Long":
                            case "String":
                            case "Boolean":
                            case "DateTime":
                                typeName = ORDER_BY;
                                break;
                            default:
                                typeName = name + ORDER_BY;
                                if (getSchema().getType(typeName) == null && !getAdditionalTypes().containsKey(typeName)) {
                                    GraphQLObjectType objectType = (GraphQLObjectType) getAdditionalTypes().get(name);
                                    if (objectType == null) {
                                        LOGGER.warn("Can not map order by type for field name: {}, type: {}", field.getName(), ((GraphQLNamedType) field.getType()).getName());
                                        return;
                                    }
                                    GraphQLInputObjectType type = new GraphQLOrderByTypeMapper(getSchema(), getAdditionalTypes()).apply(objectType);
                                    getAdditionalTypes().put(typeName, type);
                                }
                        }

                        builder.field(newInputObjectField().name(field.getName()).type(new GraphQLTypeReference(typeName)));
                }
            }
        });
    }
}
