/*
 * This file is based on code taken from the Apache Calcite project, which was released under the Apache License.
 * The changes are released under the MIT license.
 *
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
 *
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Databases and Information Systems Research Group, University of Basel, Switzerland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ch.unibas.dmi.dbis.polyphenydb.sql.ddl;


import static ch.unibas.dmi.dbis.polyphenydb.util.Static.RESOURCE;

import ch.unibas.dmi.dbis.polyphenydb.jdbc.PolyphenyDbPrepare;
import ch.unibas.dmi.dbis.polyphenydb.jdbc.PolyphenyDbSchema;
import ch.unibas.dmi.dbis.polyphenydb.schema.Function;
import ch.unibas.dmi.dbis.polyphenydb.schema.SchemaPlus;
import ch.unibas.dmi.dbis.polyphenydb.schema.TranslatableTable;
import ch.unibas.dmi.dbis.polyphenydb.schema.impl.ViewTable;
import ch.unibas.dmi.dbis.polyphenydb.schema.impl.ViewTableMacro;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlCreate;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlExecutableStatement;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlIdentifier;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlKind;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlNode;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlNodeList;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlOperator;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlSpecialOperator;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlUtil;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlWriter;
import ch.unibas.dmi.dbis.polyphenydb.sql.dialect.PolyphenyDbSqlDialect;
import ch.unibas.dmi.dbis.polyphenydb.sql.parser.SqlParserPos;
import ch.unibas.dmi.dbis.polyphenydb.util.ImmutableNullableList;
import ch.unibas.dmi.dbis.polyphenydb.util.Pair;
import ch.unibas.dmi.dbis.polyphenydb.util.Util;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;


/**
 * Parse tree for {@code CREATE VIEW} statement.
 */
public class SqlCreateView extends SqlCreate implements SqlExecutableStatement {

    private final SqlIdentifier name;
    private final SqlNodeList columnList;
    private final SqlNode query;

    private static final SqlOperator OPERATOR = new SqlSpecialOperator( "CREATE VIEW", SqlKind.CREATE_VIEW );


    /**
     * Creates a SqlCreateView.
     */
    SqlCreateView( SqlParserPos pos, boolean replace, SqlIdentifier name, SqlNodeList columnList, SqlNode query ) {
        super( OPERATOR, pos, replace, false );
        this.name = Objects.requireNonNull( name );
        this.columnList = columnList; // may be null
        this.query = Objects.requireNonNull( query );
    }


    public List<SqlNode> getOperandList() {
        return ImmutableNullableList.of( name, columnList, query );
    }


    @Override
    public void unparse( SqlWriter writer, int leftPrec, int rightPrec ) {
        if ( getReplace() ) {
            writer.keyword( "CREATE OR REPLACE" );
        } else {
            writer.keyword( "CREATE" );
        }
        writer.keyword( "VIEW" );
        name.unparse( writer, leftPrec, rightPrec );
        if ( columnList != null ) {
            SqlWriter.Frame frame = writer.startList( "(", ")" );
            for ( SqlNode c : columnList ) {
                writer.sep( "," );
                c.unparse( writer, 0, 0 );
            }
            writer.endList( frame );
        }
        writer.keyword( "AS" );
        writer.newlineAndIndent();
        query.unparse( writer, 0, 0 );
    }


    public void execute( PolyphenyDbPrepare.Context context ) {
        final Pair<PolyphenyDbSchema, String> pair = SqlDdlNodes.schema( context, true, name );
        final SchemaPlus schemaPlus = pair.left.plus();
        for ( Function function : schemaPlus.getFunctions( pair.right ) ) {
            if ( function.getParameters().isEmpty() ) {
                if ( !getReplace() ) {
                    throw SqlUtil.newContextException( name.getParserPosition(), RESOURCE.viewExists( pair.right ) );
                }
                pair.left.removeFunction( pair.right );
            }
        }
        final SqlNode q = SqlDdlNodes.renameColumns( columnList, query );
        final String sql = q.toSqlString( PolyphenyDbSqlDialect.DEFAULT ).getSql();
        final ViewTableMacro viewTableMacro = ViewTable.viewMacro( schemaPlus, sql, pair.left.path( null ), context.getObjectPath(), false );
        final TranslatableTable x = viewTableMacro.apply( ImmutableList.of() );
        Util.discard( x );
        schemaPlus.add( pair.right, viewTableMacro );
    }

}
