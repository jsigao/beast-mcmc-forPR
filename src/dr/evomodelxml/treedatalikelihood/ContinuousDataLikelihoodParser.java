/*
 * ContinuousDataLikelihoodParser.java
 *
 * Copyright (c) 2002-2016 Alexei Drummond, Andrew Rambaut and Marc Suchard
 *
 * This file is part of BEAST.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership and licensing.
 *
 * BEAST is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 *  BEAST is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BEAST; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package dr.evomodelxml.treedatalikelihood;

import dr.evolution.tree.TreeTrait;
import dr.evolution.tree.TreeTraitProvider;
import dr.evomodel.branchratemodel.BranchRateModel;
import dr.evomodel.branchratemodel.DefaultBranchRateModel;
import dr.evomodel.continuous.AbstractMultivariateTraitLikelihood;
import dr.evomodel.continuous.MultivariateDiffusionModel;
import dr.evomodel.tree.TreeModel;
import dr.evomodel.treedatalikelihood.ProcessSimulation;
import dr.evomodel.treedatalikelihood.ProcessSimulationDelegate;
import dr.evomodel.treedatalikelihood.TreeDataLikelihood;
import dr.evomodel.treedatalikelihood.continuous.ConjugateRootTraitPrior;
import dr.evomodel.treedatalikelihood.continuous.ContinuousDataLikelihoodDelegate;
import dr.evomodel.treedatalikelihood.continuous.ContinuousRateTransformation;
import dr.evomodel.treedatalikelihood.continuous.ContinuousTraitDataModel;
import dr.evomodel.treedatalikelihood.continuous.cdi.PrecisionType;
import dr.evomodelxml.treelikelihood.TreeTraitParserUtilities;
import dr.inference.model.CompoundParameter;
import dr.inference.model.Parameter;
import dr.xml.*;

import java.util.List;

/**
 * @author Andrew Rambaut
 * @author Marc Suchard
 * @version $Id$
 */
public class ContinuousDataLikelihoodParser extends AbstractXMLObjectParser {

    public static final String CONJUGATE_ROOT_PRIOR = AbstractMultivariateTraitLikelihood.CONJUGATE_ROOT_PRIOR;
    public static final String USE_TREE_LENGTH = AbstractMultivariateTraitLikelihood.USE_TREE_LENGTH;
    public static final String SCALE_BY_TIME = AbstractMultivariateTraitLikelihood.SCALE_BY_TIME;
    public static final String RECIPROCAL_RATES = AbstractMultivariateTraitLikelihood.RECIPROCAL_RATES;
    public static final String PRIOR_SAMPLE_SIZE = AbstractMultivariateTraitLikelihood.PRIOR_SAMPLE_SIZE;

    public static final String RECONSTRUCT_TRAITS = "reconstructTraits";

    public static final String PARTIAL = "partial";

    public static final String CONTINUOUS_DATA_LIKELIHOOD = "traitDataLikelihood";

    public String getParserName() {
        return CONTINUOUS_DATA_LIKELIHOOD;
    }

    public Object parseXMLObject(XMLObject xo) throws XMLParseException {

        TreeModel treeModel = (TreeModel) xo.getChild(TreeModel.class);
        MultivariateDiffusionModel diffusionModel = (MultivariateDiffusionModel) xo.getChild(MultivariateDiffusionModel.class);
        BranchRateModel rateModel = (BranchRateModel) xo.getChild(BranchRateModel.class);

        TreeTraitParserUtilities utilities = new TreeTraitParserUtilities();
        String traitName = TreeTraitParserUtilities.DEFAULT_TRAIT_NAME;

        TreeTraitParserUtilities.TraitsAndMissingIndices returnValue =
                utilities.parseTraitsFromTaxonAttributes(xo, traitName, treeModel, true);
        CompoundParameter traitParameter = returnValue.traitParameter;
        List<Integer> missingIndices = returnValue.missingIndices;
        traitName = returnValue.traitName;

        final int dim = diffusionModel.getPrecisionmatrix().length;


//        System.err.println("Length missing = " + missingIndices.size());
        Parameter sampleMissingParameter = returnValue.sampleMissingParameter;
//        System.err.println("sMP: " + (sampleMissingParameter == null ? "null" : "notnull"));
//        System.exit(-1);

//        PrecisionType precisionType = PrecisionType.SCALAR;
        PrecisionType precisionType = PrecisionType.FULL;

        ContinuousTraitDataModel dataModel = new ContinuousTraitDataModel(traitName,
                traitParameter,
                missingIndices,
                dim, precisionType);

        ConjugateRootTraitPrior rootPrior = ConjugateRootTraitPrior.parseConjugateRootTraitPrior(xo, dim);

        boolean useTreeLength = xo.getAttribute(USE_TREE_LENGTH, false);
        boolean scaleByTime = xo.getAttribute(SCALE_BY_TIME, false);
//        boolean reciprocalRates = xo.getAttribute(RECIPROCAL_RATES, false); // TODO Still need to add

        if (rateModel == null) {
            rateModel = new DefaultBranchRateModel();
        }

        ContinuousRateTransformation rateTransformation = new ContinuousRateTransformation.Default(
                treeModel, scaleByTime, useTreeLength);

        ContinuousDataLikelihoodDelegate delegate = new ContinuousDataLikelihoodDelegate(treeModel,
                diffusionModel, dataModel, rootPrior, rateTransformation, rateModel);

        TreeDataLikelihood treeDataLikelihood = new TreeDataLikelihood(delegate, treeModel, rateModel);

        boolean reconstructTraits = xo.getAttribute(RECONSTRUCT_TRAITS, true);
        if (reconstructTraits) {

            ProcessSimulationDelegate simulationDelegate = new ProcessSimulationDelegate.ConditionalOnTipsRealizedDelegate(traitName, treeModel,
                    diffusionModel, dataModel, rootPrior, rateTransformation, rateModel, delegate);

            TreeTraitProvider traitProvider = new ProcessSimulation(traitName,
                    treeDataLikelihood, simulationDelegate);
            treeDataLikelihood.addTraits(traitProvider.getTreeTraits());

            if (sampleMissingParameter != null) {
                String partialTraitName = PARTIAL + "." + traitName;
                ProcessSimulationDelegate parialSimulationDelegate = new ProcessSimulationDelegate.ConditionalOnPartiallyMissingTipsDelegate(partialTraitName,
                        treeModel, diffusionModel, dataModel, rootPrior, rateTransformation, rateModel, delegate,
                        sampleMissingParameter);

                TreeTraitProvider partialTraitProvider = new ProcessSimulation(partialTraitName,
                        treeDataLikelihood, parialSimulationDelegate);

                treeDataLikelihood.addTraits(partialTraitProvider.getTreeTraits());
            }
        }

        return treeDataLikelihood;
    }

    //************************************************************************
    // AbstractXMLObjectParser implementation
    //************************************************************************

    public String getParserDescription() {
        return "This element represents the likelihood of trait data on a tree given a diffusion model.";
    }

    public Class getReturnType() {
        return TreeDataLikelihood.class;
    }

    public static final XMLSyntaxRule[] rules = {
            new ElementRule(TreeModel.class),
            new ElementRule(MultivariateDiffusionModel.class),
            new ElementRule(BranchRateModel.class, true),
            new ElementRule(CONJUGATE_ROOT_PRIOR, ConjugateRootTraitPrior.rules),
            AttributeRule.newBooleanRule(SCALE_BY_TIME, true),
            AttributeRule.newBooleanRule(USE_TREE_LENGTH, true),
            AttributeRule.newBooleanRule(RECIPROCAL_RATES, true),
            AttributeRule.newBooleanRule(RECONSTRUCT_TRAITS, true),
    };

    public XMLSyntaxRule[] getSyntaxRules() {
        return rules;
    }
}