/*
 * CriticalBirthDeathSerialSamplingModel.java
 *
 * Copyright (c) 2002-2022 Alexei Drummond, Andrew Rambaut and Marc Suchard
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

package dr.evomodel.speciation;

import dr.inference.model.Parameter;

import static java.lang.Math.exp;
import static java.lang.Math.log;

public class CriticalBirthDeathSerialSamplingModel extends NewBirthDeathSerialSamplingModel {

    private int n_events;

    public CriticalBirthDeathSerialSamplingModel(
            String modelName,
            Parameter birthRate,
            Parameter deathRate,
            Parameter serialSamplingRate,
            Parameter treatmentProbability,
            Parameter samplingFractionAtPresent,
            Parameter originTime,
            boolean condition,
            Type units) {

        super(modelName, birthRate, deathRate, serialSamplingRate, treatmentProbability, samplingFractionAtPresent, originTime, condition, units);
        n_events = 0;
    }


    @Override
    public double processInterval(int model, double tYoung, double tOld, int nLineages) {
        // TODO Do something different
        return super.processInterval(model, tYoung, tOld, nLineages);
    }

    @Override
    public double processOrigin(int model, double rootAge) {
        double lambda = lambda();
        double rho = rho();
        double mu = mu();
        double v = exp(-(lambda - mu) * rootAge);
        double p_n = log(lambda*rho + (lambda*(1-rho) - mu)* v) - log(1- v);
        return -2*logq(rootAge) + (n_events-1)*p_n;
    }

    @Override
    public double processCoalescence(int model, double tOld) {
        n_events += 1;
        return 0;
    }

    @Override
    public double processSampling(int model, double tOld) {
        return 0;
    }

    @Override
    public double logConditioningProbability() {
        return -log(n_events);
    }
}