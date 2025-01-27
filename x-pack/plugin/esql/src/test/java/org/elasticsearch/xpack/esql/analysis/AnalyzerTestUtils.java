/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.analysis;

import org.elasticsearch.xpack.core.enrich.EnrichPolicy;
import org.elasticsearch.xpack.esql.EsqlTestUtils;
import org.elasticsearch.xpack.esql.expression.function.EsqlFunctionRegistry;
import org.elasticsearch.xpack.esql.parser.EsqlParser;
import org.elasticsearch.xpack.esql.session.EsqlConfiguration;
import org.elasticsearch.xpack.ql.index.EsIndex;
import org.elasticsearch.xpack.ql.index.IndexResolution;
import org.elasticsearch.xpack.ql.plan.logical.LogicalPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.elasticsearch.xpack.esql.EsqlTestUtils.TEST_VERIFIER;
import static org.elasticsearch.xpack.esql.EsqlTestUtils.configuration;

public final class AnalyzerTestUtils {

    private AnalyzerTestUtils() {}

    public static Analyzer defaultAnalyzer() {
        return analyzer(analyzerDefaultMapping());
    }

    public static Analyzer expandedDefaultAnalyzer() {
        return analyzer(analyzerExpandedDefaultMapping());
    }

    public static Analyzer analyzer(IndexResolution indexResolution) {
        return analyzer(indexResolution, TEST_VERIFIER);
    }

    public static Analyzer analyzer(IndexResolution indexResolution, Verifier verifier) {
        return new Analyzer(
            new AnalyzerContext(EsqlTestUtils.TEST_CFG, new EsqlFunctionRegistry(), indexResolution, defaultEnrichResolution()),
            verifier
        );
    }

    public static Analyzer analyzer(IndexResolution indexResolution, Verifier verifier, EsqlConfiguration config) {
        return new Analyzer(new AnalyzerContext(config, new EsqlFunctionRegistry(), indexResolution, defaultEnrichResolution()), verifier);
    }

    public static Analyzer analyzer(Verifier verifier) {
        return new Analyzer(
            new AnalyzerContext(EsqlTestUtils.TEST_CFG, new EsqlFunctionRegistry(), analyzerDefaultMapping(), defaultEnrichResolution()),
            verifier
        );
    }

    public static LogicalPlan analyze(String query) {
        return analyze(query, "mapping-basic.json");
    }

    public static LogicalPlan analyze(String query, String mapping) {
        return analyze(query, analyzer(loadMapping(mapping, "test"), TEST_VERIFIER, configuration(query)));
    }

    public static LogicalPlan analyze(String query, Analyzer analyzer) {
        var plan = new EsqlParser().createStatement(query);
        // System.out.println(plan);
        var analyzed = analyzer.analyze(plan);
        // System.out.println(analyzed);
        return analyzed;
    }

    public static IndexResolution loadMapping(String resource, String indexName) {
        EsIndex test = new EsIndex(indexName, EsqlTestUtils.loadMapping(resource));
        return IndexResolution.valid(test);
    }

    public static IndexResolution analyzerDefaultMapping() {
        return loadMapping("mapping-basic.json", "test");
    }

    public static IndexResolution analyzerExpandedDefaultMapping() {
        return loadMapping("mapping-default.json", "test");
    }

    public static EnrichResolution defaultEnrichResolution() {
        return loadEnrichPolicyResolution("languages", "language_code", "languages_idx", "mapping-languages.json");
    }

    public static EnrichResolution loadEnrichPolicyResolution(String policyName, String matchField, String idxName, String mappingFile) {
        IndexResolution mapping = loadMapping(mappingFile, idxName);
        List<String> enrichFields = new ArrayList<>(mapping.get().mapping().keySet());
        enrichFields.remove(matchField);
        EnrichPolicy policy = new EnrichPolicy(EnrichPolicy.MATCH_TYPE, null, List.of(idxName), matchField, enrichFields);
        EnrichResolution enrichResolution = new EnrichResolution();
        enrichResolution.addResolvedPolicy(policyName, policy, Map.of("", idxName), mapping.get().mapping());
        enrichResolution.addExistingPolicies(Set.of(policyName));
        return enrichResolution;
    }
}
