/*
 * Copyright 2019-2020 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file incorporates code covered by the following terms:
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.schema.impl;


import com.google.common.collect.ImmutableList;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import org.polypheny.db.adapter.java.JavaTypeFactory;
import org.polypheny.db.jdbc.PolyphenyDbPrepare.AnalyzeViewResult;
import org.polypheny.db.rel.type.RelDataTypeImpl;
import org.polypheny.db.schema.FunctionParameter;
import org.polypheny.db.schema.PolyphenyDbSchema;
import org.polypheny.db.schema.Schemas;
import org.polypheny.db.schema.TableMacro;
import org.polypheny.db.schema.TranslatableTable;


/**
 * Table function that implements a view. It returns the operator tree of the view's SQL query.
 */
public class ViewTableMacro implements TableMacro {

    protected final String viewSql;
    protected final PolyphenyDbSchema schema;
    private final Boolean modifiable;
    /**
     * Typically null. If specified, overrides the path of the schema as the context for validating {@code viewSql}.
     */
    protected final List<String> schemaPath;
    protected final List<String> viewPath;


    /**
     * Creates a ViewTableMacro.
     *
     * @param schema Root schema
     * @param viewSql SQL defining the view
     * @param schemaPath Schema path relative to the root schema
     * @param viewPath View path relative to the schema path
     * @param modifiable Request that a view is modifiable (dependent on analysis of {@code viewSql})
     */
    public ViewTableMacro( PolyphenyDbSchema schema, String viewSql, List<String> schemaPath, List<String> viewPath, Boolean modifiable ) {
        this.viewSql = viewSql;
        this.schema = schema;
        this.viewPath = viewPath == null ? null : ImmutableList.copyOf( viewPath );
        this.modifiable = modifiable;
        this.schemaPath = schemaPath == null ? null : ImmutableList.copyOf( schemaPath );
    }


    @Override
    public List<FunctionParameter> getParameters() {
        return Collections.emptyList();
    }


    @Override
    public TranslatableTable apply( List<Object> arguments ) {
        AnalyzeViewResult parsed = Schemas.analyzeView( schema, schemaPath, viewSql, viewPath, modifiable != null && modifiable );
        final List<String> schemaPath1 = schemaPath != null ? schemaPath : schema.path( null );
        if ( (modifiable == null || modifiable) && parsed.modifiable && parsed.table != null ) {
            return modifiableViewTable( parsed, viewSql, schemaPath1, viewPath, schema );
        } else {
            return viewTable( parsed, viewSql, schemaPath1, viewPath );
        }
    }


    /**
     * Allows a sub-class to return an extension of {@link ModifiableViewTable} by overriding this method.
     */
    protected ModifiableViewTable modifiableViewTable( AnalyzeViewResult parsed, String viewSql, List<String> schemaPath, List<String> viewPath, PolyphenyDbSchema schema ) {
        final JavaTypeFactory typeFactory = (JavaTypeFactory) parsed.typeFactory;
        final Type elementType = typeFactory.getJavaClass( parsed.rowType );
        return new ModifiableViewTable( elementType, RelDataTypeImpl.proto( parsed.rowType ), viewSql, schemaPath, viewPath, parsed.table, Schemas.path( schema.root(), parsed.tablePath ), parsed.constraint, parsed.columnMapping );
    }


    /**
     * Allows a sub-class to return an extension of {@link ViewTable} by overriding this method.
     */
    protected ViewTable viewTable( AnalyzeViewResult parsed, String viewSql, List<String> schemaPath, List<String> viewPath ) {
        final JavaTypeFactory typeFactory = (JavaTypeFactory) parsed.typeFactory;
        final Type elementType = typeFactory.getJavaClass( parsed.rowType );
        return new ViewTable( elementType, RelDataTypeImpl.proto( parsed.rowType ), viewSql, schemaPath, viewPath );
    }
}
