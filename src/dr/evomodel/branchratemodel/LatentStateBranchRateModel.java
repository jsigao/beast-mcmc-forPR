/*
 * LatentStateBranchRateModel.java
 *
 * Copyright (c) 2002-2015 Alexei Drummond, Andrew Rambaut and Marc Suchard
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

package dr.evomodel.branchratemodel;

import dr.evolution.tree.NodeRef;
import dr.evolution.tree.Tree;
import dr.evolution.tree.TreeTrait;
import dr.evomodel.tree.TreeModel;
import dr.evomodel.tree.TreeParameterModel;
import dr.inference.markovjumps.TwoStateOccupancyMarkovReward;
import dr.inference.model.*;

import java.util.Arrays;
import java.util.logging.Logger;

/**
 * LatentStateBranchRateModel
 *
 * @author Andrew Rambaut
 * @author Marc Suchard
 * @version $Id$
 *          <p/>
 *          $HeadURL$
 *          <p/>
 *          $LastChangedBy$
 *          $LastChangedDate$
 *          $LastChangedRevision$
 */
public class LatentStateBranchRateModel extends AbstractModelLikelihood implements BranchRateModel {

    public static final String LATENT_STATE_BRANCH_RATE_MODEL = "latentStateBranchRateModel";

    public static final boolean USE_CACHING = true;
    // seed 666, caching off: 204.69 seconds for 20000 states
    // state 20000	-5510.2520
    // 85.7%  5202  +     6    dr.inference.markovjumps.SericolaSeriesMarkovReward.accumulatePdf

    // seed 666, caching on: 119.43 seconds for 20000 states
    // state 20000	-5510.2520
    // 83.4%  3156  +     4    dr.inference.markovjumps.SericolaSeriesMarkovReward.accumulatePdf

    private final TreeModel tree;
    private final BranchRateModel nonLatentRateModel;
    private final Parameter latentTransitionRateParameter;
    private final Parameter latentTransitionFrequencyParameter;
    private final TreeParameterModel latentStateProportions;
    private final Parameter latentStateProportionParameter;
    private final Parameter nonLatentRateParameter;
    private final CountableBranchCategoryProvider branchCategoryProvider;
    private final int maximumNumberOfLatentPeriods;
    private final boolean conditionOnStateChange;
    private final boolean scaleByRootHeight;
    private  Parameter rootHeightParameter;

    private TwoStateOccupancyMarkovReward markovReward;
    private TwoStateOccupancyMarkovReward storedMarkovReward;
    private boolean likelihoodKnown = false;
    private boolean storedLikelihoodKnown;
    private double logLikelihood;
    private double storedLogLikelihood;

    private double[] branchLikelihoods;
    private double[] storedbranchLikelihoods;

    private boolean[] updateBranch;
    private boolean[] storedUpdateBranch;

    private boolean[] updateCategory;
    private boolean[] storedUpdateCategory;

    public LatentStateBranchRateModel(String name,
                                              TreeModel treeModel,
                                              BranchRateModel nonLatentRateModel,
                                              Parameter latentTransitionRateParameter,
                                              Parameter latentTransitionFrequencyParameter,
                                              Parameter latentStateProportionParameter,
                                              Parameter nonLatentRate,
                                              CountableBranchCategoryProvider branchCategoryProvider,
                                                int maximumNumberOfLatentPeriods,
                                      boolean scaleByRootHeight,boolean conditionOnStateChange) {
        super(name);

        this.tree = treeModel;
        addModel(tree);
        this.nonLatentRateModel = nonLatentRateModel;
        addModel(nonLatentRateModel);

        this.latentTransitionRateParameter = latentTransitionRateParameter;
        addVariable(latentTransitionRateParameter);

        this.latentTransitionFrequencyParameter = latentTransitionFrequencyParameter;
        addVariable(latentTransitionFrequencyParameter);


        this.nonLatentRateParameter = nonLatentRate;
        if(nonLatentRate!=null) {
            addVariable(nonLatentRate);
        }
        if (branchCategoryProvider ==  null) {
            this.latentStateProportions = new TreeParameterModel(tree, latentStateProportionParameter, false, Intent.BRANCH);
            addModel(latentStateProportions);

            this.latentStateProportionParameter = null;
            this.branchCategoryProvider = null;
        } else {
            this.latentStateProportions = null;
            this.branchCategoryProvider = branchCategoryProvider;
            this.latentStateProportionParameter = latentStateProportionParameter;
            this.latentStateProportionParameter.setDimension(branchCategoryProvider.getCategoryCount());

            if (USE_CACHING) {
                updateCategory = new boolean[branchCategoryProvider.getCategoryCount()];
                storedUpdateCategory = new boolean[branchCategoryProvider.getCategoryCount()];
                setUpdateAllCategories();
            }

            addVariable(latentStateProportionParameter);
        }

        branchLikelihoods = new double[tree.getNodeCount()];
        if (USE_CACHING) {
            updateBranch = new boolean[tree.getNodeCount()];
            storedUpdateBranch = new boolean[tree.getNodeCount()];
            storedbranchLikelihoods = new double[tree.getNodeCount()];

            setUpdateAllBranches();
        }
        addStatistic(errorStatistic);
        addStatistic(exogenousMeanRate);

        this.maximumNumberOfLatentPeriods = maximumNumberOfLatentPeriods;
        this.scaleByRootHeight= scaleByRootHeight;
        this.rootHeightParameter = null;
        if(this.scaleByRootHeight){
            this.rootHeightParameter=tree.getRootHeightParameter();
            addVariable(tree.getRootHeightParameter());
        }

        this.conditionOnStateChange = conditionOnStateChange;
    }

    public LatentStateBranchRateModel(String name,
                                      TreeModel treeModel,
                                      BranchRateModel nonLatentRateModel,
                                      Parameter latentTransitionRateParameter,
                                      Parameter latentTransitionFrequencyParameter,
                                      Parameter latentStateProportionParameter,
                                      Parameter nonLatentRate,
                                      CountableBranchCategoryProvider branchCategoryProvider,
                                      int maximumNumberOfLatentPeriods,
                                      boolean scaleByRootHeight){
        this(name,
                treeModel,
                nonLatentRateModel,
                latentTransitionRateParameter,
                latentTransitionFrequencyParameter,
                latentStateProportionParameter,
                nonLatentRate,
                branchCategoryProvider,
         maximumNumberOfLatentPeriods,
        scaleByRootHeight,
                false);
    }

    public LatentStateBranchRateModel(Parameter rate, Parameter prop) {
        super(LATENT_STATE_BRANCH_RATE_MODEL);
        tree = null;
        nonLatentRateModel = null;
        latentTransitionRateParameter = rate;
        latentTransitionFrequencyParameter = prop;
        latentStateProportions = null;
        this.latentStateProportionParameter = null;
        this.nonLatentRateParameter = null;
        this.branchCategoryProvider = null;
        this.maximumNumberOfLatentPeriods =5;
        this.scaleByRootHeight=false;
        this.rootHeightParameter=null;
        this.conditionOnStateChange = true;
    }

    private double[] createLatentInfinitesimalMatrix() {
        final double rate = getLatentTransitionRate();
        final double prop = latentTransitionFrequencyParameter.getParameterValue(0);

        double[] mat = new double[]{
                -rate * prop, rate * prop,
                rate * (1.0 - prop), -rate * (1.0 - prop)
        };
        return mat;
    }

    private static double[] createReward() {
        return new double[]{0.0, 1.0};
    }

    private TwoStateOccupancyMarkovReward createMarkovReward() {
        TwoStateOccupancyMarkovReward markovReward = new TwoStateOccupancyMarkovReward(createLatentInfinitesimalMatrix(),this.maximumNumberOfLatentPeriods *2);
        return markovReward;
    }

    public TwoStateOccupancyMarkovReward getMarkovReward() {
//        if (markovReward == null) {
            markovReward = createMarkovReward();
//        }
        return markovReward;
    }

    @Override
    public double getBranchRate(Tree tree, NodeRef node) {
        double nonLatentRate = nonLatentRateModel.getBranchRate(tree, node);

        double latentProportion = getLatentProportion(tree, node);
        if(this.nonLatentRateParameter !=null && latentProportion>0){
            nonLatentRate = this.nonLatentRateParameter.getParameterValue(0);
        }

        return calculateBranchRate(nonLatentRate, latentProportion);
    }

    public double getLatentProportion(Tree tree, NodeRef node) {

        if (latentStateProportions != null) {
            return latentStateProportions.getNodeValue(tree, node);
        } else {
            return latentStateProportionParameter.getParameterValue(branchCategoryProvider.getBranchCategory(tree, node));
        }
    }

    private double calculateBranchRate(double nonLatentRate, double latentProportion) {
        return nonLatentRate * (1.0 - latentProportion);
    }

    @Override
    protected void handleModelChangedEvent(Model model, Object object, int index) {
        if (model == tree) {
            likelihoodKnown = false; // node heights change elapsed times on branches, TODO could cache

            if (index == -1) {
                setUpdateAllBranches();
            } else {
                setUpdateBranch(index);
            }

        } else if (model == nonLatentRateModel) {
            // rates will change but the latent proportions haven't so the density is unchanged
        } else if (model == latentStateProportions) {
            likelihoodKnown = false; // argument of density has changed

            if (index == -1) {
                setUpdateAllBranches();
            } else {
                setUpdateBranch(index);
            }
        }
        fireModelChanged();
    }

    @Override
    protected void handleVariableChangedEvent(Variable variable, int index, Parameter.ChangeType type) {
        if (variable == latentTransitionFrequencyParameter || variable == latentTransitionRateParameter||(variable==rootHeightParameter&&scaleByRootHeight)) {
            // markovReward computations have changed
            markovReward = null;
            setUpdateAllBranches();
            likelihoodKnown = false;
        } else if (variable == latentStateProportionParameter) {
            if (index == -1) {
                setUpdateAllBranches();
            } else {
                setUpdateBranchCategory(index);
            }
            likelihoodKnown = false;
            fireModelChanged();
        }
    }

    private void setUpdateBranch(int nodeNumber) {
        if (USE_CACHING) {
            updateBranch[nodeNumber] = true;
        }
    }

    private void setUpdateAllBranches() {
        if (USE_CACHING) {
            for (int i = 0; i < updateBranch.length; i++) {
                updateBranch[i] = true;
            }
        }
    }

    private void clearUpdateAllBranches() {
        if (USE_CACHING) {
            for (int i = 0; i < updateBranch.length; i++) {
                updateBranch[i] = false;
            }
        }
    }

    private void setUpdateBranchCategory(int category) {
        if (USE_CACHING) {
            updateCategory[category] = true;

        }
    }

    private void setUpdateAllCategories() {
        if (USE_CACHING) {
            for (int i = 0; i < updateCategory.length; i++) {
                updateCategory[i] = true;
            }

        }
    }

    private void clearAllCategories() {
        if (USE_CACHING && updateCategory != null) {
            for (int i = 0; i < updateCategory.length; i++) {
                updateCategory[i] = false;
            }

        }
    }

    @Override
    protected void storeState() {
        storedMarkovReward = markovReward;
        storedLogLikelihood = logLikelihood;
        storedLikelihoodKnown = likelihoodKnown;
        if (USE_CACHING) {
            System.arraycopy(branchLikelihoods, 0, storedbranchLikelihoods, 0, branchLikelihoods.length);
            System.arraycopy(updateBranch, 0, storedUpdateBranch, 0, updateBranch.length);

            if (updateCategory != null) {
                System.arraycopy(updateCategory, 0, storedUpdateCategory, 0, updateCategory.length);
            }
        }
    }

    @Override
    protected void restoreState() {
        markovReward = storedMarkovReward;
        logLikelihood = storedLogLikelihood;
        likelihoodKnown = storedLikelihoodKnown;

        if (USE_CACHING) {
            double[] tmp = branchLikelihoods;
            branchLikelihoods = storedbranchLikelihoods;
            storedbranchLikelihoods = tmp;

            boolean[] tmp2 = updateBranch;
            updateBranch = storedUpdateBranch;
            storedUpdateBranch = tmp2;

            boolean[] tmp3 = updateCategory;
            updateCategory = storedUpdateCategory;
            storedUpdateCategory = tmp3;
        }
    }

    @Override
    protected void acceptState() {

    }

    @Override
    public Model getModel() {
        return this;
    }

    @Override
    public double getLogLikelihood() {

        if (!likelihoodKnown) {
            logLikelihood = calculateLogLikelihood();
            likelihoodKnown = true;
        }
        return logLikelihood;
    }

    private double calculateLogLikelihood() {

        double logLike = 0.0;

        for (int i = 0; i < tree.getNodeCount(); ++i) {
            NodeRef node = tree.getNode(i);
            if (node != tree.getRoot()) {
                if (updateNeededForNode(tree, node)) {
                    double branchLength = tree.getBranchLength(node);
                    double latentProportion = getLatentProportion(tree, node);

                    assert(latentProportion < 1.0);

                    double density = getBranchRewardDensity(latentProportion, branchLength);
                    branchLikelihoods[node.getNumber()] = Math.log(density);
                }
                logLike += branchLikelihoods[node.getNumber()];
            }
        }

        clearUpdateAllBranches();
        clearAllCategories();

        return logLike;
    }

    private boolean updateNeededForNode(Tree tree, NodeRef node) {
        if (USE_CACHING) {
            return (updateCategory != null && updateCategory[branchCategoryProvider.getBranchCategory(tree, node)]) || updateBranch[node.getNumber()];
        } else {
            return true;
        }
    }

    public double getBranchRewardDensity(double proportion, double branchLength) {
        if (markovReward == null) {
            markovReward = createMarkovReward();
        }

//        int state = 0 * 2 + 0; // just start = end = 0 entry
        // Reward is [0,1], and we want to track time in latent state (= 1).
        // Therefore all nodes are in state 0
//        double joint = markovReward.computePdf(reward, branchLength)[state];

        final double rate = getLatentTransitionRate() *
                latentTransitionFrequencyParameter.getParameterValue(0) * branchLength;
        final double zeroJumps = Math.exp(-rate);
        double joint = zeroJumps;
        if(proportion>0){
            joint = markovReward.computePdf(proportion * branchLength, branchLength, 0, 0);
        }else if(this.conditionOnStateChange){
                   throw new Error("Proportion latent is 0, but the model is conditioned on at least one state change. Proportion must be greater than 0, or condition must be relaxed.");
        }

//        final double joint = markovReward.computePdf(proportion * branchLength, branchLength, 0, 0);

        final double marg = markovReward.computeConditionalProbability(branchLength, 0, 0);

//        // Check numerical tolerance
        if (marg <= zeroJumps) {
            return 0.0;
        }

        //Check Truncation for error tolerance

        double truncatedJumpProbability = Arrays.stream(markovReward.getJumpProbabilities(branchLength)).sum();

        if((marg-(zeroJumps+truncatedJumpProbability))>1e-6){
            Logger.getLogger("error").warning("Numerical error. Potentially,insufficient truncation in latent branch rate model with rate of "+Math.round(getLatentTransitionRate())+" and branchlength "+ Math.round(branchLength));
            return 0;
        }

        // TODO Overhead in creating double[] could be saved by changing signature to computePdf

//        double density = joint / (marg - zeroJumps); // conditional on ending state  >= 2 jumps
        double density;
        if(this.conditionOnStateChange){
            density = joint / (marg-zeroJumps);
        }else{
            density= joint/ marg;
        }

        density *= branchLength;  // random variable is latentProportion = reward / branchLength, so include Jacobian

        if (DEBUG) {
            if (Double.isInfinite(Math.log(density))) {
                System.err.println("Infinite density in LatentStateBranchRateModel:");
                System.err.println("proportion   = " + proportion);
                System.err.println("branchLength = " + branchLength);
                System.err.println("lTRP  = " + getLatentTransitionRate());
                System.err.println("lTFP  = " + latentTransitionFrequencyParameter.getParameterValue(0));
                System.err.println("rate  = " + rate);
                System.err.println("joint = " + joint);
                System.err.println("marg  = " + marg);
                System.err.println("zero  = " + zeroJumps);
                System.err.println("Hit debugger");

                final double joint2 = markovReward.computePdf(proportion * branchLength, branchLength, 0, 0);

                final double marg2 = markovReward.computeConditionalProbability(branchLength, 0, 0);


            }
        }

        return density;
    }

    @Override
    public void makeDirty() {
        likelihoodKnown = false;
        markovReward = null;
        setUpdateAllBranches();
    }

    @Override
    public String getTraitName() {
        return BranchRateModel.RATE;
    }

    @Override
    public Intent getIntent() {
        return Intent.BRANCH;
    }

    @Override
    public TreeTrait getTreeTrait(final String key) {
        if (key.equals(BranchRateModel.RATE)) {
            return this;
        } else if (latentStateProportions != null && key.equals(latentStateProportions.getTraitName())) {
            return latentStateProportions;
        } else if (branchCategoryProvider != null && key.equals(branchCategoryProvider.getTraitName())) {
            return branchCategoryProvider;
        } else {
            throw new IllegalArgumentException("Unrecognised Tree Trait key, " + key);
        }
    }

    @Override
    public TreeTrait[] getTreeTraits() {
        return new TreeTrait[]{this, latentStateProportions, branchCategoryProvider};
    }

    @Override
    public Class getTraitClass() {
        return Double.class;
    }

    @Override
    public boolean getLoggable() {
        return true;
    }

    @Override
    public Double getTrait(final Tree tree, final NodeRef node) {
        return getBranchRate(tree, node);
    }

    @Override
    public String getTraitString(final Tree tree, final NodeRef node) {
        return Double.toString(getBranchRate(tree, node));
    }

    public static void main(String[] args) {

        Parameter rate = new Parameter.Default(4.4);
        Parameter prop = new Parameter.Default(0.25);

        LatentStateBranchRateModel model = new LatentStateBranchRateModel(rate, prop);

        double branchLength = 2.0;
        for (double reward = 0; reward < branchLength; reward += 0.01) {
            System.out.println(reward + ",\t" + model.getBranchRewardDensity(reward, branchLength) + ",");
        }

        System.out.println();
        System.out.println(model.getMarkovReward());

    }

    private Statistic errorStatistic = new Statistic.Abstract() {

        public String getStatisticName() {
            return "truncationError";
        }

        public int getDimension() {
            return 1;
        }

        public String getDimensionName(int dim) {
            return getId();
        }

        public double getStatisticValue(int dim) {
            return getError();
        }

    };

    public final double getError(){
        double error = 0.0;

        for (int i = 0; i < tree.getInternalNodeCount(); ++i) {
            NodeRef node = tree.getNode(i);
            if (node != tree.getRoot()) {
                double branchLength = tree.getBranchLength(node);
                 error += markovReward.getTruncationError(branchLength,0,0);
            }
        }
        return error;
    }

    private Statistic exogenousMeanRate = new Statistic.Abstract() {
        //shameful hack  - code borrowed from RateStatistic
        // A statistic to log the mean rate of the nonLatent bits of the tree.
        public String getStatisticName() {
            return "exogenousMeanRate";
        }

        public int getDimension() {
            return 1;
        }

        public String getDimensionName(int dim) {
            return getId();
        }

        public double getStatisticValue(int dim) {

            int length = 0;
            int offset = 0;

            length += tree.getExternalNodeCount();
            offset = length;
            length += tree.getInternalNodeCount() - 1;


            final double[] rates = new double[length];
            // need those only for mean
            final double[] branchLengths = new double[length];

            for (int i = 0; i < offset; i++) {
                NodeRef child = tree.getExternalNode(i);
                NodeRef parent = tree.getParent(child);
                branchLengths[i] = tree.getNodeHeight(parent) - tree.getNodeHeight(child) *(1-getLatentProportion(tree,child));
                rates[i] = nonLatentRateModel.getBranchRate(tree, child);
            }
            final int n = tree.getInternalNodeCount();
            int k = offset;
            for (int i = 0; i < n; i++) {
                NodeRef child = tree.getInternalNode(i);
                if (!tree.isRoot(child)) {
                    NodeRef parent = tree.getParent(child);
                    branchLengths[k] = tree.getNodeHeight(parent) - tree.getNodeHeight(child)*(1-getLatentProportion(tree,child));
                    rates[k] = nonLatentRateModel.getBranchRate(tree, child);
                    k++;
                }
            }
            double totalWeightedRate = 0.0;
            double totalTreeLength = 0.0;
            for (int i = 0; i < rates.length; i++) {
                    totalWeightedRate += rates[i] * branchLengths[i];
                    totalTreeLength += branchLengths[i];
                }
                return totalWeightedRate / totalTreeLength;
        }


    };

    /** This is private getter can scale the incoming transition rate by the hieght of the root. The idea here is that
    there are cases were we don't have much prior information on the instantaneous rate, and so we can set a prior based
    on how many latent periods we might expect over the course of the tree. This hopefully also helps with the case were
    latency is added to the root's children which pushes back the root only to lead to more latency there.
    **/
    private double getLatentTransitionRate(){
        if(scaleByRootHeight){
            return latentTransitionRateParameter.getParameterValue(0)/(rootHeightParameter.getParameterValue(0));
        }
        return latentTransitionRateParameter.getParameterValue(0);
    }


    private static boolean DEBUG = true;

}
