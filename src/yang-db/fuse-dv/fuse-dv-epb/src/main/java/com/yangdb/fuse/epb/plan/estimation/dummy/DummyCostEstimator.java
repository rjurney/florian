package com.yangdb.fuse.epb.plan.estimation.dummy;

/*-
 * #%L
 * fuse-dv-epb
 * %%
 * Copyright (C) 2016 - 2019 The YangDb Graph Database Project
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.yangdb.fuse.dispatcher.epb.CostEstimator;
import com.yangdb.fuse.model.execution.plan.PlanWithCost;

/**
 * Created by moti on 3/28/2017.
 */
public class DummyCostEstimator<P, C, TContext> implements CostEstimator<P, C, TContext> {
    public DummyCostEstimator(C dummyCost) {
        this.dummyCost = dummyCost;
    }


    private C dummyCost;

    @Override
    public PlanWithCost<P, C> estimate(P plan, TContext context) {
        return new PlanWithCost<>(plan, dummyCost);
    }
}
