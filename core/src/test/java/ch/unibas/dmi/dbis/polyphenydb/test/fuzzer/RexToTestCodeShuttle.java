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
 *  The MIT License (MIT)
 *
 *  Copyright (c) 2019 Databases and Information Systems Research Group, University of Basel, Switzerland
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a
 *  copy of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package ch.unibas.dmi.dbis.polyphenydb.test.fuzzer;


import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataType;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexCall;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexFieldAccess;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexLiteral;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexNode;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexVisitorImpl;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlOperator;
import ch.unibas.dmi.dbis.polyphenydb.sql.fun.SqlStdOperatorTable;
import ch.unibas.dmi.dbis.polyphenydb.sql.type.SqlTypeName;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;


/**
 * Converts {@link RexNode} into a string form usable for inclusion into {@link RexProgramFuzzyTest}. For instance, it converts {@code AND(=(?0.bool0, true), =(?0.bool1, true))} to {@code isTrue(and(eq(vBool(0), trueLiteral), eq(vBool(1), trueLiteral)))}.
 */
public class RexToTestCodeShuttle extends RexVisitorImpl<String> {

    private static final Map<SqlOperator, String> OP_METHODS =
            ImmutableMap.<SqlOperator, String>builder()
                    .put( SqlStdOperatorTable.AND, "and" )
                    .put( SqlStdOperatorTable.OR, "or" )
                    .put( SqlStdOperatorTable.CASE, "case_" )
                    .put( SqlStdOperatorTable.COALESCE, "coalesce" )
                    .put( SqlStdOperatorTable.IS_NULL, "isNull" )
                    .put( SqlStdOperatorTable.IS_NOT_NULL, "isNotNull" )
                    .put( SqlStdOperatorTable.IS_UNKNOWN, "isUnknown" )
                    .put( SqlStdOperatorTable.IS_TRUE, "isTrue" )
                    .put( SqlStdOperatorTable.IS_NOT_TRUE, "isNotTrue" )
                    .put( SqlStdOperatorTable.IS_FALSE, "isFalse" )
                    .put( SqlStdOperatorTable.IS_NOT_FALSE, "isNotFalse" )
                    .put( SqlStdOperatorTable.IS_DISTINCT_FROM, "isDistinctFrom" )
                    .put( SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, "isNotDistinctFrom" )
                    .put( SqlStdOperatorTable.NULLIF, "nullIf" )
                    .put( SqlStdOperatorTable.NOT, "not" )
                    .put( SqlStdOperatorTable.GREATER_THAN, "gt" )
                    .put( SqlStdOperatorTable.GREATER_THAN_OR_EQUAL, "ge" )
                    .put( SqlStdOperatorTable.LESS_THAN, "lt" )
                    .put( SqlStdOperatorTable.LESS_THAN_OR_EQUAL, "le" )
                    .put( SqlStdOperatorTable.EQUALS, "eq" )
                    .put( SqlStdOperatorTable.NOT_EQUALS, "ne" )
                    .put( SqlStdOperatorTable.PLUS, "plus" )
                    .put( SqlStdOperatorTable.UNARY_PLUS, "unaryPlus" )
                    .put( SqlStdOperatorTable.MINUS, "sub" )
                    .put( SqlStdOperatorTable.UNARY_MINUS, "unaryMinus" )
                    .build();


    protected RexToTestCodeShuttle() {
        super( true );
    }


    @Override
    public String visitCall( RexCall call ) {
        SqlOperator operator = call.getOperator();
        String method = OP_METHODS.get( operator );

        StringBuilder sb = new StringBuilder();
        if ( method != null ) {
            sb.append( method );
            sb.append( '(' );
        } else {
            sb.append( "rexBuilder.makeCall(" );
            sb.append( "SqlStdOperatorTable." );
            sb.append( operator.getName().replace( ' ', '_' ) );
            sb.append( ", " );
        }
        List<RexNode> operands = call.getOperands();
        for ( int i = 0; i < operands.size(); i++ ) {
            RexNode operand = operands.get( i );
            if ( i > 0 ) {
                sb.append( ", " );
            }
            sb.append( operand.accept( this ) );
        }
        sb.append( ')' );
        return sb.toString();
    }


    @Override
    public String visitLiteral( RexLiteral literal ) {
        RelDataType type = literal.getType();

        if ( type.getSqlTypeName() == SqlTypeName.BOOLEAN ) {
            if ( literal.isNull() ) {
                return "nullBool";
            }
            return literal.toString() + "Literal";
        }
        if ( type.getSqlTypeName() == SqlTypeName.INTEGER ) {
            if ( literal.isNull() ) {
                return "nullInt";
            }
            return "literal(" + literal.getValue() + ")";
        }
        if ( type.getSqlTypeName() == SqlTypeName.VARCHAR ) {
            if ( literal.isNull() ) {
                return "nullVarchar";
            }
        }
        return "/*" + literal.getTypeName().getName() + "*/" + literal.toString();
    }


    @Override
    public String visitFieldAccess( RexFieldAccess fieldAccess ) {
        StringBuilder sb = new StringBuilder();
        sb.append( "v" );
        RelDataType type = fieldAccess.getType();
        switch ( type.getSqlTypeName() ) {
            case BOOLEAN:
                sb.append( "Bool" );
                break;
            case INTEGER:
                sb.append( "Int" );
                break;
            case VARCHAR:
                sb.append( "Varchar" );
                break;
        }
        if ( !type.isNullable() ) {
            sb.append( "NotNull" );
        }
        sb.append( "(" );
        sb.append( fieldAccess.getField().getIndex() % 10 );
        sb.append( ")" );
        return sb.toString();
    }
}
