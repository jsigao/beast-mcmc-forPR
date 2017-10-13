package dr.evomodel.treedatalikelihood.preorder;

import dr.evolution.tree.Tree;
import dr.evomodel.continuous.MultivariateDiffusionModel;
import dr.evomodel.treedatalikelihood.continuous.ConjugateRootTraitPrior;
import dr.evomodel.treedatalikelihood.continuous.ContinuousDataLikelihoodDelegate;
import dr.evomodel.treedatalikelihood.continuous.ContinuousRateTransformation;
import dr.evomodel.treedatalikelihood.continuous.ContinuousTraitPartialsProvider;
import dr.evomodel.treedatalikelihood.continuous.cdi.ContinuousDiffusionIntegrator;
import dr.evomodel.treedatalikelihood.continuous.cdi.PrecisionType;
import dr.math.distributions.MultivariateNormalDistribution;
import dr.math.matrixAlgebra.WrappedVector;

/**
 * @author Marc A. Suchard
 */
public class ConditionalOnTipsRealizedDelegate extends AbstractRealizedContinuousTraitDelegate {

    static final private boolean DEBUG = false;

    final protected int dimPartial;
    final boolean hasNoDrift;

    public ConditionalOnTipsRealizedDelegate(String name,
                                             Tree tree,
                                             MultivariateDiffusionModel diffusionModel,
                                             ContinuousTraitPartialsProvider dataModel,
                                             ConjugateRootTraitPrior rootPrior,
                                             ContinuousRateTransformation rateTransformation,
//                                             BranchRateModel rateModel,
                                             ContinuousDataLikelihoodDelegate likelihoodDelegate) {
        super(name, tree, diffusionModel, dataModel, rootPrior, rateTransformation, likelihoodDelegate);

        this.likelihoodDelegate = likelihoodDelegate;
        this.cdi = likelihoodDelegate.getIntegrator();
        this.dimPartial = dimTrait + likelihoodDelegate.getPrecisionType().getMatrixLength(dimTrait);
        partialNodeBuffer = new double[numTraits * dimPartial];
        partialPriorBuffer = new double[numTraits * dimPartial];

        tmpMean = new double[dimTrait];

        this.hasNoDrift = ! likelihoodDelegate.getDiffusionProcessDelegate().hasDrift();

        if (hasNoDrift) {
            this.precisionBuffer = null;
            this.displacementBuffer = null;
        } else {
            this.precisionBuffer = new double[dimTrait * dimTrait];
            this.displacementBuffer = new double[dimTrait];
        }
    }

    @Override
    protected void simulateRoot(final int nodeIndex) {

        final int bufferedRoot = likelihoodDelegate.getActiveNodeIndex(nodeIndex);

        cdi.getPostOrderPartial(
                rootProcessDelegate.getPriorBufferIndex(), partialPriorBuffer); // No double-buffering
        
//        cdi.getPostOrderPartial(nodeIndex, partialNodeBuffer);
      cdi.getPostOrderPartial(bufferedRoot, partialNodeBuffer);


        if (DEBUG) {
            System.err.println("Simulate root node " + nodeIndex + " -> " + bufferedRoot);
        }

        int offsetPartial = 0;
        int offsetSample = dimNode * nodeIndex; // bufferedRoot;
        for (int trait = 0; trait < numTraits; ++trait) {

            simulateTraitForRoot(offsetSample, offsetPartial);

            offsetSample += dimTrait;
            offsetPartial += dimPartial;
        }
    }

    protected void simulateTraitForRoot(final int offsetSample, final int offsetPartial) {

        assert (likelihoodDelegate.getPrecisionType() == PrecisionType.SCALAR);

        final double rootPrec = partialNodeBuffer[offsetPartial + dimTrait];

        if (DEBUG) {
            System.err.println("\trootPrec: " + rootPrec);
        }

        if (Double.isInfinite(rootPrec)) {

            System.arraycopy(partialNodeBuffer, offsetPartial, sample, offsetSample, dimTrait);

        } else {

            final double priorPrec = partialPriorBuffer[offsetPartial + dimTrait];
            final double totalPrec = priorPrec + rootPrec;

            for (int i = 0; i < dimTrait; ++i) {
                tmpMean[i] = (rootPrec * partialNodeBuffer[offsetPartial + i]
                        + priorPrec * partialPriorBuffer[offsetPartial + i])
                        / totalPrec;
            }

            if (DEBUG) {
                System.err.println("\trootMean: " + new WrappedVector.Raw(partialNodeBuffer, offsetPartial, dimTrait));
                System.err.println("\tprioPrec: " + priorPrec);
                System.err.println("\tprioMean: " + new WrappedVector.Raw(partialPriorBuffer, offsetPartial, dimTrait));
                System.err.println("\tweigMean: " + new WrappedVector.Raw(tmpMean, 0, dimTrait));
            }

            final double sqrtScale = Math.sqrt(1.0 / totalPrec);

            MultivariateNormalDistribution.nextMultivariateNormalCholesky(
                    tmpMean, 0, // input mean
                    cholesky, sqrtScale, // input variance
                    sample, offsetSample, // output sample
                    tmpEpsilon);

            if (DEBUG) {
                System.err.println("\tsample: " + new WrappedVector.Raw(sample, offsetSample, dimTrait));
            }
        }
    }

//    @Override
//    protected void simulateNode(final BranchNodeOperation operation, final double branchNormalization) {
//        final int nodeIndex = operation.getNodeNumber();
//
//        if (hasNoDrift) {
//            likelihoodDelegate.getPostOrderPartial(nodeIndex, partialNodeBuffer);
//        } else {
////            likelihoodDelegate.getPostOrderPartial(nodeIndex, partialPriorBuffer,
////                    precisionBuffer, displacementBuffer);
//        }
//
//        if (DEBUG) {
//        System.err.println("\t\t\tNODE_INDEX = " + nodeIndex);
//        }
//
//        int offsetPartial = 0;
//        int offsetSample = dimNode * nodeIndex;
//        int offsetParent = dimNode * operation.getParentNumber();
//
//        final double branchPrecision = 1.0 / (operation.getBranchLength() * branchNormalization);
//
//        if (DEBUG) {
//            System.err.println("Simulate for node " + nodeIndex);
//        }
//        for (int trait = 0; trait < numTraits; ++trait) {
//
//            simulateTraitForNode(nodeIndex, trait, offsetSample, offsetParent, offsetPartial, 0, branchPrecision);
//
//            offsetSample += dimTrait;
//            offsetParent += dimTrait;
//            offsetPartial += dimPartial;
//
//            throw new RuntimeException("Fix me");
//        }
//
//        throw new RuntimeException("Do not use");
//    }

    @Override
    protected void simulateNode(final int parentNumber,
                                final int nodeNumber,
                                final int nodePartial,
                                final int nodeMatrix,
                                final int external) {

        cdi.getPostOrderPartial(nodePartial, partialNodeBuffer);
        final double branchPrecision = cdi.getBranchMatrices(nodeMatrix, precisionBuffer, displacementBuffer);

//        if (hasNoDrift) {
//
//        } else {
//            cdi.getBranchMatrices(nodeNumber, precisionBuffer, displacementBuffer);
//        }

        if (DEBUG) {
            System.err.println("\t\t\tNODE_INDEX = " + nodeNumber);
        }

        int offsetPartial = 0;
        int offsetSample = dimNode * nodeNumber;
        int offsetParent = dimNode * parentNumber;

//        final double branchPrecision = 1.0; // TODO / (operation.getBranchLength() * branchNormalization);

        if (DEBUG) {
            System.err.println("Simulate for node " + nodeNumber);
        }
        for (int trait = 0; trait < numTraits; ++trait) {

            simulateTraitForNode(nodeNumber, trait, offsetSample, offsetParent, offsetPartial, external, branchPrecision);
            
            offsetSample += dimTrait;
            offsetParent += dimTrait;
            offsetPartial += dimPartial;
        }
    }

    protected void simulateTraitForNode(final int nodeIndex,
                                        final int traitIndex,
                                        final int offsetSample,
                                        final int offsetParent,
                                        final int offsetPartial,
                                        final int external,
                                        final double branchPrecision) {

        final double nodePrecision = partialNodeBuffer[offsetPartial + dimTrait];

        if (Double.isInfinite(nodePrecision)) {

            if (DEBUG) {
                System.err.println("\tCopy from node partial");
            }

            System.arraycopy(partialNodeBuffer, offsetPartial, sample, offsetSample, dimTrait);

        } else if (Double.isInfinite(branchPrecision)) {

            if (DEBUG) {
                System.err.println("\tCopy from parent sample");
            }

            System.arraycopy(sample, offsetParent, sample, offsetSample, dimTrait);

        } else {

            final double totalPrecision = nodePrecision + branchPrecision;

            for (int i = 0; i < dimTrait; ++i) {
                tmpMean[i] = (nodePrecision * partialNodeBuffer[offsetPartial + i]
                        + branchPrecision * sample[offsetParent + i]) / totalPrecision;
            }

            final double sqrtScale = Math.sqrt(1.0 / totalPrecision);

            MultivariateNormalDistribution.nextMultivariateNormalCholesky(
                    tmpMean, 0, // input mean
                    cholesky, sqrtScale, // input variance
                    sample, offsetSample, // output sample
                    tmpEpsilon);
        }

        if (DEBUG) {
            System.err.println("\tSample value: " + new WrappedVector.Raw(sample, offsetSample, dimTrait));
        }
    }

    final ContinuousDataLikelihoodDelegate likelihoodDelegate;
    final private ContinuousDiffusionIntegrator cdi;
    final double[] partialNodeBuffer;
    final double[] partialPriorBuffer;
    final double[] precisionBuffer;
    final double[] displacementBuffer;
    final double[] tmpMean;
}
