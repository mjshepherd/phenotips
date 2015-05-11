package org.phenotips.phenogrid;
/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
import org.phenotips.diagnosis.DiagnosisService;
import org.phenotips.mendelianSearch.phenotype.PhenotypeScorer;
import org.phenotips.ontology.OntologyManager;
import org.phenotips.ontology.OntologyTerm;

import org.xwiki.component.annotation.Component;
import org.xwiki.script.service.ScriptService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import net.sf.json.JSONObject;

/**
 * @version $Id$
 */
@Component
@Named("phenogrid")
@Singleton
public class PhenogridService implements ScriptService
{

    private static final String SCORE_STRING = "score";

    @Inject
    private DiagnosisService service;

    @Inject
    private OntologyManager om;

    @Inject
    private PhenotypeScorer ps;

    public JSONObject get(List<String> phenotype, List<String> nonstandardPhenotype, int limit){
        JSONObject result = new JSONObject();
        List<JSONObject> matches = this.getOMIM(phenotype, nonstandardPhenotype, limit);
        result.element("b", matches);
        result.element("a", phenotype);
        result.element("apiVersionInfo", "Phenogrid prototype service");
        result.element("resource", "localhost");
        result.element("metadata", PhenogridService.getMeta(matches));
        result.element("cutoff", limit);
        return result;
    }

    private static JSONObject getMeta(List<JSONObject> OMIMViews)
    {
        double maxMaxIC, maxSumIC, meanMaxIC, meanMeanIC, meanN, meanSumIC;
        maxMaxIC = 0;
        maxSumIC = 0;
        meanMaxIC = 0;
        meanMeanIC = 0;
        meanSumIC = 0;
        meanN = 0;

        for (JSONObject OMIMView : OMIMViews) {
            double maxIC = 0;
            double sumIC = 0;
            double meanIC;
            int N = 0;
            List<JSONObject> matches = (List<JSONObject>) OMIMView.get("matches");
            for (JSONObject match : matches) {
                JSONObject lcs = (JSONObject) match.get("lcs");
                Double lcsIC = (Double) lcs.get("IC");
                if (lcsIC > maxIC){
                    maxIC = lcsIC;
                }
                sumIC += lcsIC;
                N++;
            }
            meanIC = sumIC/matches.size();

            if(maxIC > maxMaxIC) {
                maxMaxIC = maxIC;
            }
            if(sumIC > maxSumIC) {
                maxSumIC = sumIC;
            }
            meanMaxIC += maxIC;
            meanMeanIC += meanIC;
            meanSumIC += sumIC;
            meanN += N;
        }
        int numViews = OMIMViews.size();
        meanMaxIC = meanMaxIC/numViews;
        meanMeanIC = meanMeanIC/numViews;
        meanSumIC = meanSumIC/numViews;
        meanN = meanN/numViews;

        JSONObject meta = new JSONObject();
        meta.put("maxMaxIC", maxMaxIC);
        meta.put("maxSumIC", maxSumIC);
        meta.put("meanMaxIC", meanMaxIC);
        meta.put("meanMeanIC", meanMeanIC);
        meta.put("meanN", meanN);
        meta.put("meanSumIC", meanSumIC);
        meta.put("individuals", numViews);
        return meta;
    }

    private List<JSONObject> getOMIM(List<String> phenotype, List<String> nonstandardPhenotype, int limit)
    {
        List<OntologyTerm> diseases = this.service.getDiagnosis(phenotype, nonstandardPhenotype, limit);
        List<OntologyTerm> phenotypeTerms = new ArrayList<OntologyTerm>();
        for (String phene :  phenotype) {
            phenotypeTerms.add(this.om.resolveTerm(phene));
        }

        List<Map<String, Object>> resultMaps = new ArrayList<Map<String, Object>>();
        for (OntologyTerm disease :diseases) {
            //TODO: find a diseases phenotype?
            List<OntologyTerm> diseasePhenotype = this.getDiseasePhenotype(disease);
            List<Map<String, Object>> matches = this.ps.getDetailedMatches(phenotypeTerms, diseasePhenotype);
            double score = this.ps.getScore(diseasePhenotype, phenotypeTerms);
            Map<String, Object> diseaseView = this.generateDiseaseView(disease, matches, score);
            resultMaps.add(diseaseView);
        }

        PhenogridService.sortAndRankResults(resultMaps);

        return PhenogridService.convertMapToJSONList(resultMaps);
    }

    private static List<JSONObject> convertMapToJSONList(List<Map<String, Object>> in)
    {
        List<JSONObject> out = new ArrayList<JSONObject>();
        for (Map<String, Object> resultMap : in) {
            out.add(JSONObject.fromObject(resultMap));
        }
        return out;
    }

    private static void sortAndRankResults(List<Map<String, Object>> in)
    {
        Collections.sort(in, new Comparator<Map<String,Object>>()
            {
                @Override public int compare(Map<String, Object> o1, Map<String, Object> o2)
                {
                    int score1 = (int) ((Map<String, Object>) o1.get(SCORE_STRING)).get(SCORE_STRING);
                    int score2 = (int) ((Map<String, Object>) o2.get(SCORE_STRING)).get(SCORE_STRING);
                    return score2 - score1;
                }
            }
        );
        for (int i = 0; i< in.size(); i++) {
            ((Map<String, String>) in.get(i).get(SCORE_STRING)).put("rank", i + "");
        }
    }

    private Map<String, Object> generateDiseaseView(OntologyTerm disease, List<Map<String, Object>> matches,
        double score)
    {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("id", disease.getId());
        result.put("label", disease.getName());
        result.put("type", null);
        result.put("matches", matches);

        Map<String, Object> scoreMap = new HashMap<String, Object>();
        scoreMap.put("metric", "combined_score");
        scoreMap.put("score", (int) Math.round(score*100));

        result.put("score", scoreMap);

        //TODO:I have no idea what to do with taxon
        Map<String, Object> taxon = new HashMap<String, Object>();
        taxon.put("id", "MATT");
        taxon.put("label", "The best Taxon");

        result.put("taxon", taxon);

        return result;
    }

    private List<OntologyTerm> getDiseasePhenotype(OntologyTerm disease)
    {
        List<OntologyTerm> result = new ArrayList<OntologyTerm>();
        List<String> diseasePhenotype = (List<String>) disease.get("actual_symptom");
        for(String termId : diseasePhenotype){
            result.add(this.om.resolveTerm(termId));
        }
        return result;
    }
}
