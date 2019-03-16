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

package ch.unibas.dmi.dbis.polyphenydb.adapter.elasticsearch;


import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptCost;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexNode;
import ch.unibas.dmi.dbis.polyphenydb.adapter.java.JavaTypeFactory;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptCluster;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptCost;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptPlanner;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelTraitSet;
import ch.unibas.dmi.dbis.polyphenydb.rel.RelNode;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.Project;
import ch.unibas.dmi.dbis.polyphenydb.rel.metadata.RelMetadataQuery;
import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataType;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexNode;
import ch.unibas.dmi.dbis.polyphenydb.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Implementation of {@link ch.unibas.dmi.dbis.polyphenydb.rel.core.Project} relational expression in Elasticsearch.
 */
public class ElasticsearchProject extends Project implements ElasticsearchRel {

    ElasticsearchProject( RelOptCluster cluster, RelTraitSet traitSet, RelNode input, List<? extends RexNode> projects, RelDataType rowType ) {
        super( cluster, traitSet, input, projects, rowType );
        assert getConvention() == ElasticsearchRel.CONVENTION;
        assert getConvention() == input.getConvention();
    }


    @Override
    public Project copy( RelTraitSet relTraitSet, RelNode input, List<RexNode> projects, RelDataType relDataType ) {
        return new ElasticsearchProject( getCluster(), traitSet, input, projects, relDataType );
    }


    @Override
    public RelOptCost computeSelfCost( RelOptPlanner planner, RelMetadataQuery mq ) {
        return super.computeSelfCost( planner, mq ).multiplyBy( 0.1 );
    }


    @Override
    public void implement( Implementor implementor ) {
        implementor.visitChild( 0, getInput() );

        final List<String> inFields = ElasticsearchRules.elasticsearchFieldNames( getInput().getRowType() );
        final ElasticsearchRules.RexToElasticsearchTranslator translator = new ElasticsearchRules.RexToElasticsearchTranslator( (JavaTypeFactory) getCluster().getTypeFactory(), inFields );

        final List<String> fields = new ArrayList<>();
        final List<String> scriptFields = new ArrayList<>();
        for ( Pair<RexNode, String> pair : getNamedProjects() ) {
            final String name = pair.right;
            final String expr = pair.left.accept( translator );

            if ( ElasticsearchRules.isItem( pair.left ) ) {
                implementor.addExpressionItemMapping( name, ElasticsearchRules.stripQuotes( expr ) );
            }

            if ( expr.equals( name ) ) {
                fields.add( name );
            } else if ( expr.matches( "\"literal\":.+" ) ) {
                scriptFields.add( ElasticsearchRules.quote( name ) + ":{\"script\": " + expr.split( ":" )[1] + "}" );
            } else {
                scriptFields.add( ElasticsearchRules.quote( name ) + ":{\"script\":"
                        // _source (ES2) vs params._source (ES5)
                        + "\"" + implementor.elasticsearchTable.scriptedFieldPrefix() + "." + expr.replaceAll( "\"", "" ) + "\"}" );
            }
        }

        StringBuilder query = new StringBuilder();
        if ( scriptFields.isEmpty() ) {
            List<String> newList = fields.stream().map( ElasticsearchRules::quote ).collect( Collectors.toList() );

            final String findString = String.join( ", ", newList );
            query.append( "\"_source\" : [" ).append( findString ).append( "]" );
        } else {
            // if scripted fields are present, ES ignores _source attribute
            for ( String field : fields ) {
                scriptFields.add( ElasticsearchRules.quote( field ) + ":{\"script\": "
                        // _source (ES2) vs params._source (ES5)
                        + "\"" + implementor.elasticsearchTable.scriptedFieldPrefix() + "." + field + "\"}" );
            }
            query.append( "\"script_fields\": {" + String.join( ", ", scriptFields ) + "}" );
        }

        implementor.list.removeIf( l -> l.startsWith( "\"_source\"" ) );
        implementor.add( "{" + query.toString() + "}" );
    }
}
